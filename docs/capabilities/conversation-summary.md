# conversation-summary — итог обсуждения

- **id:** `conversation-summary`
- **Статус:** delivered

## Что делает

По событию `SUMMARY_REQUESTED` решает, пора ли подводить итог чата, и если да — саммаризирует переписку
через LLM, ведёт lifecycle записи саммари и присылает итог в чат.

Правила (`ProcessSummaryCommandImpl`):

| Условие | Поведение |
|---|---|
| чат не в `summary.enabled-chats` (список приходит из `SUMMARY_ENABLED_CHATS`; пустое значение даёт пустой список — саммаризация выключена) | выйти, ничего не делать |
| у чата уже есть `PENDING`-саммари | пропустить, чтобы не запускать второй прогон |
| последнее саммари зависло в `PENDING` | WARNING и пропуск |
| сообщений меньше `summary.threshold` и прошлое саммари есть | пропустить |
| иначе | создать `PENDING`, взять до `summary.batch-size` сообщений, вызвать LLM, сохранить `COMPLETED` |

Саммаризация — часть `GenerateSummaryCommandImpl`: выборка до 1000 сообщений, форматирование с именами
авторов, LLM (3 попытки), сохранение completed; при исключении запись помечается `FAILED` и исключение
пробрасывается дальше. `ProcessSummaryCommandImpl` ловит это исключение и логирует его на ERROR с `e.message`
и throwable (PII — общий контур §«PII и логи» в [`docs/development/configuration.md`](../development/configuration.md)), поэтому событие
подтверждается, запись остаётся `FAILED`, а retry брокера не запускается. Сохранение completed идёт через
in-process порт `message-storage`, который публикует `SUMMARY_READY`; потребитель события — `vk-facade`
(`SummaryReadyEventProcessor`), он формирует forward-блок на диапазон `[first, last]` и отправляет итог
через `VkSendMessageInputPort` (`vk-message-send`).

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `SUMMARY_REQUESTED` из `all-events-queue` | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessor.kt:12-28`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessor.kt#L12-L28) |
| порог, включённые чаты, pending, batch-size | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt:37-126`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt#L37-L126) |
| создание pending, выборка, форматирование, LLM | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImpl.kt:23-60`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImpl.kt#L23-L60) |
| LLM-адаптер, 3 попытки | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapter.kt:17-42`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapter.kt#L17-L42) |
| AI-сервис саммаризации и JSON-ответ | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/ConversationSummarizer.kt:8-37`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/ConversationSummarizer.kt#L8-L37) |
| сохранение completed → публикация `SUMMARY_READY` | [`src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:238-269`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt#L238-L269) |
| `SUMMARY_READY` → forward-блок → отправка в VK | [`src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt:23-57`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt#L23-L57) |

Конфигурация: `summary.enabled-chats` (`SUMMARY_ENABLED_CHATS`, ADR 2026-10-28-configuration-and-secrets),
`summary.threshold`, `summary.batch-size`, `aiSumarizationSystemprompt`
в [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml).

## Данные

Персистентных данных не владеет: записи саммари хранит `message-storage` (таблица `summaries`, миграция
[`src/persistence/src/main/resources/db/changelog/changes/004-create-summaries-table.yaml`](../../src/persistence/src/main/resources/db/changelog/changes/004-create-summaries-table.yaml)). Владеет
смыслом lifecycle `PENDING`/`COMPLETED`/`FAILED` и правилами запуска.

## Journey

`journey-summary`, центральный шаг: от `SUMMARY_REQUESTED` до `SUMMARY_READY`; доставку выполняет
`vk-message-send`.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `SUMMARY_REQUESTED` и payload `SummaryRequestedPayload` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryRequestedPayload.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryRequestedPayload.kt)) | producer — `message-storage` | `message-storage` |
| `SUMMARY_READY` и payload `SummaryReadyPayload` ([`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryReadyPayload.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryReadyPayload.kt)) | producer — `message-storage` (`PersistenceDataUsecase.handleSaveCompletedSummary`), consumer — этот шаг | `message-storage` |
| таблица `summaries` и статусы | `message-storage` | `message-storage` |
| `VkSendMessageInputPort` | producer — `vk-message-send` | `vk-message-send` |
| in-process порты чтения сообщений/профилей и записи саммари | `ai-answer` (те же порты чтения) | `message-storage` |

## Зависит от

