# 2026-10-28: In-process порты вместо loopback-REST, события — RabbitMQ

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

До прохода внутри одного деплоя работали два синхронных HTTP-контура на `http://localhost:8080`:
`ai -> persistence` (`configKey = "persistence"`) и `persistence -> vk-facade` (`configKey = "vk-facade"`).
Контракт был продублирован дословно: интерфейс `PersistenceResource` с девятью методами существовал на
сервере и клиентской копией, DTO запросов — в двух файлах. Изменение одной стороны не ломало сборку другой,
а каждый вызов платил за JSON-раундтрип, URL и таймауты.

В `share` при этом уже лежали односторонние порты: 9 портов
`share/port/output/persistence/*` и 2 порта `share/port/output/vk/*` (все — `OutputPort<REQ,RESP>`).

## Решение

Синхронные вызовы между модулями одного деплоя идут через порты `share`, реализованные модулем-поставщиком
in-process:

| Порт (`share`) | Реализация | Делегирует в |
|---|---|---|
| [`FindStoredMessagesBeforePort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredMessagesBeforePort.kt), [`FindStoredMessagesBetweenPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredMessagesBetweenPort.kt), [`FindLastStoredSummaryPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindLastStoredSummaryPort.kt), [`HasStoredPendingSummaryPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/HasStoredPendingSummaryPort.kt), [`CreateStoredPendingSummaryPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/CreateStoredPendingSummaryPort.kt), [`SaveStoredCompletedSummaryPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/SaveStoredCompletedSummaryPort.kt), [`MarkStoredSummaryFailedPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/MarkStoredSummaryFailedPort.kt), [`FindStoredUserProfilesByIdsPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredUserProfilesByIdsPort.kt), [`FindStoredGroupProfilesByIdsPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredGroupProfilesByIdsPort.kt) | [`persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt:24-149`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt) (9 классов) | `PersistenceDataInputPort` — тот же use case, что у REST-ресурса |
| [`GetUserProfilesBatchPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetUserProfilesBatchPort.kt), [`GetGroupProfilesBatchPort`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetGroupProfilesBatchPort.kt) | [`vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt) (2 класса) | `VkProfileInputPort` |

Потребители держат только `share`-порты:
[`ai/adapter/output/context/ConversationContextRestAdapter.kt:22-26`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapter.kt),
[`ai/command/summary/GenerateSummaryCommandImpl.kt:7-12`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImpl.kt),
[`persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt:9-10,20-21`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt).

Удалены: клиентские копии REST-контрактов (на тот момент `PersistenceService`/`VkFacadeService`),
REST-адаптеры
`PersistenceDataRestAdapter`/`SummaryPersistenceRestAdapter`/`VkFacadeRestAdapter`, девять внутренних
портов `ai/port/output/persistence/*` и `quarkus.rest-client.persistence`/`vk-facade` из конфига.

REST-эндпоинты остаются публичным API модулей и единственной копией контракта:
[`persistence/adapter/input/rest/PersistenceResource.kt:18-79`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceResource.kt) + [`PersistenceDataResource.kt:21-126`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceDataResource.kt);
[`vkFacade/adapter/input/rest/VkProfileResource.kt:14-32`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/rest/VkProfileResource.kt). Контроллер и in-process адаптер — два входных
адаптера к одному `port.input` (Information Expert: логика в use case, адаптеры только маппят).

Асинхронный поток остаётся на RabbitMQ: приём событий VK не должен ждать AI. Публикация —
[`infrastructure/adapter/output/mq/PublishEventOutputAdapter.kt:15-33`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/adapter/output/mq/PublishEventOutputAdapter.kt) (Jackson-строка + routing key = имя
события), потребление — [`infrastructure/mq/adapter/input/EventDispatcher.kt:63-85`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt) с retry-лестницей
(`:91-113`). Payload'ы событий — тоже контракт и переехали в `share`:
[`share/domain/event/SummaryRequestedPayload.kt:3-6`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryRequestedPayload.kt), [`share/domain/event/SummaryReadyPayload.kt:3-8`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryReadyPayload.kt).

Версионирования схемы событий нет и не требуется: производитель и потребитель в одном деплое, меняются одним
коммитом. Допустимы только аддитивные изменения; несовместимое изменение = новое имя события в `Event`
([`share/domain/Event.kt:3-10`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/Event.kt) — закрытый enum из шести значений).

## Последствия

- Один контракт на вызов: изменение типа в `share` ломает компиляцию обеих сторон.
- Проводка валидируется сборкой (ArC: injection point без реализации или с двумя реализациями валит
  `./gradlew build`) и полным интеграционным тестом
  ([`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt)).
- Публичный REST остаётся; его удаление — отдельная задача при подтверждённом отсутствии внешних потребителей.
- `CircuitBreaker` для VK API **не применён**: расширение `quarkus-smallrye-fault-tolerance` объявлено
  ([`src/vk-facade/build.gradle.kts:21`](../../src/vk-facade/build.gradle.kts)), пакет называется `adapter/output/vk/circuitbreaker`, но ни одной
  аннотации `@CircuitBreaker`/`@Retry` в коде нет, а блок `circuit-breaker.vk-profile` в
  [`src/app/src/main/resources/application.yml:193-198`](../../src/app/src/main/resources/application.yml) никем не читается. Это заявленный долг, а не
  работающий механизм.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Оставить loopback-REST, зафиксировать как долг | Две копии контракта, URL, таймауты, сериализация — навсегда; изменение одной стороны не ломает сборку |
| Генерировать клиент из OpenAPI | Источник истины уже есть — `share`; генератор добавил бы шаг сборки и второй маппинг |
| Прямое ребро `ai -> persistence` | Модульная граница (ADR 2026-10-28-module-boundaries-and-layer-rules) и тест allowlist запрещают; вызывать чужой use case напрямую — Information Expert нарушен |
| Версионировать схему событий | Один деплой; версия дала бы двойную поддержку форматов без внешних потребителей |
