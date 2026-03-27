# KeepMoments backend

Базовый сервис на Go 1.25 со слоями:

- `internal/presentation` - HTTP API.
- `internal/logic` - бизнес-логика для шаблонов и фото.
- `internal/connections` - подключения к PostgreSQL, S3 и автозапуск миграций.
- `internal/repository` - работа с таблицами `templates` и `photos`.

## Быстрый старт

1. Проверьте [`.env`](C:\Users\lomg\Desktop\KeepMoments\backend\.env) или скопируйте [`.env.example`](C:\Users\lomg\Desktop\KeepMoments\backend\.env.example).
2. Запустите `docker-compose up -d --build`.
3. API будет доступен на `http://localhost:8080`.

## Эндпоинты

- `GET /health`
- `POST /api/v1/templates`
- `GET /api/v1/templates`
- `GET /api/v1/templates/{id}`
- `DELETE /api/v1/templates/{id}`
- `POST /api/v1/photos`
- `GET /api/v1/photos/{id}`
- `DELETE /api/v1/photos/{id}`

## Примеры

Создать шаблон:

```bash
curl -X POST http://localhost:8080/api/v1/templates \
  -H "Content-Type: application/json" \
  -d '{"name":"basic","description_json":{"layout":"story","ratio":"9:16"}}'
```

Загрузить фото:

```bash
curl -X POST http://localhost:8080/api/v1/photos \
  -F "template_id=1" \
  -F "description_json={\"camera\":\"iphone\",\"tags\":[\"travel\"]}" \
  -F "file=@./example.jpg"
```
