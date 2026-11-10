# message-storage — переписка в PostgreSQL и читающие API

- **id:** `message-storage`
- **Статус:** delivered

## Что делает

Владеет схемой PostgreSQL и всей записью/чтением данных бота:

- по событию `MESSAGE_NEW` подтягивает профили авторов сообщения и forward'ов: отсутствующие и устаревшие
  (`last_updated` старше месяца) запрашивает через in-process порты `vk-profiles`, изменившиеся
  typed-поля и человеческие ключи `details` обновляет, а поля, которых VK больше не отдаёт, оставляет
  прежними; перед обновлением пишет старые значения изменившихся typed-полей и `details` в
  `vk_user_profile_history` (JSONB-diff); сохраняет сообщение вместе с
  пересланными как JSONB и публикует `MESSAGE_RECEIVED` и `SUMMARY_REQUESTED`;
- по запросу отдаёт сохранённые сообщения, последнее саммари, признак pending-саммари, профили; ведёт
  записи саммари по lifecycle (`PENDING` → `COMPLETED`/`FAILED`);
- при сохранении завершённого саммари публикует `SUMMARY_READY`;
- при сохранении сообщения материализует статистику автора в той же транзакции: три upsert'а в
  `user_statistics` → `user_chat_statistics` → `user_hour_statistics` (+1 к `message_count`, +длина в
  code points к `total_length`, `first_message_at=LEAST`, `last_message_at=GREATEST`, UTC-бакет
  `(utc_date, utc_hour)`); по запросу читает только эти агрегаты, конвертируя UTC-бакет в
  `statistics.timezone` (число сообщений, суммарная и средняя длина, разбивка по чатам, гистограммы по
  часу и ISO-дню недели), и отдаёт историю профиля
  ([ADR 2026-11-10-vk-profile-history-and-statistics](../adr/2026-11-10-vk-profile-history-and-statistics.md),
  [ADR 2026-11-10-user-statistics-materialization](../adr/2026-11-10-user-statistics-materialization.md)).

Профиль, который VK не вернул по запросу, не перезаписывается: сохранённые данные остаются, а строка
остаётся устаревшей и будет перезапрошена при следующем сообщении.

