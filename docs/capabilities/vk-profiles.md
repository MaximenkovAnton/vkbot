# vk-profiles — профили участников VK

- **id:** `vk-profiles`
- **Статус:** delivered

## Что делает

Отдаёт профили пользователей и групп: батчем по списку id из VK API (`users.get`, `groups.getById`) через
публичный REST и через in-process порты `share`, которыми пользуются `message-storage` при сохранении
сообщения и `daily-greetings` при чтении профилей по дате рождения. Группы нормализуются к отрицательному
id: запрос уходит с `-id`, ответ возвращается как `-id` (`VkGetGroupProfilesBatchAdapter`). Ошибка VK —
`VkApiException`.

Профили хранятся в PostgreSQL (`message-storage`); `lastUpdated` — дата последней актуализации. Профиль
считается устаревшим через месяц: при входящем сообщении `message-storage` перезапрашивает его из VK,
обновляет изменившиеся поля и сохраняет прежние значения полей, которых VK больше не отдаёт
(`FetchUserProfileCommandImpl`). Отсутствующий профиль запрашивается сразу.

Пользователь запрашивается одним `users.get` со всеми полями, которые отдаёт VK, кроме фото/аватарки
(`photo_*`, `crop_photo`, `has_photo`); весь ответ складывается в `VkUserProfile.profileData` (JSONB
`vk_user_profiles.profile_data`), а при актуализации `message-storage` пишет историю изменений
([ADR 2026-11-10](<../adr/2026-11-10: Полный профиль VK, история изменений и статистика активности.md>)).

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `GET /api/profiles/users/batch`, `GET /api/profiles/groups/batch` | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/rest/VkFacadeController.kt:14-32` |
| use case профилей | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/usecase/profile/VkProfileUsecase.kt:9-21` |
| команда: пользователи и группы раздельно | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/command/getProfile/GetProfileCommandImpl.kt:12-30` |
| VK API: `users.get` со всеми полями пользователя (кроме фото), `groups.getById`, отрицательный id группы | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/circuitbreaker/GetProfileAdapter.kt:12-77` |
| REST-клиент VK для профилей | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkClient.kt:27-38` |
| in-process порты для других модулей | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36` |
| контракт портов в `share` | `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetUserProfilesBatchPort.kt`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetGroupProfilesBatchPort.kt` |
| чтение сохранённых профилей (`message-storage`) | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceDataController.kt:108-126` |

Конфигурация: `vk.api.token`, `vk.api.version`, `quarkus.rest-client.vk.*` в
`src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет: таблицы `vk_user_profiles` и `vk_group_profiles` — зона `message-storage`
(схема, jOOQ, Liquibase). Владеет контрактом профиля: составом полей `VkUserProfile`/`VkGroupProfile`,
правилом отрицательного id группы и маппингом VK-ответа в домен.

## Journey

- `journey-answer` — поддержка: имена участников в контексте ответа.
- `journey-summary` — поддержка: имена авторов в тексте саммари.
- `journey-greetings` — поддержка: имя именинника из `VkUserProfile` (поля профиля рендерит
  `daily-greetings`).
- Отдельный внешний вход: REST `/api/profiles/*` для клиентов, которым нужны профили без ответа бота.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `VkUserProfile`, `VkGroupProfile` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/VkUserProfile.kt`, `.../VkGroupProfile.kt`) | `message-storage` (сохранение), `ai-answer` и `conversation-summary` (имена), `daily-greetings` (имя именинника) | `vk-profiles` |
| `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort` | `message-storage` (`FetchUserProfileCommandImpl`) | `vk-profiles` |
| таблицы `vk_user_profiles`/`vk_group_profiles` | `message-storage` пишет их своими портами | `message-storage` |
| `VkClient.getUsers`/`getGroups` и токен VK | `vk-message-send` использует тот же клиент для `messages.send` | `vk-profiles` — методы профилей; общая часть клиента — конфигурация деплоя (ADR 0006) |

## Зависит от

- Ни от одной возможности: самодостаточный поставщик данных; потребители подключаются портами и REST.

## ADR

- ADR 0003 — VK REST-клиент только в `adapter/output`, `share`-порты без реализаций.
- ADR 0004 — `message-storage` получает профили in-process портами, а не loopback-HTTP.
- ADR 0006 — токен VK и URL клиента в конфигурации деплоя.

## Acceptance criteria

- **AC-vk-profiles-1** — КОГДА in-process порт запрашивает профили пользователей батчем, СИСТЕМА ДОЛЖНА
  передать только пользовательские id и вернуть профили. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapterTest.kt:16-34`.
- **AC-vk-profiles-2** — КОГДА in-process порт запрашивает профили групп батчем, СИСТЕМА ДОЛЖНА передать
  только групповые id и вернуть профили. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapterTest.kt:36-52`.
- **AC-vk-profiles-3** — КОГДА профиль пользователя запрашивается из VK, СИСТЕМА ДОЛЖНА передать все поля
  `users.get`, кроме фото/аватарки, и вернуть весь ответ VK в `profileData` без фото-полей. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/circuitbreaker/VkGetUserProfilesBatchAdapterTest.kt:17-75`.

## Пробелы приёмки

- Нормализация групп к отрицательному id не покрыта тестом; поля пользователя и исключение фото покрыты
  `VkGetUserProfilesBatchAdapterTest`.
- REST `/api/profiles/*` не покрыт ни одним HTTP-тестом.
- Сохранение профилей jOOQ-репозиториями (`SaveUserProfilePort`/`SaveGroupProfilePort`) не покрыто тестом:
  `FetchUserProfileCommandImplTest` проверяет слияние на fake-портах, `ProfileHistoryAndStatisticsIntegrationTest`
  — запись профиля и истории в PostgreSQL, `ProfileStorageIntegrationTest` — чтение сохранённых строк.
- `profileData` у профилей, сохранённых до ADR 2026-11-10, пуст до первой актуализации; история за это окно
  пишется только по типизированным полям (named gap ADR 2026-11-10).
- Логика устаревания профилей живёт в `message-storage`; здесь проверены только in-process порты, а не
  актуализация по `lastUpdated`.
