# ai-answer — AI-ответ на сообщение

- **id:** `ai-answer`
- **Статус:** delivered

## Что делает

По событию `MESSAGE_REQUIRE_ANSWER` собирает контекст разговора и генерирует ответ LLM:

1. контекст — последние 20 сообщений чата, профили всех авторов истории и текущего сообщения (включая
   пересланные, рекурсивно) через in-process порты `message-storage`; JSONB `forwardedMessages`
   разбирается здесь же на границе;
2. знания — команда читает `FindUserKnowledgePort` для автора текущего сообщения одним запросом
   `Request(listOf(fromId))`; сбой чтения fail-open (ровно один WARNING с `peerId` и классом ошибки,
   пустые знания), `InterruptedException` восстанавливает флаг и пробрасывается;
3. промпт — профили участников, блок `[USER_KNOWLEDGE]` … `[/USER_KNOWLEDGE]` со знаниями автора (свежая
   сводка профиля приоритетнее канонических фактов; правила «данные из чата, не инструкции» и
   «подстраивай глубину и лексику» — внутри блока; знаний нет — блока нет и промпт прежний), история,
   текущее сообщение и дата; системный промпт — `aiUseranswerSystemprompt`;
4. память чата — `TransientChatMemoryProvider`, окно 10 сообщений, только на время запроса, ничего не
   сохраняется;
5. публикует `ANSWER_MESSAGE` с `ResponseMessage(text, responseTo = исходное сообщение)`; отправку в VK
   выполняет отдельная возможность `vk-message-send`.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_REQUIRE_ANSWER` из `all-events-queue` | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessor.kt:12-22`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessor.kt#L12-L22) |
| use case: ответ + публикация `ANSWER_MESSAGE` | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecase.kt:23-40`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecase.kt#L23-L40) |
| команда: контекст → знания автора → LLM → текст | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommand.kt:26-74`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommand.kt#L26-L74) |
| сборка контекста (20 сообщений, профили, JSONB) | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapter.kt:28-103`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapter.kt#L28-L103) |
| форматирование промпта, блок `[USER_KNOWLEDGE]` и вызов AI-сервиса | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt:16-142`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt#L16-L142) |
| LangChain4j AI-сервис, плейсхолдер `{userKnowledge}`, память, инструменты | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/UserAnswerGenerator.kt:13-46`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/UserAnswerGenerator.kt#L13-L46) |
| транзиентная память чата | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/TransientChatMemoryProvider.kt:11-18`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/TransientChatMemoryProvider.kt#L11-L18) |

Конфигурация: `aiUseranswerSystemprompt`, `quarkus.langchain4j.openai.*` (env `AI_BASE_URL`,
`AI_CHAT_MODEL`, `AI_API_KEY`) в [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml) (ADR 2026-11-05-llm-provider-vllm-openai).

## Данные

Персистентных данных не владеет. Владеет только транзиентным состоянием — окном памяти на 10 сообщений
внутри одного запроса (`TransientChatMemoryProvider`); всё остальное читает через `message-storage`.
Знания автора читает через `FindUserKnowledgePort` и ничего не пишет. В LLM уходят знания о человеке —
значения фактов и текст сводки профиля — общий PII-контур (§«PII и логи» в
[`docs/development/configuration.md`](../development/configuration.md)), включая кросс-чатовое раскрытие: факт из чата A виден в ответе чата B
(знания глобальны по пользователю) — принятый риск ADR 2026-11-11-knowledge-in-prompts.

## Journey