Читать можно двумя входами к одному `port.input`: публичным REST `/persistence/*` и in-process адаптерами,
которые подключают модули `ai` и `persistence` без HTTP (ADR 2026-10-28-in-process-ports-instead-of-loopback-rest).

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_NEW` из `all-events-queue` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/event/MessageNewEventProcessor.kt:12-22` |
| сохранение сообщения и публикация `MESSAGE_RECEIVED` + `SUMMARY_REQUESTED` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecase.kt:28-67` |
| `GET /persistence/messages/*`, `/summaries/*`, `/profiles/*`, `/profiles/users/{id}/history`, `/statistics/users/{id}`; `POST /persistence/summaries/*` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceDataController.kt:21-154` |
| контракт REST-контроллера и DTO | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceService.kt:24-97` |
| 18 in-process адаптеров `share`-портов чтения/записи сообщений, саммари, позиции `ts`, профилей по дате рождения, чатов человека, claim поздравления и трёх read-only чтений сводки «что нового» | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` |
| реализация 20 операций (включая три чтения сводки «что нового» — последнее сообщение автора, сообщения диапазона дат, саммари периода — а также историю профиля и статистику автора) и публикация `SUMMARY_READY` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt` |
| вход сохранения сообщения | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/input/SaveMessageInputPort.kt:8-17` |
| запросы и ответы читающего контракта | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/input/PersistenceDataInputPort.kt:75-149` |
| jOOQ: таблицы и репозитории | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/Tables.kt:6-82`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqMessageRepository.kt:14-130`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqSummaryRepository.kt`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqUserStatisticsRepository.kt` |
| история профиля и статистика автора | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqVkUserProfileRepository.kt:22-148`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqUserStatisticsRepository.kt:17-124` |
| материализация статистики при сохранении сообщения: порт, адаптер и jOOQ-writer | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/port/output/persistence/UpdateUserStatisticsPort.kt`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/UpdateUserStatisticsAdapter.kt`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqUserStatisticsWriterRepository.kt` |
| changeset 009 и ручной backfill-скрипт | `src/persistence/src/main/resources/db/changelog/changes/009-create-user-statistics-tables.yaml`, `src/persistence/src/main/resources/db/backfill/backfill-user-statistics.sql` |
| сериализация пересланных сообщений в JSONB | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/json/JacksonForwardedMessagesAdapter.kt:11-20` |
| claim поздравления: один autocommit `INSERT … ON CONFLICT DO NOTHING` в `greeting_log` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/JooqGreetingLogRepository.kt` |
| профили: подтягивание недостающих и сохранение | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt:18-153` |

## Данные, которыми владеет

Схема и миграции — единственный владелец (другие модули данных в БД не видят):

- `messages` — сообщение, `forwarded_messages` как `JSONB`, индексы по `from_id`, `peer_id`, `date`;
- `vk_user_profiles` — профиль пользователя, `profile_details JSONB` — человеческие значения полей VK
  (русские подписи кодов, title вместо объектов, без VK-id/пустых строк/волатильных полей),
  `last_updated` — дата последней актуализации, индекс по ней;
- `vk_user_profile_history` — JSONB-diff `{field: {old, new}}` по изменившимся typed-колонкам и ключам
  `details` одной актуализации (старые строки удалены changeset 008);
  `changed_at`, индекс `(profile_id, changed_at)`; строка появляется только при реальном изменении;
- `vk_group_profiles` — профиль группы, id хранится отрицательным (как в домене), `last_updated` — дата
  последней актуализации, индекс по ней;
- `summaries` — `PENDING`/`COMPLETED`/`FAILED`, диапазон сообщений, short/full, индексы по `peer_id`;
- `greeting_log` — одна строка на `(greeting_date, peer_id)` (PK), признак «поздравление за этот день уже
  отправлено»; пишется модулем `daily-greetings` через `ClaimDailyGreetingPort`, схема — здесь;
- `user_statistics` — материализованные итоги автора: `message_count`, `total_length` (code points),
  `first_message_at`, `last_message_at`, `updated_at`; одна строка на автора (PK `user_id`);
- `user_chat_statistics` — автор×чат: `message_count`, `total_length`, `last_message_at`;
  PK `(user_id, peer_id)`;
- `user_hour_statistics` — автор×UTC-дата×UTC-час: `message_count`; PK `(user_id, utc_date, utc_hour)`,
  CHECK `chk_user_hour_statistics_utc_hour` (`utc_hour BETWEEN 0 AND 23`); ретенции нет.

Миграции: `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml`,
`src/persistence/src/main/resources/db/changelog/changes/001-create-messages-table.yaml`,
`.../002-create-user-profiles-table.yaml`, `.../003-create-group-profiles-table.yaml`,
`.../004-create-summaries-table.yaml`, `.../005-create-long-poll-position-table.yaml`,
`.../006-create-greeting-log-table.yaml`, `.../007-add-user-profile-data-and-history.yaml`,
`.../008-replace-profile-data-with-human-details.yaml` (три changeSet'а: `add profile_details` → all-rows
`delete` истории с `lock_timeout` → `dropColumn` старой колонки),
`.../009-create-user-statistics-tables.yaml` (только DDL: три `createTable` + один `sql` с
`ALTER TABLE ... CHECK (utc_hour BETWEEN 0 AND 23)`). Переигровка накопленных `messages` в агрегаты —
отдельный ручной ops-скрипт `src/persistence/src/main/resources/db/backfill/backfill-user-statistics.sql`
(не миграция и не рантайм-бин: в файле нет `BEGIN`/`COMMIT`, запускается под `psql --single-transaction
-v ON_ERROR_STOP=1`, требует остановленного писателя, берёт `ACCESS EXCLUSIVE` + `lock_timeout` и делает
`ANALYZE`). Смысл данных делят другие возможности: профили — `vk-profiles`,
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
- ADR 2026-11-10-vk-profile-human-details — `profile_details` вместо сырого снимка, typed + человеческий diff истории, changeset 008.
- ADR 2026-11-10-user-statistics-materialization — материализованные агрегаты статистики автора: три таблицы, upsert в транзакции сообщения, ручной backfill-скрипт, конвертация UTC-бакетов на чтении.

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
- **AC-message-storage-5** — КОГДА при актуализации профиля изменились typed-поля или ключи `details`,
  СИСТЕМА ДОЛЖНА записать в `vk_user_profile_history` только изменившиеся ключи со старыми и новыми
  человеческими значениями (typed-ключ `birth_date`, не `bdate`); без изменений строки нет. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImplTest.kt`,
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/domain/profile/ProfileFieldsMergerTest.kt`.
- **AC-message-storage-6** — КОГДА запрошена статистика автора, СИСТЕМА ДОЛЖНА отдавать число сообщений,
  суммарную и среднюю длину, разбивку по чатам и гистограммы по часу/ISO-дню недели только из трёх
  агрегатных таблиц (`user_statistics`, `user_chat_statistics`, `user_hour_statistics`), конвертируя
  UTC-бакет в `statistics.timezone` на чтении; КОГДА у автора есть строки `messages`, но агрегатов нет,
  СИСТЕМА ДОЛЖНА отдавать нули. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/UserStatisticsMaterializationIntegrationTest.kt`.
- **AC-message-storage-7** — КОГДА профиль сохранён jOOQ и прочитан обратно, СИСТЕМА ДОЛЖНА писать/читать
  JSONB `vk_user_profiles.profile_details` и не иметь колонки сырого снимка; changeset 008 объявляет
  `add profile_details` → all-rows `delete` истории с `lock_timeout` → `dropColumn` старой колонки. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileHistoryAndStatisticsIntegrationTest.kt`,
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/migration/ProfileMigrationChangesetTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileMigrationIntegrationTest.kt`.
- **AC-message-storage-8** — КОГДА `SaveMessageUsecase` сохраняет сообщение, СИСТЕМА ДОЛЖНА в той же
  транзакции обновить агрегаты в фиксированном порядке `user_statistics` → `user_chat_statistics` →
  `user_hour_statistics` (+1 к `message_count`, +длина в code points к `total_length`,
  `first_message_at=LEAST`, `last_message_at=GREATEST`, UTC-бакет `(utc_date, utc_hour)`), так что сбой
  записи статистики откатывает и сообщение. Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecaseTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/UserStatisticsMaterializationIntegrationTest.kt`.
- **AC-message-storage-9** — КОГДА SQL-скрипт переигровки `backfill-user-statistics.sql` запускается
  вручную на БД с непустыми `messages` и пустыми агрегатами, СИСТЕМА ДОЛЖНА заполнить все три таблицы
  ровно по `messages`; КОГДА он запускается повторно, числа не меняются; КОГДА есть чужой писатель или
  скрипт падает в середине, СИСТЕМА ДОЛЖНА не изменить ни одной строки (fail fast по `lock_timeout`,
  полный откат транзакции вызывающего). Test:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/backfill/BackfillScriptContractTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/UserStatisticsMaterializationIntegrationTest.kt`.

## Пробелы приёмки

- Сохранение сообщения в `messages` проверяется косвенно: `UserStatisticsMaterializationIntegrationTest`
  сеет сообщения production-путём и сверяет агрегаты и `count(messages)`, отдельного теста
  `JooqMessageRepository.persist` нет.
- REST `/persistence/*` не покрыт ни одним HTTP-тестом; новые `/profiles/users/{id}/history` и
  `/statistics/users/{id}` проверены на уровне репозитория/БД, не HTTP.
- `profile_details` у старых профилей пуст до первой актуализации (backfill нет); при пустом прежнем
  снимке история пишется только по типизированным полям; текстовые поля VK нельзя очистить.
- Старые строки истории удалены changeset 008 без переноса; откат образа после 008 — только с
  восстановлением БД из бэкапа.
- Статистика материализована; дрейф возникает в окне отката образа и при ручной чистке `messages`:
  сообщения этого окна в агрегаты не попадут, согласованность восстанавливает ручной запуск
  `backfill-user-statistics.sql` (остановка писателя, `ACCESS EXCLUSIVE` + `lock_timeout`, `ANALYZE`);
  автоматического заполнения и рантайм-бина нет.
- У `messages` нет уникального ключа, поэтому повторная доставка `MESSAGE_NEW` создаёт дубль сообщения и
  дубль в агрегатах; дедупликации нет.
- Ретенции нет: `user_hour_statistics` растёт ≤ 24 строк на автора в сутки, `user_chat_statistics` —
  ≤ числа чатов автора; удаление строк `messages` агрегаты не пересчитывает (статистика от журнала не
  зависит).
- Публикация `SUMMARY_READY` при сохранении завершённого саммари не покрыта тестом —
  `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:255-268`.
- Запись профилей jOOQ-репозиториями не покрыта тестом: `ProfileStorageIntegrationTest` проверяет чтение
  сохранённых строк, `FetchUserProfileCommandImplTest` — слияние и сохранение на fake-портах.