- `event-dispatch` — получение `SUMMARY_REQUESTED`.
- `message-storage` — in-process порты чтения сообщений/профилей и lifecycle-записи саммари.
- `vk-message-send` — доставка итога в чат.

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — вся бизнес-логика саммаризации в `command`, LLM — только в `adapter/output`.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — сообщения и профили читаются in-process портами, `SUMMARY_READY` публикуется событием.
- ADR 2026-10-28-configuration-and-secrets — порог и batch-size — конфигурация с дефолтами `100`; включённые чаты — `SUMMARY_ENABLED_CHATS`,
  пустое значение оставляет саммаризацию выключенной.

## Acceptance criteria

- **AC-conversation-summary-1** — КОГДА чат не входит в `summary.enabled-chats`, или у него есть
  `PENDING`-саммари, или последнее саммари зависло в `PENDING`, СИСТЕМА ДОЛЖНА пропустить прогон, не вызывая
  `GenerateSummaryCommand`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt).
- **AC-conversation-summary-2** — КОГДА саммаризация продолжается, СИСТЕМА ДОЛЖНА взять окно сообщений
  размером `summary.batch-size` (`FindStoredMessagesBeforePort(peerId, current+1, batchSize)` без прошлого саммари;
  иначе `FindStoredMessagesBetweenPort(peerId, lastMessageId ?: 0, current, batchSize)`), пропустить прогон при
  пустом окне и при `size < summary.threshold` вместе с прошлым саммари, а иначе вызвать
  `GenerateSummaryCommand` с first/last id из окна. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt).
- **AC-conversation-summary-3** — КОГДА `GenerateSummaryCommandImpl` выполняется, СИСТЕМА ДОЛЖНА создать
  `PENDING`-запись, сохранить `COMPLETED` с short/full из LLM; при пустом диапазоне сохранить `COMPLETED` с
  текстом «Нет сообщений»; при исключении LLM пометить запись `FAILED` и пробросить исключение. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt).
- **AC-conversation-summary-4** — КОГДА сообщения форматируются для LLM, СИСТЕМА ДОЛЖНА собрать
  `[автор]: текст` через `\n\n`, где автор — «Имя Фамилия» пользователя, имя группы (id берётся как
  `-fromId`), либо fallback «Пользователь N» / «Группа N»; профили запрашиваются раздельно для пользователей
  и групп. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt).
- **AC-conversation-summary-5** — КОГДА LLM-саммаризация падает, СИСТЕМА ДОЛЖНА повторить попытку до 3 раз и
  после третьей бросить `SummaryGenerationException` (`VkBotAppException`, статус `EXTERNAL_FAILURE`) с
  последней ошибкой в `cause` (успех на 2-й попытке не пробрасывает и не зовёт LLM в третий раз). Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapterTest.kt).
- **AC-conversation-summary-6** — КОГДА приходит JSON-payload `SUMMARY_REQUESTED`, СИСТЕМА ДОЛЖНА разобрать
  `peerId` и `conversationMessageId`, передать их в `ProcessSummaryInputPort` и назвать событие
  `SUMMARY_REQUESTED`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessorTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessorTest.kt).
- **AC-conversation-summary-7** — КОГДА выполняются `ProcessSummaryUsecase` / `GenerateSummaryUsecase`,
  СИСТЕМА ДОЛЖНА передать id в соответствующую команду и вернуть её результат (`GenerateSummaryCommand` —
  `summaryId`). Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/ProcessSummaryUsecaseTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/ProcessSummaryUsecaseTest.kt),
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/GenerateSummaryUsecaseTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/GenerateSummaryUsecaseTest.kt).
- **AC-conversation-summary-8** — КОГДА приходит `SUMMARY_READY`, СИСТЕМА ДОЛЖНА отправить текст итога в чат
  `peerId` с forward-блоком `[firstConversationMessageId, lastConversationMessageId]` и `is_reply = false`;
  автор сообщения — бот (`vk.bot.id`), сообщество — `vk.group.id`, `conversationMessageId` —
  `lastConversationMessageId` из payload. Test:
  [`src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SummaryReadyEventProcessorTest.kt`](../../src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SummaryReadyEventProcessorTest.kt).

## Пробелы приёмки

- Сохранение completed с публикацией события (`handleSaveCompletedSummary`, `persistence`) по-прежнему без
  теста: это код соседнего модуля в `journey-summary`, а не шага саммаризации.
- Пустой `summary.enabled-chats` (пустой или отсутствующий `SUMMARY_ENABLED_CHATS`) достижим и означает
  «саммаризация выключена»; поведение этого случая тестом не покрыто — проверен только парсинг списка с
  пробелами.
- `ProcessSummaryCommandImpl` ловит исключение `GenerateSummaryCommand`; покрыто проглатывание, а не логи
  (лог — ненаблюдаемое для пользователя поведение).
