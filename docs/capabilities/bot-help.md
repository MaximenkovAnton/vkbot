# bot-help — справка по /help, /помощь и «что умеешь»

- **id:** `bot-help`
- **Статус:** delivered

## Что делает

Справка — ветка шага 3 `journey-answer`: после `requiredAnswer=true` бот публикует ровно одно обычное
сообщение `ANSWER_MESSAGE` с захардкоженным русским текстом, минуя `ai-answer` и `MESSAGE_REQUIRE_ANSWER`.
Строки маршрутизации ниже действуют только при `requiredAnswer=true` (в групповом чате — упоминание бота
в тексте или forward от бота,
`src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33`).
Спам-этап идёт раньше `requiredAnswer`: при `spamProbability >= decision.spam-threshold` публикуется
спам-уведомление, а не справка, и это возможно даже при `requiresAnswer=false`. «Без вызова модели» ниже
означает «без **дополнительного (ленивого)** вызова decision-модели для интента», а не «модель не
вызывалась в группе вообще»: в группе анализ мог быть получен спам-этапом и переиспользован.

| Текст (trim, нижний регистр) | Интент | Результат |
|---|---|---|
| ровно `/help` или `/помощь` | `HELP` детерминированно, без дополнительного (ленивого) вызова модели для интента | ровно один `ANSWER_MESSAGE` с `ResponseMessage(messageText = справка, responseTo = Message)` |
| начинается с `/help` или `/помощь`, но не равен команде (`/help что нового`, `/help@simarel`) | `GENERAL` детерминированно | ровно один `MESSAGE_REQUIRE_ANSWER` (обычный AI-ответ), справки нет |
| иначе `DecisionAnalysis.intent = HELP` (третий вопрос decision-модели) | `HELP` из модели | ровно один `ANSWER_MESSAGE` со справкой |
| иначе | не `bot-help` | маршрутизация `answer-decision` |

В ветке справки `ai-answer` и `MESSAGE_REQUIRE_ANSWER` не участвуют: справка идёт сразу в
`vk-message-send`. Текст справки — константа `BuildHelpTextCommandImpl`; `<mention>` подставляется из
существующего ключа `vk.bot.mention` (дефолт `@simarel`):

```text
Я — бот сообщества. Вот что я умею:
• «Что нового» — расскажу, что происходило в чате за выбранный срок. Примеры: «что нового за вчера», «что нового с 1 по 3 ноября». Если срок не указать, возьму время с вашего последнего сообщения, но не больше недели.
• «Что ты умеешь», /help или /помощь — эта справка: что я умею и как меня вызвать.
В групповом чате упомяните бота, например: @simarel что нового за вчера. Остальные вопросы получат обычный AI-ответ.
```

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_RECEIVED` из `all-events-queue` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/adapter/input/event/messageReceived/MessageReceivedEventProcessor.kt:12-22` |
| маршрутизация после `requiredAnswer`: точная команда, ленивый вызов модели, публикация справки | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInput.kt:75-115` |
| правило точной команды: `/help`, `/помощь`, граница «с аргументами → `GENERAL`» | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/resolveIntent/ResolveMessageIntentCommand.kt:10-35` |
| текст справки и подстановка `vk.bot.mention` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/help/BuildHelpTextCommand.kt:10-36` |

Конфигурация: `vk.bot.mention` (существующий ключ, дефолт `@simarel`) —
`src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет, в БД ничего не пишет. Текст справки — константа кода. Текст сообщения,
ушедшего в decision-модель для интента, логируется провайдером на INFO и экспортируется по OTLP — PII,
принятый риск ADR 2026-11-08-decision-model-langchain4j, ADR 2026-11-09-decision-runtime-spam-policy; новый код текст не логирует. `vk.bot.mention` — существующий ключ (ADR 2026-10-28-configuration-and-secrets).

## Journey

