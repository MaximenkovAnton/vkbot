# conversation-summary — итог обсуждения

- **id:** `conversation-summary`
- **Статус:** delivered

## Что делает

По событию `SUMMARY_REQUESTED` решает, пора ли подводить итог чата, и если да — саммаризирует переписку
через LLM, ведёт lifecycle записи саммари и присылает итог в чат.

Правила (`ProcessSummaryCommandImpl`):

| Условие | Поведение |
|---|---|
| чат не в `summary.enabled-chats` (список приходит из обязательного непустого `SUMMARY_ENABLED_CHATS`; пустое значение не даёт стартовать, `SRCFG00040`) | выйти, ничего не делать |
| у чата уже есть `PENDING`-саммари | пропустить, чтобы не запускать второй прогон |
| последнее саммари зависло в `PENDING` | WARNING и пропуск |
| сообщений меньше `summary.threshold` и прошлое саммари есть | пропустить |
| иначе | создать `PENDING`, взять до `summary.batch-size` сообщений, вызвать LLM, сохранить `COMPLETED` |

Саммаризация — часть `GenerateSummaryCommandImpl`: выборка до 1000 сообщений, форматирование с именами
авторов, LLM (3 попытки), сохранение completed; при исключении запись помечается `FAILED` и исключение
пробрасывается дальше. `ProcessSummaryCommandImpl` ловит это исключение и логирует его, поэтому событие
подтверждается, запись остаётся `FAILED`, а retry брокера не запускается. Сохранение completed идёт через
in-process порт `message-storage`, который публикует `SUMMARY_READY`; потребитель события — `vk-facade`
(`SummaryReadyEventProcessor`), он формирует forward-блок на диапазон `[first, last]` и отправляет итог
через `VkSendMessageInputPort` (`vk-message-send`).

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `SUMMARY_REQUESTED` из `all-events-queue` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessor.kt:12-28` |
| порог, включённые чаты, pending, batch-size | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt:37-126` |
| создание pending, выборка, форматирование, LLM | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImpl.kt:27-90` |
| LLM-адаптер, 3 попытки | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapter.kt:17-42` |
| AI-сервис саммаризации и JSON-ответ | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiService.kt:8-37` |
| сохранение completed → публикация `SUMMARY_READY` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:115-146` |
| `SUMMARY_READY` → forward-блок → отправка в VK | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt:23-54` |

Конфигурация: `summary.enabled-chats` (обязательный непустой `SUMMARY_ENABLED_CHATS`, ADR 0006),
`summary.threshold`, `summary.batch-size`, `aiSumarizationSystemprompt`
в `src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет: записи саммари хранит `message-storage` (таблица `summaries`, миграция
`src/persistence/src/main/resources/db/changelog/changes/004-create-summaries-table.yaml`). Владеет
смыслом lifecycle `PENDING`/`COMPLETED`/`FAILED` и правилами запуска.

## Journey

`journey-summary`, центральный шаг: от `SUMMARY_REQUESTED` до `SUMMARY_READY`; доставку выполняет
`vk-message-send`.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `SUMMARY_REQUESTED` и payload `SummaryRequestedPayload` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryRequestedPayload.kt`) | producer — `message-storage` | `message-storage` |
| `SUMMARY_READY` и payload `SummaryReadyPayload` (`src/share/src/main/kotlin/com/simarel/vkbot/share/domain/event/SummaryReadyPayload.kt`) | producer — `message-storage` (`PersistenceDataUsecase.handleSaveCompletedSummary`), consumer — этот шаг | `message-storage` |
| таблица `summaries` и статусы | `message-storage` | `message-storage` |
| `VkSendMessageInputPort` | producer — `vk-message-send` | `vk-message-send` |
| in-process порты чтения сообщений/профилей и записи саммари | `ai-answer` (те же порты чтения) | `message-storage` |

## Зависит от

- `event-dispatch` — получение `SUMMARY_REQUESTED`.
- `message-storage` — in-process порты чтения сообщений/профилей и lifecycle-записи саммари.
- `vk-message-send` — доставка итога в чат.

## ADR

- ADR 0003 — вся бизнес-логика саммаризации в `command`, LLM — только в `adapter/output`.
- ADR 0004 — сообщения и профили читаются in-process портами, `SUMMARY_READY` публикуется событием.
- ADR 0006 — порог и batch-size — конфигурация с дефолтами `100`; включённые чаты — обязательный непустой
  `SUMMARY_ENABLED_CHATS`, без него приложение не стартует, поэтому «выключено по умолчанию» недостижимо.

## Acceptance criteria

Нет AC с тестом: ни один существующий тест не доказывает ни одно поведение этой возможности (в тестовом
профиле `summary.enabled-chats: 1,2,3`, а peerId интеграционного теста — `2000000001`/`2000000002`,
поэтому шаг не запускается; в модуле `ai` тестов нет). Это пробел продукта, зафиксированный в
`capabilities.md`, а не отсутствие возможности — код её реализует.

## Пробелы приёмки

- `ProcessSummaryCommandImpl` (включённые чаты, pending, порог, batch-size) не покрыт тестом.
- `GenerateSummaryCommandImpl` (pending → completed/failed, форматирование авторов) не покрыт тестом.
- `SummarizationAiAdapter` (3 попытки, затем failed) не покрыт тестом.
- `SummaryReadyEventProcessor` (forward на диапазон, синтетическое сообщение) не покрыт тестом.
- Handler `handleSaveCompletedSummary` и публикация `SUMMARY_READY` не покрыты тестом.
