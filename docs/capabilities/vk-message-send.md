# vk-message-send — отправка сообщения в VK API

- **id:** `vk-message-send`
- **Статус:** delivered

## Что делает

Доставляет готовый текст в чат VK: `peer_id`, текст, опциональный forward-блок и `random_id`. Forward
формируется детерминированно: если он не задан явно и чат не личный — это reply на исходное сообщение;
`random_id` — SHA-256 от текста, peer_id и даты исходного сообщения, поэтому повторная отправка того же
ответа даёт тот же id.

Отправку используют обе продуктовые ветки: ответ на сообщение (`ai-answer`) и доставку саммари
(`conversation-summary`), поэтому это отдельная возможность, а не часть генерации ответа.

Ошибка VK (`response.error != null`) превращается в `VkException`; событие не подтверждается, и
`event-dispatch` отправляет его по retry-лестнице.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `ANSWER_MESSAGE` из `all-events-queue` | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SendMessageEventProcessor.kt:12-22` |
| use case: peer_id, forward, random_id | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/usecase/message/MessageSendUsecase.kt:15-44` |
| входной контракт для обоих потребителей | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/port/input/vk/VkSendMessageInputPort.kt:9-16` |
| команда отправки | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/command/sendVkMessage/SendVkMessageCommandImpl.kt:8-20` |
| выходной порт и HTTP-адаптер к VK | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/port/output/vk/VkSendMessageOutputPort.kt:10-17`, `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkSendMessageOutputAdapter.kt:18-29` |
| REST-клиент VK: `POST /messages.send` | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkClient.kt:18-25` |
| формат forward-блока | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/domain/ForwardedMessages.kt:5-15` |
| ошибка VK | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/exception/VkException.kt:6-8` |

Конфигурация: `vk.api.token`, `vk.api.version`, `quarkus.rest-client.vk.*` в
`src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Владеет двумя контрактами: формой forward-блока `ForwardedMessages` и
правилом детерминированного `random_id`.

## Journey

- `journey-answer`, шаг 5: отправка ответа на сообщение.
- `journey-summary`, последний шаг: отправка итога обсуждения с forward-блоком на диапазон сообщений.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `VkSendMessageInputPort` | `ai-answer` (через `ANSWER_MESSAGE`), `conversation-summary` (саммари с явным forward) | `vk-message-send` |
| `ResponseMessage` | producer — `ai-answer` | `ai-answer` |
| `VkClient.sendMessage` и токен VK | `vk-profiles` использует тот же клиент для `users.get`/`groups.getById` | `vk-message-send` — метод `messages.send`; общая часть клиента (`configKey = "vk"`, версия, токен) — конфигурация деплоя (ADR 0006) |

## Зависит от

- `event-dispatch` — получение `ANSWER_MESSAGE` и retry при ошибке VK.

## ADR

- ADR 0003 — HTTP-клиент VK — только `adapter/output`; use case и команда не видят JAX-RS.
- ADR 0004 — `ANSWER_MESSAGE` идёт событием, а не синхронным вызовом из `ai`.
- ADR 0006 — токен и версия API в конфигурации, заголовок `Authorization: Bearer` — в конфиге клиента.

## Acceptance criteria

- **AC-vk-message-send-1** — КОГДА use case получает `ResponseMessage`, СИСТЕМА ДОЛЖНА вызвать
  `SendVkMessageCommand` с `peer_id` и текстом ответа. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/usecase/message/MessageSendUsecaseTest.kt:12-29`.
- **AC-vk-message-send-2** — КОГДА отправка успешна, СИСТЕМА ДОЛЖНА передать в `VkClient.sendMessage`
  peer_id, текст и `rand`. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkSendMessageOutputAdapterTest.kt:15-28`,
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/command/sendVkMessage/SendVkMessageCommandImplTest.kt:12-25`.
- **AC-vk-message-send-3** — КОГДА VK API вернул ошибку, СИСТЕМА ДОЛЖНА бросить `VkException`, чтобы
  событие ушло в retry. Test:
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkSendMessageOutputAdapterTest.kt:30-44`.

## Пробелы приёмки

- Детерминированный `random_id` (SHA-256) не проверен: `MessageSendUsecaseTest` не смотрит на `rand`.
- Forward для группового чата (`forwardMessagesIfNeeded`) не покрыт тестом.
- Ошибка VK на уровне сквозного сценария: интеграционный тест `VK API failure should handle error
  gracefully` проверяет только ответ вебхука `200`, но не факт retry/DLQ
  (`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:270-307`).
