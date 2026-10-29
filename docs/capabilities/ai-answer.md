# ai-answer — AI-ответ на сообщение

- **id:** `ai-answer`
- **Статус:** delivered

## Что делает

По событию `MESSAGE_REQUIRE_ANSWER` собирает контекст разговора и генерирует ответ LLM:

1. контекст — последние 20 сообщений чата, профили всех авторов истории и текущего сообщения (включая
   пересланные, рекурсивно) через in-process порты `message-storage`; JSONB `forwardedMessages`
   разбирается здесь же на границе;
2. промпт — профили участников, история, текущее сообщение и дата; системный промпт —
   `aiUseranswerSystemprompt`;
3. память чата — `TransientChatMemoryProvider`, окно 10 сообщений, только на время запроса, ничего не
   сохраняется;
4. публикует `ANSWER_MESSAGE` с `ResponseMessage(text, responseTo = исходное сообщение)`; отправку в VK
   выполняет отдельная возможность `vk-message-send`.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_REQUIRE_ANSWER` из `all-events-queue` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessor.kt:12-22` |
| use case: ответ + публикация `ANSWER_MESSAGE` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecase.kt:23-40` |
| команда: контекст → LLM → текст | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommand.kt:32-47` |
| сборка контекста (20 сообщений, профили, JSONB) | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapter.kt:28-103` |
| форматирование промпта и вызов AI-сервиса | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt:19-119` |
| LangChain4j AI-сервис, память, инструменты | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/UserAnswerAiService.kt:13-44` |
| транзиентная память чата | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/TransientChatMemoryProvider.kt:11-18` |

Конфигурация: `aiUseranswerSystemprompt`, `quarkus.langchain4j.*` в `src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Владеет только транзиентным состоянием — окном памяти на 10 сообщений
внутри одного запроса (`TransientChatMemoryProvider`); всё остальное читает через `message-storage`.

## Journey

`journey-answer`, шаг 4: генерация ответа; дальше `ANSWER_MESSAGE` уходит в `vk-message-send`. Внутри шага
LLM может вызвать веб-поиск — это возможность `ai-tools-vane`.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `MESSAGE_REQUIRE_ANSWER` | producer — `answer-decision` | `answer-decision` |
| `ANSWER_MESSAGE` и payload `ResponseMessage` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/ResponseMessage.kt`) | `vk-message-send` (consumer) | `ai-answer` |
| чтение `messages` и профилей через `share`-порты | `conversation-summary` (те же порты) | `message-storage` |
| слот инструментов `UserAnswerAiService.tools` | `ai-tools-vane` | `ai-answer` |
| `PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |

## Зависит от

- `event-dispatch` — получение `MESSAGE_REQUIRE_ANSWER`, публикация `ANSWER_MESSAGE`.
- `message-storage` — in-process порты контекста и профилей.
- `ai-tools-vane` — инструменты веб-поиска, которые LLM может вызвать.

## ADR

- ADR 0003 — `command`/`usecase` не видят LangChain4j: AI-сервис и адаптер только в `adapter/output`.
- ADR 0004 — контекст и профили приходят in-process портами, а не HTTP-вызовом к `persistence`.
- ADR 0006 — промпты и endpoint LLM — конфигурация, не код.

## Acceptance criteria

- **AC-ai-answer-1** — КОГДА `MESSAGE_REQUIRE_ANSWER` обработан, СИСТЕМА ДОЛЖНА опубликовать
  `ANSWER_MESSAGE` и довести ответ до `messages.send` с `peer_id` исходного сообщения. Test:
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:42-177`.

## Пробелы приёмки

- В модуле `ai` нет ни одного теста: сборка контекста (20 сообщений, профили, JSONB), форматирование
  промпта, публикация `ANSWER_MESSAGE` и текст отправленного ответа проверяются только сквозным
  интеграционным тестом с подменённой LLM.
- `Message.forwardedContextString()` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt:40`)
  не вызывается ни из одного места дерева — второй форматтер пересланных сообщений, промпт ответа строит
  `GenerateAnswerOutputAdapter`.
