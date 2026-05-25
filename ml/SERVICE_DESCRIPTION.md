# KeepMoments ML Service

## Обзор

FastAPI-сервис для автоматического подбора и ранжирования фотографий при заполнении фотоальбомов. Сервис объединяет несколько техник машинного обучения: мультимодальные эмбеддинги CLIP, кластеризацию для обеспечения разнообразия и метрики технического качества изображений.

**Версия:** 0.1.0
**Runtime:** Python 3.11, FastAPI 0.115.0, PyTorch 2.4.1 (CPU)
**Источник фото:** конфигурируемый — внутренний backend HTTP API (по умолчанию) или AWS S3 / MinIO

---

## Эндпоинты

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/health` | Проверка работоспособности |
| `POST` | `/process` | Основной пайплайн обработки |

### POST /process

**Входные данные (`ProcessRequest`):**

```json
{
  "photo_ids": ["photo-uuid-1", "photo-uuid-2", "..."],
  "user_description": "romantic wedding with warm tones",
  "min_photos": 10,
  "max_photos": 20,
  "template": {
    "id": "template_1",
    "pages": [
      {
        "id": "page_1",
        "slots": [
          {"id": "slot_1", "photo_id": null, "required_orientation": "landscape"}
        ]
      }
    ],
    "front_cover": {"mode": "caption", "text": null},
    "back_cover": {"mode": "photo", "photo_id": null}
  }
}
```

`photo_ids` интерпретируются как идентификаторы фото в выбранном источнике: при `PHOTO_SOURCE=backend` это ID фото в бекенде (используются в URL `GET /api/v1/photos/{id}/file/`), при `PHOTO_SOURCE=s3` — это ключи объектов в S3-бакете.

`front_cover` и `back_cover` — опциональны. Поле `mode` принимает значения `"caption"` (обложка с текстом) или `"photo"` (обложка с фото). При `null`-значениях текст/фото генерируются/выбираются автоматически.

Поле слота `required_orientation` принимает значения `"portrait"`, `"landscape"` или `null`. При `null` ориентация не ограничивается. Квадратные фото считаются landscape.

**Выходные данные (`ProcessResponse`):**

```json
{
  "filled_template": {
    "id": "template_1",
    "pages": [
      {
        "id": "page_1",
        "slots": [{"id": "slot_1", "photo_id": "photo-uuid-7"}],
        "caption": "A tender moment captured in golden light."
      }
    ],
    "front_cover": {"mode": "caption", "photo_id": null, "text": "Love in Every Frame"},
    "back_cover": {"mode": "photo", "photo_id": "photo-uuid-3", "text": null}
  }
}
```

---

## Конфигурация

Переменные окружения (файл `.env`):

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `PHOTO_SOURCE` | `backend` | Источник фотографий: `backend` или `s3` |
| `BACKEND_BASE_URL` | — | База URL бекенда (например, `http://backend:8000`). **Обязательна** при `PHOTO_SOURCE=backend`. Запросы идут на `{BACKEND_BASE_URL}/api/v1/photos/{id}/file/` |
| `BACKEND_TIMEOUT` | `30.0` | Таймаут (сек) одного запроса к бекенду |
| `AWS_ACCESS_KEY_ID` | — | AWS ключ доступа. **Обязателен** при `PHOTO_SOURCE=s3` |
| `AWS_SECRET_ACCESS_KEY` | — | AWS секретный ключ. **Обязателен** при `PHOTO_SOURCE=s3` |
| `AWS_REGION` | `us-east-1` | Регион AWS |
| `S3_BUCKET_NAME` | — | Бакет с фотографиями. **Обязателен** при `PHOTO_SOURCE=s3` |
| `S3_ENDPOINT_URL` | — | Кастомный endpoint (например, `http://minio:9000` для локального деплоя) |
| `CLIP_MODEL_NAME` | `ViT-B/32` | Вариант модели CLIP |
| `KMEANS_RANDOM_STATE` | `42` | Сид воспроизводимости |
| `LOG_LEVEL` | `INFO` | Уровень логирования |
| `ANTHROPIC_API_KEY` | — | Ключ Anthropic API для генерации подписей; если не задан — подписи пропускаются |
| `ANTHROPIC_MODEL` | `claude-haiku-4-5-20251001` | Модель Anthropic для генерации подписей |
| `OPENROUTER_API_KEY` | — | Ключ OpenRouter; если задан, имеет приоритет над Anthropic |
| `OPENROUTER_MODEL` | `google/gemini-flash-1.5` | Модель OpenRouter для генерации подписей |

Pydantic-валидатор настроек проверяет согласованность при старте: `PHOTO_SOURCE=backend` требует `BACKEND_BASE_URL`, `PHOTO_SOURCE=s3` — все три S3-переменные. Невалидная конфигурация приводит к ошибке на старте приложения.

---

## Архитектура пайплайна

