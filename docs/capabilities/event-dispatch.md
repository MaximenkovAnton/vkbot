# event-dispatch — доставка событий через RabbitMQ

- **id:** `event-dispatch`
- **Статус:** delivered

## Что делает

Транспорт всех шагов продукта: публикует события в exchange `all-events` (routing key = имя события),
принимает их из очереди `queue` и вызывает единственный `EventProcessor`, чей `event()` совпал с routing key.
Неизвестный routing key логируется как ошибка и подтверждается. Исключение обработки не теряет сообщение:
оно уходит по retry-лестнице `retry-5s` → `retry-30s` → `retry-1m` → `retry-15m` → `retry-1h` → `dlq-final`
(без TTL, ручной разбор), счётчик — заголовок `x-retry-count`.

Событий ровно шесть — закрытый `enum class Event`: `MESSAGE_NEW`, `MESSAGE_RECEIVED`,
`MESSAGE_REQUIRE_ANSWER`, `ANSWER_MESSAGE`, `SUMMARY_REQUESTED`, `SUMMARY_READY`.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `@Incoming("all-events-queue")`, маршрутизация по routing key | `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt:63-88` |
| retry-лестница и финальный DLQ | `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt:90-111` |
| конфигурация уровней retry из `dlq.levels[]` | `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/config/DlqConfig.kt:6-16` |
| публикация: JSON payload + routing key = имя события | `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/adapter/output/mq/PublishEventOutputAdapter.kt:15-33` |
| реализация `PublishEventCommand` для всех модулей | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/command/publishEvent/PublishEventCommandImpl.kt:11-21` |
| контракты: событие, процессор, порт публикации, команда | `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/Event.kt:3-10`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/input/EventProcessor.kt:5-8`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/PublishEventOutputPort.kt`, `src/share/src/main/kotlin/com/simarel/vkbot/share/command/publishEvent/PublishEventCommand.kt:9-13` |

Конфигурация: `dlq.levels[]` в `src/app/src/main/resources/application-queues.yml`, `mp.messaging.*` в
`src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Владеет топологией брокера: exchange `all-events`, очередь `queue`,
пять retry-exchange/queue с TTL и `dlq-final-exchange`, а также каталогом имён событий (routing key).

## Journey

Транспорт обоих путей: `journey-answer` и `journey-summary` проходят через него на каждом шаге. Отдельного
пользовательского шага не образует.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `Event` (каталог из шести имён и routing key) | все producer/consumer возможностей | `event-dispatch` |
| `EventProcessor` | `MessageNewEventProcessor`, `MessageReceivedEventProcessor`, `MessageRequireAnswerEventProcessor`, `SummaryRequestedEventProcessor`, `SendMessageEventProcessor`, `SummaryReadyEventProcessor` | `event-dispatch` |
| `PublishEventOutputPort` | `vk-webhook`, `message-storage`, `answer-decision`, `ai-answer`, `conversation-summary` | `event-dispatch` |
| `PublishEventCommand` и его реализация | все публикующие возможности | `event-dispatch` |
| payload каждого события | producer соответствующего события (см. `message-storage`, `ai-answer`, `vk-message-send`, `conversation-summary`) | producer события |

## Зависит от

- Ни от одной возможности; нижний слой продукта.

## ADR

- ADR 0003 — диспетчер и публикатор — адаптеры в `infrastructure`, контракты — в `share`.
- ADR 0004 — асинхронные шаги внутри деплоя идут событиями, синхронные — in-process портами.
- ADR 0006 — `dlq.levels[]` и `mp.messaging.*` — единственный источник конфигурации очередей.

## Acceptance criteria

- **AC-event-dispatch-1** — КОГДА `PublishEventCommand` получает событие, СИСТЕМА ДОЛЖНА передать имя события
  и payload в `PublishEventOutputPort` без изменения. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/command/publishEvent/PublishEventCommandImplTest.kt:11-22`.
- **AC-event-dispatch-2** — КОГДА событие попадает в очередь, СИСТЕМА ДОЛЖНА вызвать процессор, чей `event()`
  совпал с routing key: цепочка `MESSAGE_NEW → MESSAGE_RECEIVED → MESSAGE_REQUIRE_ANSWER → ANSWER_MESSAGE`
  доходит до `messages.send`. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:42-177`.

## Пробелы приёмки

- Retry-лестница, DLQ и заголовок `x-retry-count` не покрыты ни одним тестом
  (`src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt:90-111`).
- Routing key = `Event.name` на публикации не проверен: тест команды смотрит только на переданный event.
- Неизвестный routing key (лог + ack) не проверен.
- At-least-once без идемпотентности: повторная доставка `MESSAGE_NEW` создаст дубль сообщения; outbox нет —
  известное ограничение, отмеченное в `docs/architecture/data-flow.md`.
