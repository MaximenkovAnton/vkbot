# vk-long-poll — приём событий VK через Bots Long Poll

- **id:** `vk-long-poll`
- **Статус:** delivered

## Что делает

`receiver` сам опрашивает Bots Long Poll API: `groups.getLongPollServer` через REST-клиент
`configKey = "vk"` (`https://api.vk.ru/method`), затем циклический `a_check` по адресу `server` из ответа —
`{server}?act=a_check&key=…&ts=…&wait=25`. Обновления `message_new`/`message_reply` проходят DMZ-маппинг и
становятся доменным `Message`; публикуется `MESSAGE_NEW`. Позиция опроса `ts` лежит в PostgreSQL в таблице
`long_poll_position` и сохраняется только после успешной публикации, поэтому сбой между публикацией и
сохранением даёт дубликат, но не потерю. HTTP-входа у `receiver` нет: `POST /vk/callback` отвечает 404, а
ключей `vk.secret`/`vk.confirmation-code` в конфигурации нет — приём событий идёт только опросом
([ADR 2026-11-05-bots-long-poll-transport](../adr/2026-11-05-bots-long-poll-transport.md)).

Коды VK `failed` восстанавливают сессию: `1` — продолжить с новым `ts` из ответа (часть истории VK потерял,
WARN), `2` — новая сессия с тем же `ts`, `3` — новая сессия и `ts` из `groups.getLongPollServer`. При сбое
публикации пауза 5 с и то же обновление повторяется; при сетевой ошибке или 5xx на `a_check` пауза нарастает
до 60 с и сбрасывается после успеха. Если `groups.getLongPollServer` вернул `error` вместо `response`
(токен не от этого сообщества, Long Poll API выключен), сессию получить не удаётся: в ERROR уходит код,
подкод и сообщение VK, запрос повторяется с той же нарастающей паузой.

Каждое обработанное обновление сначала регистрируется в inbox: `RegisterIncomingMessagePort` пишет сырой
JSON события в `incoming_messages` (unique по `peer_id` + `conversation_message_id`) и возвращает id записи,
который едет в `Message.inboxMessageId` публикуемого `MESSAGE_NEW`; только после регистрации обновление
публикуется и сохраняется `ts`. Сбой регистрации или публикации не сохраняет `ts` — обновление повторяется,
а повторная запись гасится upsert'ом по ключу дедупа.

Опрос VK выполняет держатель лидер-лиза в PostgreSQL: перед каждым `a_check` реплика вызывает
`AcquireLongPollLeasePort` с owner-UUID и TTL; получив лиз — опрашивает VK, не получив — стоит в standby и VK
не трогает. Экземпляров может быть несколько: лидер один, остальные ждут, пересменка занимает не больше TTL,
а после пересменки `ts` читается из `long_poll_position`, а не из памяти реплики. На `ShutdownEvent` лиз
освобождается. Смены лидерства логируются: INFO на получение лиза, первый standby и release, WARN на takeover
и потерю лиза; PII в этих строках нет.