```mermaid
flowchart TD
    A([📥 POST /process\nProcessRequest]) --> B

    subgraph async["⚡ async FastAPI handler"]
        B[ThreadPoolExecutor\nasync → sync bridge]
    end

    B --> C

    subgraph pipeline["🔄 Основной пайплайн"]
        C["📥 Шаг 1: Загрузка фото\n━━━━━━━━━━━━━━━━━━━\n• Источник по PHOTO_SOURCE:\n   - backend → httpx.AsyncClient,\n     GET /api/v1/photos/{id}/file/\n     (asyncio.gather параллельно)\n   - s3 → boto3 + ThreadPoolExecutor\n• Вход: photo_ids\n• Выход: dict[photo_id → bytes]\n• Ошибки: пропуск с логированием"]

        C --> ORI["📐 Шаг 1б: Определение ориентации\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n• PIL.Image → width / height\n• width ≥ height → landscape\n• width < height → portrait\n• Квадрат → landscape\n• Выход: dict[photo_id → Orientation]"]

        ORI --> V1{Доступных фото\n≥ min_photos?}
        V1 -->|Нет| ERR1([❌ HTTP 503\nНедостаточно фото])
        V1 -->|Да| D

        D["📐 Шаг 2: Подсчёт слотов шаблона\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n• Обход: Template → Pages → Slots\n• Проверка: n_slots > 0\n• Выход: n_slots"]

        D --> E["🧮 Шаг 3: Расчёт n_select\n━━━━━━━━━━━━━━━━━━━━━━━━━━\nn_select = max(min_photos,\n  min(n_slots, max_photos))\ncap by len(available_photos)"]

        E --> F["🧠 Шаг 4: CLIP-эмбеддинги\n(Vision Encoder)\n━━━━━━━━━━━━━━━━━━━━━━━━\n• Кэшированная модель ViT-B/32\n• bytes → PIL.Image → preprocess\n• Батчевый инференс (CPU)\n• L2-нормализация\n• Выход: dict[photo_id → вектор 512d]"]

        F --> G["🗂️ Шаг 5: KMeans кластеризация\n(Визуальное разнообразие)\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n• n_clusters = min(n_embeds, n_select)\n• sklearn KMeans, seed=42\n• Выход: dict[cluster_id → [photo_ids]]"]

        G --> H["📊 Шаг 6: Оценка качества\n(Техническое качество)\n━━━━━━━━━━━━━━━━━━━━━━━━\n• Резкость: дисперсия Лапласиана\n• Экспозиция: 1 - |яркость - 0.5|×2\n• Итог: 0.6×резкость + 0.4×экспозиция\n• Нормализация min-max по батчу\n• Выход: dict[photo_id → score ∈ [0,1]]"]

        H --> I["🎯 Шаг 7: Round-Robin отбор\n(Разнообразие + Качество)\n━━━━━━━━━━━━━━━━━━━━━━━━\n• Сортировка внутри кластеров\n  по quality (убывание)\n• Round-robin по кластерам:\n  лучшее из кл.0, кл.1, кл.2...\n• Выход: list[n_select photo_ids]"]

        I --> J["🔤 Шаг 8: Переранжирование\nпо тексту (CLIP Text Encoder)\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n• Токенизация user_description\n• Текстовый вектор (L2-норм.)\n• Косинусное сходство:\n  text_vec · photo_embed\n• Сортировка по сходству (убывание)\n• Выход: list[photo_ids] по релевантности"]

        J --> K["📋 Шаг 9: Заполнение шаблона\n━━━━━━━━━━━━━━━━━━━━━━━━\n• Обход: Pages → Slots (по порядку)\n• Slot.required_orientation задан:\n  ищем фото с совпадающей ориент.\n  → fallback: первое доступное\n• required_orientation = null:\n  берём первое из ranked\n• Незаполненные слоты → null\n• Выход: FilledTemplate"]

        K --> CAP["💬 Шаг 10: Генерация подписей\n(AI Caption Generator)\n━━━━━━━━━━━━━━━━━━━━━━━━\n• Anthropic Claude или OpenRouter\n• Одна подпись на каждую страницу\n• Изображения → base64 JPEG ≤512px\n• Промпт: тема альбома + номер стр.\n• Пропускается без API-ключа\n• Выход: FilledTemplate + captions"]

        CAP --> COV["🎨 Шаг 11: Заполнение обложек\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n• mode=photo: ручной или авто-выбор\n  (front → лучшее, back → последнее)\n• mode=caption: ручная или авто-генерация\n  через тот же AI-бэкенд (≤5 фото)\n• Пропускается без обложек в шаблоне\n• Выход: FilledTemplate + covers"]
    end

    COV --> L([📤 HTTP 200\nProcessResponse\nfilled_template])

    style A fill:#4A90D9,color:#fff,stroke:#2C5F8A
    style L fill:#27AE60,color:#fff,stroke:#1A7A40
    style ERR1 fill:#E74C3C,color:#fff,stroke:#A93226
    style async fill:#FFF9E6,stroke:#F0C040
    style pipeline fill:#F0F8FF,stroke:#4A90D9
    style V1 fill:#F39C12,color:#fff,stroke:#D68910
    style ORI fill:#8E44AD,color:#fff,stroke:#6C3483
```

