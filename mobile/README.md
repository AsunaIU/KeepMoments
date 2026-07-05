# KeepMoments

Android-приложение для автоматической сборки фотоальбомов. Пользователь выбирает фотографии и 
задаёт описание, после чего приложение автоматически формирует структурированный альбом и 
экспортирует его в PDF.

**Мобильный клиент на Kotlin/Android; серверная часть — Go-бэкенд и Python ML-сервис**

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
