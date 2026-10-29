# vk-profiles — профили участников VK

- **id:** `vk-profiles`
- **Статус:** delivered

## Что делает

Отдаёт профили пользователей и групп: батчем по списку id из VK API (`users.get`, `groups.getById`) через
публичный REST и через in-process порты `share`, которыми пользуется `message-storage` при сохранении
сообщения. Группы нормализуются к отрицательному id: запрос уходит с `-id`, ответ возвращается как `-id`
(`VkGetGroupProfilesBatchAdapter`). Ошибка VK — `VkApiException`.

Кэша и TTL нет: поле `lastUpdated` есть в модели и колонке, но логики устаревания в коде нет; отсутствующие
профили запрашиваются каждый раз, когда их нет в локальной таблице.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `GET /api/profiles/users/batch`, `GET /api/profiles/groups/batch` | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/rest/VkFacadeController.kt:14-32` |
| use case профилей | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/usecase/profile/VkProfileUsecase.kt:9-21` |
| команда: пользователи и группы раздельно | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/command/getProfile/GetProfileCommandImpl.kt:12-30` |
| VK API: `users.get`, `groups.getById`, отрицательный id группы | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/circuitbreaker/GetProfileAdapter.kt:12-73` |
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
- Отдельный внешний вход: REST `/api/profiles/*` для клиентов, которым нужны профили без ответа бота.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `VkUserProfile`, `VkGroupProfile` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/VkUserProfile.kt`, `.../VkGroupProfile.kt`) | `message-storage` (сохранение), `ai-answer` и `conversation-summary` (имена) | `vk-profiles` |
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

## Пробелы приёмки

- Запросы к VK API и нормализация групп к отрицательному id не покрыты тестом
  (`src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/circuitbreaker/GetProfileAdapter.kt`).
- REST `/api/profiles/*` не покрыт ни одним HTTP-тестом.
- Сохранение профилей (`FetchUserProfileCommandImpl`, `SaveUserProfilePort`) не покрыто тестом.
- `lastUpdated` не имеет логики устаревания — кэша нет; в документации это не должно называться кэшем.