---

## Стратегия многомерного отбора

Пайплайн последовательно применяет четыре критерия отбора:

```
Разнообразие          Качество             Релевантность
     │                    │                     │
     ▼                    ▼                     ▼
KMeans               Quality Score         CLIP Text
Clustering           (Sharpness +          Re-ranking
(visual groups)      Exposure)             (user intent)
     │                    │                     │
     └──────────┬──────────┘                    │
                ▼                               │
         Round-Robin                            │
         Selection ──────────────────────────►  │
                                                ▼
                                     Final ranked photo list
```

| Шаг | Цель | Метод |
|-----|------|-------|
| Ориентация | Классификация portrait / landscape | PIL размеры изображения |
| Кластеризация | Покрытие визуального пространства | KMeans на CLIP-эмбеддингах |
| Quality Score | Технически хорошие снимки | Лапласиан + яркость |
| Round-Robin | Баланс разнообразия и качества | Циклический обход кластеров |
| Text Re-rank | Соответствие описанию пользователя | Косинусное сходство CLIP |
| Template Fill | Укладка фото в слоты с учётом ориентации | Жадный поиск по ориентации + fallback |
| Caption Gen | Контекстные подписи к страницам | Multimodal LLM (Claude / OpenRouter) |
| Cover Fill | Заполнение обложек альбома | Авто-выбор фото + LLM для названия |

---

## Обработка ошибок

Сервис реализует **graceful degradation** — частичные сбои не блокируют весь запрос:

| Сценарий | Поведение |
|----------|-----------|
| Ошибка загрузки отдельного фото из бекенда (HTTP 4xx/5xx, сетевая ошибка, таймаут) | Лог-предупреждение, фото пропускается |
| Ошибка загрузки отдельного фото из S3 | Лог-предупреждение, фото пропускается |
| Ошибка определения ориентации фото | Лог-предупреждение, фото пропускается из словаря ориентаций |
| Нет фото подходящей ориентации для слота | Fallback: первое доступное фото |
| Ошибка препроцессинга изображения | Лог-предупреждение, фото пропускается |
| Ошибка расчёта качества | Оценка качества = 0.0 |
| Пустое `user_description` | Порядок из Round-Robin сохраняется |
| Ошибка text re-ranking | Исходный порядок сохраняется |
| Доступных фото < `min_photos` | HTTP 422 |
| Шаблон без слотов | HTTP 422 |
| Ошибка кодирования фото для подписи | Лог-предупреждение, фото пропускается |
| Ошибка LLM при генерации подписи | Лог-предупреждение, `caption = null` |
| Нет `ANTHROPIC_API_KEY` и `OPENROUTER_API_KEY` | Подписи и авто-обложки пропускаются |

---

## Структура модулей

```
ml/
├── app/
│   ├── main.py              # FastAPI app, /process, /health, lifespan-инициализация клиентов
│   ├── config.py            # Settings (pydantic-settings, lru_cache, валидатор PHOTO_SOURCE)
│   ├── dependencies.py      # DI: s3_client, http_client, download_executor, clip_model
│   ├── schemas.py           # ProcessRequest, ProcessResponse, Template models
│   └── pipeline/
│       ├── __init__.py      # run_pipeline: диспетчер источника + оркестрация шагов
│       ├── backend_loader.py # Параллельная загрузка из бекенда (httpx.AsyncClient)
│       ├── s3_loader.py     # Параллельная загрузка из S3 (boto3 + ThreadPoolExecutor)
│       ├── embeddings.py    # CLIP image encoder + кэш модели
│       ├── clustering.py    # KMeans кластеризация
│       ├── quality.py       # Оценка резкости и экспозиции
│       ├── selector.py      # Round-robin отбор
│       ├── reranker.py      # CLIP text encoder + переранжирование
│       ├── orientation.py     # Определение ориентации фото (portrait / landscape)
│       ├── template_filler.py # Заполнение шаблона с учётом ориентации слотов
│       ├── caption_generator.py # LLM-генерация подписей (Anthropic / OpenRouter)
│       └── cover_filler.py  # Заполнение обложек (фото или подпись)
├── Dockerfile
├── requirements.txt
└── .env.example
```

---

## Деплой

**Docker:**
```bash
cd ml
cp .env.example .env   # выбрать PHOTO_SOURCE и заполнить соответствующие переменные
docker build -t keepmoments-ml .
docker run --env-file .env -p 8000:8000 keepmoments-ml
```

**Локально (Python 3.11+):**
```bash
cd ml
pip install -r requirements.txt
uvicorn app.main:app --reload
```
