# message-storage — переписка в PostgreSQL и читающие API

- **id:** `message-storage`
- **Статус:** delivered

## Что делает

Владеет схемой PostgreSQL и всей записью/чтением данных бота:

- по событию `MESSAGE_NEW` подтягивает отсутствующие профили авторов (через in-process порты `vk-profiles`),
  сохраняет сообщение вместе с пересланными как JSONB и публикует `MESSAGE_RECEIVED` и `SUMMARY_REQUESTED`;
- по запросу отдаёт сохранённые сообщения, последнее саммари, признак pending-саммари, профили; ведёт
  записи саммари по lifecycle (`PENDING` → `COMPLETED`/`FAILED`);
- при сохранении завершённого саммари публикует `SUMMARY_READY`.

Читать можно двумя входами к одному `port.input`: публичным REST `/persistence/*` и in-process адаптерами,
которые подключают модули `ai` и `persistence` без HTTP (ADR 0004).

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_NEW` из `all-events-queue` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/event/MessageNewEventProcessor.kt:12-22` |
| сохранение сообщения и публикация `MESSAGE_RECEIVED` + `SUMMARY_REQUESTED` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecase.kt:28-67` |
| `GET /persistence/messages/*`, `/summaries/*`, `/profiles/*`; `POST /persistence/summaries/*` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceDataController.kt:21-127` |
| контракт REST-контроллера и DTO | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceService.kt:18-90` |
| 9 in-process адаптеров `share`-портов чтения/записи саммари | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt:17-144` |
| реализация 9 операций и публикация `SUMMARY_READY` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:49-146` |
| вход сохранения сообщения | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/input/SaveMessageInputPort.kt:8-17` |
| запросы и ответы читающего контракта | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/input/PersistenceDataInputPort.kt:12-55` |
| jOOQ: таблицы и репозитории | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/Tables.kt:6-57`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqMessageRepository.kt:19-66`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqSummaryRepository.kt` |
| сериализация пересланных сообщений в JSONB | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/json/JacksonForwardedMessagesAdapter.kt:11-20` |
| профили: подтягивание недостающих и сохранение | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt:26-95` |

## Данные, которыми владеет

Схема и миграции — единственный владелец (другие модули данных в БД не видят):

- `messages` — сообщение, `forwarded_messages` как `JSONB`, индексы по `from_id`, `peer_id`, `date`;
- `vk_user_profiles` — профиль пользователя, индекс по `last_updated`;
- `vk_group_profiles` — профиль группы (id хранится отрицательным), индекс по `last_updated`;
- `summaries` — `PENDING`/`COMPLETED`/`FAILED`, диапазон сообщений, short/full, индексы по `peer_id`.

Миграции: `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml`,
`src/persistence/src/main/resources/db/changelog/changes/001-create-messages-table.yaml`,
`.../002-create-user-profiles-table.yaml`, `.../003-create-group-profiles-table.yaml`,
`.../004-create-summaries-table.yaml`. Смысл данных делят другие возможности: профили — `vk-profiles`,
саммари — `conversation-summary`, а схема остаётся здесь (см. таблицу ниже).

## Journey

- `journey-answer`, шаг 2: сохранение входящего сообщения и профилей.
- `journey-summary`, шаг 1: хранилище сообщений и записей саммари.
- `journey-stored-data`: REST `/persistence/*` — единственный публичный читающий API деплоя.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| таблица `messages` | `ai-answer` (контекст ответа), `conversation-summary` (выборка для саммари) | `message-storage` |
| читающая модель `StoredMessage` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/StoredMessage.kt`) | `ai-answer`, `conversation-summary` | `message-storage` |
| таблицы `vk_user_profiles`/`vk_group_profiles` | `vk-profiles` (заполняет через свои порты), `ai-answer`, `conversation-summary` (читают имена) | `message-storage` — схема, jOOQ, Liquibase |
| таблица `summaries` и статусы | `conversation-summary` (создаёт pending, завершает, помечает failed) | `message-storage` — схема, jOOQ, Liquibase |
| `PersistenceDataInputPort` | REST-контроллер и 9 in-process адаптеров | `message-storage` |
| события `MESSAGE_RECEIVED`, `SUMMARY_REQUESTED`, `SUMMARY_READY` | `answer-decision`/`ai-answer` (`MESSAGE_RECEIVED`), `conversation-summary` (`SUMMARY_REQUESTED`, `SUMMARY_READY`) | `message-storage` — единственный публикатор этих трёх |

## Зависит от

- `event-dispatch` — публикация событий и получение `MESSAGE_NEW`.
- `vk-profiles` — in-process порты `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort` для недостающих
  профилей.

## ADR

- ADR 0003 — модуль `persistence` видит только `share`, слои внутри модуля.
- ADR 0004 — читающие и пишущие контракты для других модулей — in-process порты `share`, не loopback-REST.
- ADR 0006 — datasource и Liquibase-конфигурация в `:src:app`.

## Acceptance criteria

- **AC-message-storage-1** — КОГДА in-process порт `FindStoredMessagesBefore`/`FindStoredMessagesBetween`
  получает запрос, СИСТЕМА ДОЛЖНА без искажений передать его в `PersistenceDataInputPort` и вернуть
  полученные сообщения. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt:38-62`.
- **AC-message-storage-2** — КОГДА in-process порт сохранения завершённого саммари получает id, short и full,
  СИСТЕМА ДОЛЖНА передать их в `PersistenceDataInputPort` как `SaveCompletedSummary`. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt:111-123`.

## Пробелы приёмки

- Сохранение в PostgreSQL (`SaveMessageUsecase` + jOOQ) не покрыто ни одним тестом: интеграционный тест
  доходит до `messages.send`, но не проверяет строки в БД, поэтому пустая история не валит тест.
- REST `/persistence/*` не покрыт ни одним HTTP-тестом.
- Публикация `SUMMARY_READY` при сохранении завершённого саммари не покрыта тестом —
  `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:132-144`.
