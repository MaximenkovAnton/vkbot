# answer-decision — нужен ли AI-ответ и спам-уведомление

- **id:** `answer-decision`
- **Статус:** delivered

## Что делает

По событию `MESSAGE_RECEIVED` прогоняет decision-модель и правила и решает, что делать с сообщением.
Для применимого сообщения (человек в групповом чате при включённом kill-switch) вызывается
decision-модель; при `spamProbability >= decision.spam-threshold` бот публикует обычное
сообщение-уведомление и обработка останавливается. Иначе работают правила `requiredAnswer`: если нужен
ответ — публикуется `MESSAGE_REQUIRE_ANSWER` с тем же доменным `Message`; если нет — поток на этом шаге
заканчивается, и сообщение остаётся только сохранённым.

Спам-правило:

| Условие | Действие |
|---|---|
| отправитель человек (`fromId.isHuman()`) и групповой чат (`peerId.isGroupChat()`) и `decision.spam-reply.enabled = true` | вызвать decision-модель один раз |
| `spamProbability >= decision.spam-threshold` (дефолт `0.9`) | ровно один `ANSWER_MESSAGE` с `ResponseMessage(messageText, responseTo = исходное Message)`, текст `Сработал автодетект спама. Вероятность спама: N%.`, `N = round(spamProbability * 100)`; `MESSAGE_REQUIRE_ANSWER` не публикуется |
| отказ decision-модели (`RuntimeException`) | fail-open: WARNING без PII (только `peerId` и класс исключения), обработка продолжается по правилам ниже |
| иначе (личный чат, не-человек, kill-switch off, вероятность ниже порога) | обработка по правилам `requiredAnswer` |

Третий вопрос decision-модели (`intent` = `help`/`news`/`general`) вычисляется в том же единственном
вызове и попадает в `DecisionAnalysis.intent`, но в этой возможности не маршрутизируется: справка
`/help` и «что нового» — следующая задача (`intent-routing-help`).

Правила `requiredAnswer` (`IsRequireAnswerCommandImpl`):

| Условие | Решение |
|---|---|
| отправитель не человек (`FromId` не в человеческом диапазоне) | не отвечать |
| не групповой чат (`PeerId` ≤ 2 000 000 000) | отвечать |
| текст начинается с `!` или `\` | не отвечать |
| текст содержит `vk.bot.mention` | отвечать |
| последнее пересланное сообщение — от бота (`fromId == vk.bot.id`) | отвечать |
| иначе | не отвечать |

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `MESSAGE_RECEIVED` из `all-events-queue` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/adapter/input/event/messageReceived/MessageReceivedEventProcessor.kt:12-22` |
| координация: спам-этап, решение, публикация `ANSWER_MESSAGE`/`MESSAGE_REQUIRE_ANSWER` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInput.kt:35-79` |
| правило порога спама | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isSpam/IsSpamCommand.kt:9-23` |
| decision-порт | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/port/output/decision/AnalyzeMessageOutputPort.kt:9-13` |
| клиент decision-модели: три вопроса, маппинг, ошибки | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/adapter/output/decision/DecisionModelOutputAdapter.kt:55-136` |
| правила решения | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33` |
| предикаты чата и человека | `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/PeerId.kt:15-19` |

Конфигурация: `vk.bot.id`, `vk.bot.mention`, `decision.spam-threshold`, `decision.spam-reply.enabled` в
`src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Читает доменное `Message` и настройки бота; состояние не пишет.
Decision-анализ эфемерный, в БД не сохраняется. Нормализованный текст сообщения уходит во внешний
System One (`POST /v1/systemone`, ADR 0010/0012) для применимого сообщения; WARNING при отказе
содержит только `peerId` и простое имя класса исключения, без текста.

## Journey