Записи inbox старше ретенции удаляются по cron: каждая реплика независимо вызывает
`PurgeIncomingMessagesPort` с `received_at < now - retention`, один `DELETE` за прогон; счётчик уходит в INFO.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| цикл опроса: виртуальный поток, старт по `StartupEvent`, стоп по `ShutdownEvent`, `vk.longpoll.enabled` | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunner.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunner.kt) |
| `groups.getLongPollServer` и `a_check`, read-timeout 60 с | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapter.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapter.kt) |
| порты Long Poll: `GetLongPollServerPort` (сессия) и `CheckLongPollPort` (`a_check`) | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/output/LongPollPort.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/output/LongPollPort.kt) |
| тип обновления (`MESSAGE_NEW`, `MESSAGE_REPLY`, `UNKNOWN`) | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/domain/vo/VkUpdateType.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/domain/vo/VkUpdateType.kt) |
| входной контракт use case `VkUpdateInputPortRequest(type, message, rawUpdate)` | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/ReceiveMessageInputPort.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/ReceiveMessageInputPort.kt) |
| inbox-регистрация до публикации: `RegisterIncomingMessagePort` → `Message.inboxMessageId` → `MESSAGE_NEW` | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt) |
| лидер-лиз в цикле опроса: `AcquireLongPollLeasePort`/`ReleaseLongPollLeasePort`, standby, takeover | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunner.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunner.kt) |
| автоочистка inbox: cron-раннер, ретенция, `PurgeInboxInputPort` | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/schedule/InboxCleanupRunner.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/schedule/InboxCleanupRunner.kt), [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/RunInboxCleanupUsecase.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/RunInboxCleanupUsecase.kt), [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/PurgeInboxInputPort.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/PurgeInboxInputPort.kt) |
| маршрутизация обновления и публикация `MESSAGE_NEW` | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt), [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/command/sendVkEvent/PublishVkEventCommandImpl.kt:15-23`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/command/sendVkEvent/PublishVkEventCommandImpl.kt#L15-L23) |
| DMZ-маппинг VK JSON → `Message` (fwd, reply, wall) | [`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt:13-58`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt#L13-L58) |
| позиция `ts`: таблица одной строки `long_poll_position` | [`src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/LongPollPositionTable.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/LongPollPositionTable.kt) |
| порты позиции `ts` | [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindLongPollTsPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindLongPollTsPort.kt), [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/SaveLongPollTsPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/SaveLongPollTsPort.kt) |
| VO позиции | [`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/LongPollTs.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/LongPollTs.kt) |

Конфигурация: `vk.group.id` и `vk.longpoll.enabled` в [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml)
(`VK_GROUP_ID`, `VK_LONGPOLL_ENABLED`); политика inbox — четыре ключа `vk.inbox.*` там же (`VK_INBOX_LEASE_TTL`,
`VK_INBOX_STANDBY_PAUSE`, `VK_INBOX_RETENTION`, `VK_INBOX_CLEANUP_CRON`).

## Данные

Владеет позицией опроса: одна строка `long_poll_position` (`ts`, changeset
`005-create-long-poll-position-table`; схема — владение `persistence`). Через share-порты пишет
`incoming_messages` (сырой JSON события в `payload`, `received_at`, unique по `peer_id` +
`conversation_message_id`, changeset `011-create-incoming-messages`) и `long_poll_lease` (owner, `lease_until`,
changeset `012-create-long-poll-lease`); схема этих таблиц — владение `message-storage`, receiver — их
единственный потребитель и владелец политики (когда регистрировать, TTL лиза, ретенция). `incoming_messages`
режется cron-очисткой: `received_at < now - vk.inbox.retention`, один `DELETE` на прогон, каждая реплика
независимо. `server` и `key` сессии не хранятся — на старте сессия запрашивается заново.

## Journey

`journey-answer`, шаг 1: событие VK становится `MESSAGE_NEW` и уходит в цепочку сохранения.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| модель `Message` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt)) | `message-storage`, `answer-decision`, `ai-answer` | `vk-long-poll` — единственный producer: здесь VK JSON становится доменом |
| событие `MESSAGE_NEW` и его payload `Message` | `message-storage` (consumer) | `vk-long-poll` |
| `PublishEventOutputPort` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/PublishEventOutputPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/PublishEventOutputPort.kt)) | `message-storage`, `answer-decision`, `ai-answer`, `conversation-summary` | `event-dispatch` |
| контракт позиции `LongPollTs` и порты `FindLongPollTsPort`/`SaveLongPollTsPort` | `persistence` (реализация через `FindLongPollTsInProcessAdapter`/`SaveLongPollTsInProcessAdapter`) | `vk-long-poll` — единственный потребитель позиции |
| порты inbox и лиза `RegisterIncomingMessagePort`, `PurgeIncomingMessagesPort`, `AcquireLongPollLeasePort`, `ReleaseLongPollLeasePort` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/RegisterIncomingMessagePort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/RegisterIncomingMessagePort.kt)) | `persistence` (реализация через in-process адаптеры) | `vk-long-poll` — единственный потребитель; схема `incoming_messages`/`long_poll_lease` — `message-storage` |

## Зависит от

- `event-dispatch` — публикация `MESSAGE_NEW` идёт через `PublishEventOutputPort`, реализованный
  `PublishEventOutputAdapter`.
- `message-storage` — хранение `ts` через share-порты `FindLongPollTsPort`/`SaveLongPollTsPort`, а также
  inbox-регистрация, лидер-лиз и очистка через `RegisterIncomingMessagePort`,
  `PurgeIncomingMessagesPort`, `AcquireLongPollLeasePort`, `ReleaseLongPollLeasePort`; HTTP-вызова
  нет, реализация in-process.

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — разбор ответа VK (`jakarta.json`) живёт во входном адаптере, порты опроса остаются на плоских
  типах; `server`/`key`/`ts` не протекают в домен.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — межмодульное взаимодействие событием RabbitMQ (`MESSAGE_NEW`) и in-process портами (позиция
  `ts`), без HTTP-вызова.
- ADR 2026-10-28-configuration-and-secrets — `vk.group.id` и `vk.longpoll.enabled` читаются из одного production-конфига, секретов в дереве
  нет.
- ADR 2026-11-05-bots-long-poll-transport — транспорт Bots Long Poll, `ts` в PostgreSQL, дубликат вместо потери;
  ограничение «один экземпляр» снято лидер-лизом (ADR 2026-11-23-inbox-and-poller-lease).
- ADR 2026-11-23-inbox-and-poller-lease — inbox `incoming_messages` (сырой JSON, ретенция 24 ч),
  лидер-лиз `long_poll_lease`, реплики и rolling update после перехода, `random_id` из inbox id.

## Acceptance criteria

- **AC-vk-long-poll-1** — КОГДА `a_check` вернул обновление с сообщением, СИСТЕМА ДОЛЖНА опубликовать
  `MESSAGE_NEW`, а `ts` сохранить только после успешной публикации. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt).
- **AC-vk-long-poll-2** — КОГДА в PostgreSQL есть сохранённый `ts`, СИСТЕМА ДОЛЖНА продолжить опрос с него,
  а не с `ts` новой сессии, и сохранить его для следующего запроса. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt),
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/LongPollPositionIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/LongPollPositionIntegrationTest.kt) (реальная БД:
  засеянный `ts` уходит в первый `a_check`, ответный `ts` ложится в таблицу).
- **AC-vk-long-poll-3** — КОГДА VK вернул `failed: 1`, `failed: 2` или `failed: 3`, СИСТЕМА ДОЛЖНА
  продолжить опрос по коду (`ts` из ответа / новая сессия с тем же `ts` / новая сессия и новый `ts`) и не
  остановиться. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt).
- **AC-vk-long-poll-4** — КОГДА публикация `MESSAGE_NEW` бросила исключение, СИСТЕМА ДОЛЖНА не сохранять
  `ts`, подождать 5 с и повторить то же обновление. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt).
- **AC-vk-long-poll-5** — КОГДА тестовый профиль, `POST /vk/callback` ДОЛЖЕН вернуть 404, а обновление из
  Long Poll — дойти до `messages.send` с `peer_id` исходного сообщения. Test:
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt).
- **AC-vk-long-poll-6** — КОГДА `groups.getLongPollServer` вернул `error` вместо `response`, СИСТЕМА ДОЛЖНА
  бросить `VkLongPollServerException` (`VkBotAppException`, статус `EXTERNAL_FAILURE`) с кодом, подкодом
  и сообщением VK, чтобы отказ доступа попадал в лог, а не терялся в общей ошибке. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapterTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapterTest.kt).
- **AC-vk-long-poll-7** — КОГДА обработанное обновление приходит из Long Poll, СИСТЕМА ДОЛЖНА сначала
  зарегистрировать его в inbox через `RegisterIncomingMessagePort` (payload — сырой JSON события) и только
  после этого опубликовать `MESSAGE_NEW` с `Message.inboxMessageId`; сбой регистрации публикацию не делает
  и `ts` не сохраняет. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecaseTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecaseTest.kt),
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt).
- **AC-vk-long-poll-8** — КОГДА реплик несколько, СИСТЕМА ДОЛЖНА опрашивать VK только под лидер-лизом:
  acquire перед `a_check`, standby без опроса, release на shutdown, takeover с `ts` из `long_poll_position`,
  логи переходов без PII. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt).
- **AC-vk-long-poll-9** — КОГДА cron очистки сработал, СИСТЕМА ДОЛЖНА удалить записи inbox старше ретенции
  (`cutoff = now - vk.inbox.retention`) и залогировать счётчик удалённых записей. Test:
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/RunInboxCleanupUsecaseTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/RunInboxCleanupUsecaseTest.kt),
  [`src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/schedule/InboxCleanupRunnerTest.kt`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/schedule/InboxCleanupRunnerTest.kt).
- **AC-vk-long-poll-10** — КОГДА `vk.inbox.cleanup-cron` равен `off`, СИСТЕМА ДОЛЖНА не регистрировать задачу
  планировщика `inbox-cleanup`, а при валидном cron — регистрировать её. Test:
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/InboxCleanupScheduleTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/InboxCleanupScheduleTest.kt),
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/InboxCleanupScheduleEnabledTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/InboxCleanupScheduleEnabledTest.kt).

## Пробелы приёмки

- `MessageMapper` не покрыт ни одним тестом: правила fwd/reply/wall держатся только на коде
  ([`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt)).
- `failed: 1` означает потерю части истории VK: опрос продолжается с нового `ts`, WARN логируется, но AC на
  потерянные события нет и восстановить их нельзя.
- Fencing не вводится: `a_check` может занимать до 60 с (read-timeout), а TTL лиза — 40 с, поэтому живой
  лидер изредка переживает свой лиз и коротко опрашивается параллельно; дубли гасит inbox, регресс `ts`
  принят решением.
- Дедупликации в `messages` эта возможность не даёт: повторно опубликованный `MESSAGE_NEW` несёт тот же
  `inboxMessageId`, а insert-first/дедуп хранения — владение `message-storage`.
