# KeepMoments

Приложение для автоматической сборки фотоальбомов из пользовательских фотографий:
пользователь выбирает снимки и задаёт описание, приложение формирует
структурированный альбом и экспортирует его в PDF.

> [!CAUTION]
> This is only MVP

## Компоненты

- [`mobile/`](mobile/README.md) — Android-клиент (Kotlin/Compose): экраны, работа с
  фото, тесты и QA-документация
- `backend/` — Go-бэкенд (REST API)
- `ml/` — Python ML-сервис (обработка фотографий)

## Сборка

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

`API_BASE_URL` задаётся в `local.properties` (`api.baseUrl=...`).

## Тесты

Unit-тесты (`src/test`) запускаются без эмулятора, сети и бэкенда:

```bash
./gradlew testDebugUnitTest
```

Инструментальные тесты (`src/androidTest`) требуют устройство или эмулятор:

```bash
./gradlew connectedDebugAndroidTest
```

**Покрытие:**

- `PhotoValidatorTest` — валидация формата, размера и разрешения фото;
- `AuthRepositoryTest` — вход/регистрация и разбор ошибок сервера;
- `TokenAuthenticatorTest` — обновление токена при `401` и защита от циклов;
- `DraftEditorViewModelTest` / `DraftsViewModelTest` — лимиты, дедуп, guard'ы
  создания книги, создание и правка черновиков;
- `DraftRepositoryTest` — видимость черновиков и работа с фото в Room;
- `DraftDatabaseMigrationTest` — миграция схемы БД *(нужен девайс)*;
- `AuthScreenTest` — состояния экрана авторизации *(нужен девайс)*.

Общие хелперы — в `src/test/.../testutil/`, фикстуры — в
`src/test/resources/fixtures/`.

## QA-документация

Ручное тестирование и тест-дизайн — в `docs/qa/`:

- [Тест-план](docs/qa/test-plan.md)
- [Тест-кейсы](docs/qa/test-cases.md)
- [Смоук-чек-лист](docs/qa/checklist-smoke.md)
- [Баг-репорты](docs/qa/bug-reports.md)
