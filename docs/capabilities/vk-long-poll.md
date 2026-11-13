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
подкод и сообщение VK, запрос повторяется с той же нарастающей паузой. Приложение остаётся одним
экземпляром: два экземпляра с одним токеном и одной позицией не поддерживаются.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| цикл опроса: виртуальный поток, старт по `StartupEvent`, стоп по `ShutdownEvent`, `vk.longpoll.enabled` | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunner.kt` |
| `groups.getLongPollServer` и `a_check`, read-timeout 60 с | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapter.kt` |
| порты Long Poll: `GetLongPollServerPort` (сессия) и `CheckLongPollPort` (`a_check`) | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/output/LongPollPort.kt` |
| тип обновления (`MESSAGE_NEW`, `MESSAGE_REPLY`, `UNKNOWN`) | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/domain/vo/VkUpdateType.kt` |
| входной контракт use case `VkUpdateInputPortRequest(type, message)` | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/ReceiveMessageInputPort.kt` |
| маршрутизация обновления и публикация `MESSAGE_NEW` | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt`, `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/command/sendVkEvent/PublishVkEventCommandImpl.kt:15-23` |
| DMZ-маппинг VK JSON → `Message` (fwd, reply, wall) | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt:13-58` |
| позиция `ts`: таблица одной строки `long_poll_position` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/LongPollPositionTable.kt` |
| порты позиции `ts` | `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindLongPollTsPort.kt`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/SaveLongPollTsPort.kt` |
| VO позиции | `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/LongPollTs.kt` |

Конфигурация: `vk.group.id` и `vk.longpoll.enabled` в `src/app/src/main/resources/application.yml`
(`VK_GROUP_ID`, `VK_LONGPOLL_ENABLED`).

## Данные

Владеет позицией опроса: одна строка `long_poll_position` (`ts`, changeset
`005-create-long-poll-position-table`; схема — владение `persistence`). Сообщения не хранит: создаёт
доменное `Message`, но единственный побочный эффект — событие `MESSAGE_NEW` в RabbitMQ и сохранённый `ts`.
`server` и `key` сессии не хранятся — на старте сессия запрашивается заново.

## Journey

`journey-answer`, шаг 1: событие VK становится `MESSAGE_NEW` и уходит в цепочку сохранения.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| модель `Message` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt`) | `message-storage`, `answer-decision`, `ai-answer` | `vk-long-poll` — единственный producer: здесь VK JSON становится доменом |
| событие `MESSAGE_NEW` и его payload `Message` | `message-storage` (consumer) | `vk-long-poll` |
| `PublishEventOutputPort` (`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/PublishEventOutputPort.kt`) | `message-storage`, `answer-decision`, `ai-answer`, `conversation-summary` | `event-dispatch` |
| контракт позиции `LongPollTs` и порты `FindLongPollTsPort`/`SaveLongPollTsPort` | `persistence` (реализация через `PersistenceDataInProcessAdapter`) | `vk-long-poll` — единственный потребитель позиции |

## Зависит от

- `event-dispatch` — публикация `MESSAGE_NEW` идёт через `PublishEventOutputPort`, реализованный
  `PublishEventOutputAdapter`.
- `message-storage` — хранение `ts` через share-порты `FindLongPollTsPort`/`SaveLongPollTsPort`; HTTP-вызова
  нет, реализация in-process.

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — разбор ответа VK (`jakarta.json`) живёт во входном адаптере, порты опроса остаются на плоских
  типах; `server`/`key`/`ts` не протекают в домен.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — межмодульное взаимодействие событием RabbitMQ (`MESSAGE_NEW`) и in-process портами (позиция
  `ts`), без HTTP-вызова.
- ADR 2026-10-28-configuration-and-secrets — `vk.group.id` и `vk.longpoll.enabled` читаются из одного production-конфига, секретов в дереве
  нет.
- ADR 2026-11-05-bots-long-poll-transport — транспорт Bots Long Poll, `ts` в PostgreSQL, дубликат вместо потери, один экземпляр.

## Acceptance criteria

- **AC-vk-long-poll-1** — КОГДА `a_check` вернул обновление с сообщением, СИСТЕМА ДОЛЖНА опубликовать
  `MESSAGE_NEW`, а `ts` сохранить только после успешной публикации. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`.
- **AC-vk-long-poll-2** — КОГДА в PostgreSQL есть сохранённый `ts`, СИСТЕМА ДОЛЖНА продолжить опрос с него,
  а не с `ts` новой сессии, и сохранить его для следующего запроса. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/LongPollPositionIntegrationTest.kt` (реальная БД:
  засеянный `ts` уходит в первый `a_check`, ответный `ts` ложится в таблицу).
- **AC-vk-long-poll-3** — КОГДА VK вернул `failed: 1`, `failed: 2` или `failed: 3`, СИСТЕМА ДОЛЖНА
  продолжить опрос по коду (`ts` из ответа / новая сессия с тем же `ts` / новая сессия и новый `ts`) и не
  остановиться. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`.
- **AC-vk-long-poll-4** — КОГДА публикация `MESSAGE_NEW` бросила исключение, СИСТЕМА ДОЛЖНА не сохранять
  `ts`, подождать 5 с и повторить то же обновление. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunnerTest.kt`.
- **AC-vk-long-poll-5** — КОГДА тестовый профиль, `POST /vk/callback` ДОЛЖЕН вернуть 404, а обновление из
  Long Poll — дойти до `messages.send` с `peer_id` исходного сообщения. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt`.
- **AC-vk-long-poll-6** — КОГДА `groups.getLongPollServer` вернул `error` вместо `response`, СИСТЕМА ДОЛЖНА
  бросить `VkLongPollServerException` (`VkBotAppException`, статус `EXTERNAL_FAILURE`) с кодом, подкодом
  и сообщением VK, чтобы отказ доступа попадал в лог, а не терялся в общей ошибке. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapterTest.kt`.

## Пробелы приёмки

- `MessageMapper` не покрыт ни одним тестом: правила fwd/reply/wall держатся только на коде
  (`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt`).
- `failed: 1` означает потерю части истории VK: опрос продолжается с нового `ts`, WARN логируется, но AC на
  потерянные события нет и восстановить их нельзя.
- Ограничение «один экземпляр» — требование деплоя, тестом не проверяется; два экземпляра с одним токеном и
  одной позицией `ts` не поддерживаются.