`journey-answer`, шаг 3, ветка `HELP`: `ANSWER_MESSAGE` → `vk-message-send`; `ai-answer` пропущен.
В `journey-summary` не участвует.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| событие `ANSWER_MESSAGE`, payload `ResponseMessage` | `vk-message-send` (consumer); producers — `ai-answer`, `answer-decision` (спам и справка), `chat-news-digest` (сводка «что нового») | `ai-answer` (контракт/payload не меняется) |
| `MessageIntent` | `answer-decision`, тесты `processor` и `src/app` | `processor` (ADR 2026-11-08-decision-model-langchain4j, ADR 2026-11-09-decision-runtime-spam-policy) |
| `vk.bot.mention` | `IsRequireAnswerCommandImpl`, `BuildHelpTextCommandImpl` | composition root `:src:app` (ADR 2026-10-28-configuration-and-secrets) |

## Зависит от

- `answer-decision` — та же точка ветвления шага 3: справка публикуется после `requiredAnswer=true`.
- `event-dispatch` — публикация `ANSWER_MESSAGE` и доставка события.

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — команда и use case без фреймворка, вход только через `port.input`.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — шаг в событийном потоке, публикация через `PublishEventCommand`.
- ADR 2026-11-08-decision-model-langchain4j — decision-клиент и PII decision-логов (INFO/OTLP).
- ADR 2026-11-09-decision-runtime-spam-policy — источник интента — decision-модель, спам/jailbreak-kill-switch'и гасят только decision-этап.

## Acceptance criteria

- **AC-bot-help-1** — КОГДА `requiredAnswer=true` и текст (trim, без учёта регистра) точно `/help` или
  `/помощь`, СИСТЕМА ДОЛЖНА опубликовать ровно один `ANSWER_MESSAGE` со справкой; дополнительного
  (ленивого) вызова decision-модели для интента нет (в группе анализ мог быть получен спам-этапом);
  `/help` с аргументами или `@`-суффиксом — детерминированный `GENERAL`, справки нет. Tests:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`,
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/command/resolveIntent/ResolveMessageIntentCommandTest.kt`.
- **AC-bot-help-2** — КОГДА `requiredAnswer=true`, текст не является точной командой и итоговый
  `DecisionAnalysis.intent = HELP` (из анализа спам-этапа или ленивого вызова), СИСТЕМА ДОЛЖНА
  опубликовать ровно один `ANSWER_MESSAGE` со справкой (`responseTo = Message`). Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-bot-help-3** — КОГДА `BuildHelpTextCommand.execute` вызван при настроенном `vk.bot.mention`,
  СИСТЕМА ДОЛЖНА вернуть текст справки дословно: 4 строки, разделитель `\n`, без завершающего `\n`,
  упоминание подставлено. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/command/help/BuildHelpTextCommandTest.kt`.
- **AC-bot-help-4** — КОГДА `requiredAnswer=true` и справка опубликована (по точной команде — без
  дополнительного (ленивого) вызова модели для интента; по интенту `HELP` — из анализа спам-этапа или из
  того же ленивого вызова, без дополнительного вызова), СИСТЕМА ДОЛЖНА не публиковать
  `MESSAGE_REQUIRE_ANSWER`/`NEWS_DIGEST_REQUESTED` и не запускать AI-ответ. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-bot-help-5** — КОГДА `requiresAnswer=false` (в группе нет упоминания или forward от бота),
  СИСТЕМА ДОЛЖНА не выполнять маршрутизацию по интенту: нет `ANSWER_MESSAGE` со справкой, нет
  `MESSAGE_REQUIRE_ANSWER` и `NEWS_DIGEST_REQUESTED`; спам-уведомление возможно, потому что спам-этап
  идёт до `requiredAnswer`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.

## Пробелы приёмки

- `/help` с аргументами не поддерживаются как команда — детерминированный `GENERAL` без справки;
  `/start`, `!help` не поддерживаются; «что нового» — только через интент модели.
- В групповом чате справка подчиняется `requiredAnswer`: нужно упоминание бота или forward от бота.
- Спам-этап имеет приоритет над точной командой и может дать уведомление при `requiresAnswer=false`.
- Доставка событий at-least-once: повторная доставка может дать повторную справку; дедупликации нет.
- PII decision-логов — принятый риск ADR 2026-11-08-decision-model-langchain4j, ADR 2026-11-09-decision-runtime-spam-policy.