`journey-answer`, шаг 3: между сохранением и генерацией ответа. Спам-ветка идёт из шага 3 сразу в шаг 5
(`vk-message-send`), минуя `ai-answer`. В `journey-summary` не участвует.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| доменное `Message` | `vk-long-poll` (producer), `message-storage`, `ai-answer` | `vk-long-poll` |
| `DecisionAnalysis`, `MessageClass`, `MessageIntent`, контракт трёх вопросов | тесты `processor` и `src/app` | `processor` (decision-клиент, ADR 0010/0012) |
| событие `MESSAGE_REQUIRE_ANSWER` | `ai-answer` (consumer) | `answer-decision` |
| событие `ANSWER_MESSAGE`, payload `ResponseMessage` | `vk-message-send` (consumer); producer — `ai-answer` и `answer-decision` (спам-ответ) | `ai-answer` (контракт/payload не меняется) |
| событие `MESSAGE_RECEIVED` | producer — `message-storage` | `message-storage` |
| `PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |

## Зависит от

- `event-dispatch` — получение `MESSAGE_RECEIVED` и публикация `ANSWER_MESSAGE`/`MESSAGE_REQUIRE_ANSWER`.
- внешний System One decision-endpoint — через `AnalyzeMessageOutputPort` и библиотечный
  `DecisionModel` (ADR 0010, ADR 0012).

## ADR

- ADR 0003 — решение живёт в `command`, входной адаптер ходит только в `port.input`.
- ADR 0004 — шаг связан с соседями событием RabbitMQ, а не вызовом use case другого модуля.
- ADR 0010 — decision-клиент: библиотечный `DecisionModel`, System One API, нормализация, PII-риск.
- ADR 0012 — decision-модель вызывается из runtime: спам-уведомление, kill-switch, fail-open, третий
  вопрос `intent`.

## Acceptance criteria

- **AC-answer-decision-1** — КОГДА решение «ответ нужен», СИСТЕМА ДОЛЖНА опубликовать
  `MESSAGE_REQUIRE_ANSWER` ровно один раз с исходным `Message`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-2** — КОГДА решение «ответ не нужен», СИСТЕМА ДОЛЖНА не публиковать событие и
  вернуть `ok`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-3** — КОГДА человек пишет в групповой чат, kill-switch включён и
  `spamProbability >= decision.spam-threshold`, СИСТЕМА ДОЛЖНА опубликовать ровно один `ANSWER_MESSAGE`
  с текстом `Сработал автодетект спама. Вероятность спама: N%.`, `N = round(spamProbability * 100)`, и
  не публиковать `MESSAGE_REQUIRE_ANSWER`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-4** — КОГДА сообщение не от человека или чат не групповой, СИСТЕМА ДОЛЖНА не
  вызывать decision-модель и не публиковать спам-ответ ни при какой вероятности. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-5** — КОГДА `spamProbability` ниже порога или `decision.spam-reply.enabled =
  false`, СИСТЕМА ДОЛЖНА не публиковать спам-ответ (при выключенном kill-switch — не вызывать модель
  вовсе) и продолжать по правилам `requiredAnswer`. Tests:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/command/isSpam/IsSpamCommandTest.kt`,
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-6** — КОГДА decision-модель отказала (`RuntimeException`), СИСТЕМА ДОЛЖНА не
  пробрасывать исключение, записать одно WARNING-сообщение без текста/PII и продолжить обработку по
  прежним правилам. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-7** — КОГДА decision-модель вызвана, СИСТЕМА ДОЛЖНА задать ровно три вопроса
  (`spam`, `class`, `intent`) в этом порядке и маппить `intent` в `MessageIntent`. Tests:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/adapter/output/decision/DecisionModelOutputAdapterTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DecisionModelIntegrationTest.kt`.
- **AC-answer-decision-8** — КОГДА ответ на `intent` невалиден (нет ответа, неверный тип, значение или
  вероятность вне `{help,news,general}`) или получен отказ модели, СИСТЕМА ДОЛЖНА бросить
  `DecisionModelException` с литеральным сообщением и не возвращать частичный анализ. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/adapter/output/decision/DecisionModelOutputAdapterTest.kt`.
- **AC-answer-decision-9** — КОГДА нормализованный текст пуст, СИСТЕМА ДОЛЖНА не вызывать модель и
  вернуть `HAM`/`null`/пустую карту/`0.0`/`GENERAL`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/adapter/output/decision/DecisionModelOutputAdapterTest.kt`.
- **AC-answer-decision-10** — КОГДА `intent` = `HELP` или `NEWS`, а спам ниже порога, СИСТЕМА ДОЛЖНА
  опубликовать ровно один `MESSAGE_REQUIRE_ANSWER` и не маршрутизировать интент в этой задаче. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.

## Пробелы приёмки

- Сами правила `IsRequireAnswerCommandImpl` не покрыты ни одним тестом: в тесте use case подставлен
  `FakeIsRequireAnswerCommand`, поэтому все пять ветвей таблицы выше держатся только на коде
  (`src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33`).
- `intent` вычисляется, но не маршрутизируется до следующей задачи; личные чаты в этой возможности
  decision-модель не вызывают. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- Спам-ответ — только уведомление: модерации, удаления, бана и лимита ответов нет; доставка событий
  at-least-once, поэтому повторная доставка может дать повторный ответ.