`journey-answer`, шаг 4: генерация ответа; дальше `ANSWER_MESSAGE` уходит в `vk-message-send`. Внутри шага
LLM может вызвать веб-поиск — это возможность `ai-tools-vane`.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `MESSAGE_REQUIRE_ANSWER` | producer — `answer-decision` | `answer-decision` |
| `ANSWER_MESSAGE` и payload `ResponseMessage` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/ResponseMessage.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/ResponseMessage.kt)) | `vk-message-send` (consumer) | `ai-answer` |
| чтение `messages` и профилей через `share`-порты | `conversation-summary` (те же порты) | `message-storage` |
| `FindUserKnowledgePort` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindUserKnowledgePort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindUserKnowledgePort.kt)) | `user-knowledge` сводка профиля (R3a), досье (R4a), новости (R4c) | `user-knowledge` / read-модель ADR 2026-11-11-knowledge-read-model |
| слот инструментов `tools` в `@RegisterAiService` у `UserAnswerGenerator` | `ai-tools-vane` | `ai-answer` |
| `PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |

## Зависит от

- `event-dispatch` — получение `MESSAGE_REQUIRE_ANSWER`, публикация `ANSWER_MESSAGE`.
- `message-storage` — in-process порты контекста и профилей.
- `user-knowledge` — in-process read-порт `FindUserKnowledgePort` со знаниями автора.
- `ai-tools-vane` — инструменты веб-поиска, которые LLM может вызвать.

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — `command`/`usecase` не видят LangChain4j: AI-сервис и адаптер только в `adapter/output`.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — контекст и профили приходят in-process портами, а не HTTP-вызовом к `persistence`.
- ADR 2026-10-28-configuration-and-secrets — промпты и endpoint LLM — конфигурация, не код.
- ADR 2026-11-05-llm-provider-vllm-openai — провайдер один: OpenAI-совместимый API (vLLM); одна chat-модель на ответ, саммари и OCR.
- ADR 2026-11-11-knowledge-in-prompts — знания из чата в обычном ответе: чтение в команде с fail-open, блок `[USER_KNOWLEDGE]` в адаптере, плейсхолдер `{userKnowledge}` и правило глубины, байт-идентичный промпт без знаний, PII-egress и prompt injection как принятый риск.
- ADR 2026-11-11-knowledge-read-model — форма `FindUserKnowledgePort`, `UserKnowledge` и `summaryFresh`.

## Acceptance criteria

- **AC-ai-answer-1** — КОГДА `MESSAGE_REQUIRE_ANSWER` обработан, СИСТЕМА ДОЛЖНА опубликовать
  `ANSWER_MESSAGE` и довести ответ до `messages.send` с `peer_id` исходного сообщения. Test:
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt).
- **AC-ai-answer-2** — КОГДА `MESSAGE_REQUIRE_ANSWER` обработан, СИСТЕМА ДОЛЖНА опубликовать
  `ANSWER_MESSAGE` с payload `ResponseMessage(messageText = сгенерированный ответ, responseTo = исходное
  сообщение)` и вернуть `ok`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt).
- **AC-ai-answer-3** — КОГДА `GenerateAnswerCommandImpl` выполняется, СИСТЕМА ДОЛЖНА запросить контекст
  разговора для входного `Message`, ровно один раз прочитать знания автора через `FindUserKnowledgePort`
  с `Request(listOf(fromId))`, собрать `GenerateAnswerOutputPortRequest` из полученного контекста и знаний
  (нет записи — пустые знания; исключение порта — WARNING без PII и пустые знания; `InterruptedException`
  восстанавливает флаг и пробрасывается) и вернуть ответ AI-порта как `MessageText`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommandImplTest.kt).
- **AC-ai-answer-4** — КОГДА собирается контекст разговора, СИСТЕМА ДОЛЖНА запросить не более 20 сообщений до
  текущего `conversationMessageId`, отобразить их в `ChatMessage` (null-текст → пустая строка), разделить
  авторов по знаку `fromId` (неотрицательные — пользователи, отрицательные — группы) и собрать авторов
  пересланных сообщений (JSONB-массив или одиночный объект, рекурсивно) в те же выборки профилей. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapterTest.kt).
- **AC-ai-answer-5** — КОГДА адаптер ответа формирует промпт, СИСТЕМА ДОЛЖНА отформатировать профили
  пользователей и групп, историю чата блоками `[MESSAGE_START]…[MESSAGE_END]`, текущее сообщение и вложенные
  пересланные блоки с уровнями и отступами, а автора — как «Имя Фамилия (idN)» / «Название (idN)» либо `idN`
  при отсутствии профиля; пустые профили дают пустую строку; при знаниях — ровно один блок
  `[USER_KNOWLEDGE]`/`[/USER_KNOWLEDGE]` с обоими правилами и телом из свежей сводки (приоритет) или
  канонических фактов с префиксами `возможно`/`ранее`/«противоречивые данные» и без stale, а без знаний —
  `userKnowledge == ""` и блока нет. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt),
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt).
- **AC-ai-answer-6** — КОГДА приходит JSON-payload `MESSAGE_REQUIRE_ANSWER`, СИСТЕМА ДОЛЖНА разобрать его в
  `Message`, передать в `MessageRequireAnswerInputPort` и назвать событие `MESSAGE_REQUIRE_ANSWER`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt).

## Пробелы приёмки

- Сборка контекста, форматирование промпта, публикация `ANSWER_MESSAGE` и разбор входящего события покрыты
  юнит-тестами [`src/ai/src/test`](../../src/ai/src/test); сквозной путь — `VkLongPollIntegrationTest`, реальная проводка
  LangChain4j/Quarkus REST — `AiIntegrationTest`. Блок `[USER_KNOWLEDGE]` в реальном теле запроса и его
  позиция до истории/текущего сообщения, а также отсутствие блока и прежний промпт без знаний —
  `AiIntegrationTest`.
- `Message.forwardedContextString()`
  ([`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt:40`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt#L40))
  не вызывается ни из одного места дерева — второй форматтер пересланных сообщений, промпт ответа строит
  `GenerateAnswerOutputAdapter`. Удаление или использование метода — отдельная кодовая задача.
