# vk-webhook — приём событий VK Callback

- **id:** `vk-webhook`
- **Статус:** delivered

## Что делает

Принимает `POST /vk/callback` от сообщества VK: проверяет секрет до бизнес-логики, отвечает на
`confirmation` кодом подтверждения, распознаёт тип события (`message_new`, `message_reply`, `confirmation`,
иначе `UNKNOWN`), превращает VK JSON в доменное `Message` на границе DMZ и публикует `MESSAGE_NEW` через
RabbitMQ. Всегда отвечает VK быстро и не ждёт обработки: ответ — `text/plain` `ok`, кроме confirmation и
ошибки секрета.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `POST /vk/callback` | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/VkMediatorRouterImpl.kt:16-40` |
| проверка секрета (CDI-декоратор до логики) | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/VkMediatorRouterSecurityDecorator.kt:11-29` |
| маршрутизация типов события | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt:17-32` |
| DMZ-маппинг VK JSON → `Message` (fwd, reply, wall) | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt:13-58` |
| публикация `MESSAGE_NEW` | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/command/sendVkEvent/PublishVkEventCommandImpl.kt:15-23` |
| единый маппинг ошибок в HTTP-статус | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/ExceptionHandler.kt:10-39` |
| входной контракт use case | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/ReceiveMessageInputPort.kt:10-15` |
| confirmation-код из конфигурации | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/config/ConstantConfiguration.kt:12-14` |
| тип события | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/domain/vo/VkCallbackEvent.kt:10-14` |

Конфигурация: `vk.secret`, `vk.confirmation-code` в `src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Создаёт доменное `Message`, но не хранит его: единственный побочный
эффект — событие в RabbitMQ. Секрет и confirmation-код — конфигурация деплоя, не данные продукта.

## Journey

`journey-answer`, шаг 1: событие VK становится `MESSAGE_NEW` и уходит в цепочку сохранения.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| модель `Message` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt`) | `message-storage`, `answer-decision`, `ai-answer` | `vk-webhook` — единственный producer: здесь VK JSON становится доменом |
| событие `MESSAGE_NEW` и его payload `Message` | `message-storage` (consumer) | `vk-webhook` |
| `PublishEventOutputPort` (`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/PublishEventOutputPort.kt`) | `message-storage`, `answer-decision`, `ai-answer`, `conversation-summary` | `event-dispatch` |
| иерархия `VkBotAppException`/`ExceptionStatus` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/exception/VkBotAppException.kt`, `.../ExceptionStatus.kt`) | `vk-message-send` (`VkException` → `EXTERNAL_FAILURE`) | `vk-webhook` — единственный HTTP-маппинг ошибок в деплое |

## Зависит от

- `event-dispatch` — публикация `MESSAGE_NEW` идёт через `PublishEventOutputPort`, реализованный
  `PublishEventOutputAdapter`.

## ADR

- ADR 0003 — `domain`/`adapter` границы: `jakarta.json.JsonObject` не покидает входной адаптер, ошибки
  маппятся в адаптере.
- ADR 0004 — межмодульное взаимодействие событием RabbitMQ, а не HTTP-вызовом.
- ADR 0006 — секрет и confirmation-код читаются из одного production-конфига.

## Acceptance criteria

- **AC-vk-webhook-1** — КОГДА VK присылает событие `confirmation` с верным секретом, СИСТЕМА ДОЛЖНА ответить
  кодом `vk.confirmation-code`. Test: `src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:179-200`.
- **AC-vk-webhook-2** — КОГДА секрет неверен или отсутствует, СИСТЕМА ДОЛЖНА ответить `403` с JSON-ошибкой и
  не передавать событие в бизнес-логику. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:225-268`.
- **AC-vk-webhook-3** — КОГДА тип события неизвестен, СИСТЕМА ДОЛЖНА ответить `ok` и не публиковать событие.
  Test: `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecaseTest.kt:31-47`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:202-223`.
- **AC-vk-webhook-4** — КОГДА приходит `message_new` с верным секретом и сообщением, СИСТЕМА ДОЛЖНА
  смаппить его в доменное `Message` и опубликовать `MESSAGE_NEW` ровно один раз. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/VkMediatorRouterIntegrationTest.kt:38-72`,
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecaseTest.kt:49-67`.
- **AC-vk-webhook-5** — КОГДА к адаптеру доходит исключение валидации или необработанное исключение,
  СИСТЕМА ДОЛЖНА отдать JSON-ошибку со статусом 400 или 503. Test:
  `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/ExceptionHandlerTest.kt:10-47`.

## Пробелы приёмки

- `MessageMapper` не покрыт ни одним тестом: правила fwd/reply/wall и `message_reply` держатся только на коде
  (`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt`).
- Value objects бросают `share.ValidationException` (обычный `RuntimeException`,
  `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/exception/ValidationException.kt:3`), а
  `ExceptionHandler` знает только `receiver.ValidationException`; такой случай уходит в
  `handleUnknownException` → 503 вместо 400. Это текущее поведение, зафиксированное в `AGENTS.md` §9;
  AC на 400 для `share.ValidationException` нет, изменение — кодовая задача.
