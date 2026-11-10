# message-storage — переписка в PostgreSQL и читающие API

- **id:** `message-storage`
- **Статус:** delivered

## Что делает

Владеет схемой PostgreSQL и всей записью/чтением данных бота:

- по событию `MESSAGE_NEW` подтягивает профили авторов сообщения и forward'ов: отсутствующие и устаревшие
  (`last_updated` старше месяца) запрашивает через in-process порты `vk-profiles`, изменившиеся поля
  обновляет, а поля, которых VK больше не отдаёт, оставляет прежними; сохраняет сообщение вместе с
  пересланными как JSONB и публикует `MESSAGE_RECEIVED` и `SUMMARY_REQUESTED`;
- по запросу отдаёт сохранённые сообщения, последнее саммари, признак pending-саммари, профили; ведёт
  записи саммари по lifecycle (`PENDING` → `COMPLETED`/`FAILED`);
- при сохранении завершённого саммари публикует `SUMMARY_READY`.

Профиль, который VK не вернул по запросу, не перезаписывается: сохранённые данные остаются, а строка
остаётся устаревшей и будет перезапрошена при следующем сообщении.

Читать можно двумя входами к одному `port.input`: публичным REST `/persistence/*` и in-process адаптерами,
которые подключают модули `ai` и `persistence` без HTTP (ADR 2026-10-28-in-process-ports-instead-of-loopback-rest).

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_NEW` из `all-events-queue` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/event/MessageNewEventProcessor.kt:12-22` |
| сохранение сообщения и публикация `MESSAGE_RECEIVED` + `SUMMARY_REQUESTED` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecase.kt:28-67` |
| `GET /persistence/messages/*`, `/summaries/*`, `/profiles/*`; `POST /persistence/summaries/*` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceDataController.kt:21-126` |
| контракт REST-контроллера и DTO | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceService.kt:18-90` |
| 18 in-process адаптеров `share`-портов чтения/записи сообщений, саммари, позиции `ts`, профилей по дате рождения, чатов человека, claim поздравления и трёх read-only чтений сводки «что нового» | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` |
| реализация 18 операций (включая три чтения сводки «что нового»: последнее сообщение автора, сообщения диапазона дат, саммари периода) и публикация `SUMMARY_READY` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt` |
| вход сохранения сообщения | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/input/SaveMessageInputPort.kt:8-17` |
| запросы и ответы читающего контракта | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/input/PersistenceDataInputPort.kt:65-135` |
| jOOQ: таблицы и репозитории | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/Tables.kt:6-72`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqMessageRepository.kt:14-130`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqSummaryRepository.kt` |
| сериализация пересланных сообщений в JSONB | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/json/JacksonForwardedMessagesAdapter.kt:11-20` |
| claim поздравления: один autocommit `INSERT … ON CONFLICT DO NOTHING` в `greeting_log` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqGreetingLogRepository.kt` |
| профили: подтягивание недостающих и сохранение | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt:26-95` |

## Данные, которыми владеет

Схема и миграции — единственный владелец (другие модули данных в БД не видят):

- `messages` — сообщение, `forwarded_messages` как `JSONB`, индексы по `from_id`, `peer_id`, `date`;
- `vk_user_profiles` — профиль пользователя, `last_updated` — дата последней актуализации, индекс по ней;
- `vk_group_profiles` — профиль группы, id хранится отрицательным (как в домене), `last_updated` — дата
  последней актуализации, индекс по ней;
- `summaries` — `PENDING`/`COMPLETED`/`FAILED`, диапазон сообщений, short/full, индексы по `peer_id`;
- `greeting_log` — одна строка на `(greeting_date, peer_id)` (PK), признак «поздравление за этот день уже
  отправлено»; пишется модулем `daily-greetings` через `ClaimDailyGreetingPort`, схема — здесь.

Миграции: `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml`,
`src/persistence/src/main/resources/db/changelog/changes/001-create-messages-table.yaml`,
`.../002-create-user-profiles-table.yaml`, `.../003-create-group-profiles-table.yaml`,
`.../004-create-summaries-table.yaml`, `.../005-create-long-poll-position-table.yaml`,
`.../006-create-greeting-log-table.yaml`. Смысл данных делят другие возможности: профили — `vk-profiles`,
саммари — `conversation-summary`, поздравления — `daily-greetings`; схема остаётся здесь (см. таблицу ниже).

## Journey

- `journey-answer`, шаг 2: сохранение входящего сообщения и профилей.
- `journey-summary`, шаг 1: хранилище сообщений и записей саммари.
- `journey-stored-data`: REST `/persistence/*` — единственный публичный читающий API деплоя.
- `journey-greetings`: чтение групповых чатов и чатов именинника, профили по `(day, month)` и claim
  `greeting_log` — шаги 1 и 3 этого пути.
- `journey-news-digest`: чтение саммари периода и свежих сообщений через три in-process порта — шаг
  `chat-news-digest`.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| таблица `messages` | `ai-answer` (контекст ответа), `conversation-summary` (выборка для саммари), `daily-greetings` (чаты, где писал именинник), `chat-news-digest` (свежие сообщения периода) | `message-storage` |
| читающая модель `StoredMessage` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/StoredMessage.kt`) | `ai-answer`, `conversation-summary`, `chat-news-digest` | `message-storage` |
| таблица `vk_user_profiles`/`vk_group_profiles` | `vk-profiles` (заполняет через свои порты), `ai-answer`, `conversation-summary`, `daily-greetings` (профили по дате рождения) | `message-storage` — схема, jOOQ, Liquibase |
| таблица `summaries` и статусы | `conversation-summary` (создаёт pending, завершает, помечает failed); `chat-news-digest` читает только `COMPLETED` | `message-storage` — схема, jOOQ, Liquibase |
| `PersistenceDataInputPort` | REST-контроллер и 18 in-process адаптеров | `message-storage` |
| таблица `greeting_log` и семантика claim | `daily-greetings` (вызывает `ClaimDailyGreetingPort`) | `message-storage` — схема, jOOQ, Liquibase |
| `vk_user_profiles.birth_date` | `daily-greetings` читает через `FindUserProfilesByBirthDatePort`; заполняет `vk-profiles` | `message-storage` — схема |
| события `MESSAGE_RECEIVED`, `SUMMARY_REQUESTED`, `SUMMARY_READY` | `answer-decision`/`ai-answer` (`MESSAGE_RECEIVED`), `conversation-summary` (`SUMMARY_REQUESTED`, `SUMMARY_READY`) | `message-storage` — единственный публикатор этих трёх |

## Зависит от

- `event-dispatch` — публикация событий и получение `MESSAGE_NEW`.
- `vk-profiles` — in-process порты `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort` для недостающих
  профилей.

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — модуль `persistence` видит только `share`, слои внутри модуля.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — читающие и пишущие контракты для других модулей — in-process порты `share`, не loopback-REST.
- ADR 2026-10-28-configuration-and-secrets — datasource и Liquibase-конфигурация в `:src:app`.

## Acceptance criteria

- **AC-message-storage-1** — КОГДА in-process порт `FindStoredMessagesBefore`/`FindStoredMessagesBetween`
  получает запрос, СИСТЕМА ДОЛЖНА без искажений передать его в `PersistenceDataInputPort` и вернуть
  полученные сообщения. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt:27-77`.
- **AC-message-storage-2** — КОГДА in-process порт сохранения завершённого саммари получает id, short и full,
  СИСТЕМА ДОЛЖНА передать их в `PersistenceDataInputPort` как `SaveCompletedSummary`. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt:147-169`.

- **AC-message-storage-3** — КОГДА приходит сообщение, а профиль его автора отсутствует или `last_updated`
  старше месяца, СИСТЕМА ДОЛЖНА запросить профиль через in-process порт `vk-profiles`, заменить изменившиеся
  поля и сохранить прежние значения полей, которых VK больше не отдаёт. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImplTest.kt`.
- **AC-message-storage-4** — КОГДА профиль группы сохранён с отрицательным id, СИСТЕМА ДОЛЖНА находить его
  по тому же id и отдавать вместе с датой последней актуализации. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileStorageIntegrationTest.kt`.

## Пробелы приёмки

- Сохранение в PostgreSQL (`SaveMessageUsecase` + jOOQ) не покрыто ни одним тестом: интеграционный тест
  доходит до `messages.send`, но не проверяет строки в БД, поэтому пустая история не валит тест.
- REST `/persistence/*` не покрыт ни одним HTTP-тестом.
- Публикация `SUMMARY_READY` при сохранении завершённого саммари не покрыта тестом —
  `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:255-268`.
- Запись профилей jOOQ-репозиториями не покрыта тестом: `ProfileStorageIntegrationTest` проверяет чтение
  сохранённых строк, `FetchUserProfileCommandImplTest` — слияние и сохранение на fake-портах.
