# answer-decision — нужен ли AI-ответ, спам-уведомление и маршрутизация интентов

- **id:** `answer-decision`
- **Статус:** delivered

## Что делает

По событию `MESSAGE_RECEIVED` прогоняет decision-модель и правила и решает, что делать с сообщением. Для
применимого сообщения (человек в групповом чате при включённом kill-switch) вызывается decision-модель;
при `spamProbability >= decision.spam-threshold` бот публикует обычное сообщение-уведомление и обработка
останавливается. Иначе работают правила `requiredAnswer`: если ответ не нужен, поток на этом шаге
заканчивается; если нужен — итоговый интент выбирает ровно одно событие: `help` → `ANSWER_MESSAGE` со
справкой, `news` → `NEWS_DIGEST_REQUESTED`, `general` → `MESSAGE_REQUIRE_ANSWER` с тем же доменным
`Message`. Ветку `help` подробно описывает `bot-help.md`, ветку `news` — `chat-news-digest.md`.

Спам-правило:

| Условие | Действие |
|---|---|
| отправитель человек (`fromId.isHuman()`) и групповой чат (`peerId.isGroupChat()`) и `decision.spam-reply.enabled = true` | вызвать decision-модель один раз (спам-этап) |
| `spamProbability >= decision.spam-threshold` (дефолт `0.9`) | ровно один `ANSWER_MESSAGE` с `ResponseMessage(messageText, responseTo = исходное Message)`, текст `Сработал автодетект спама. Вероятность спама: N%.`, `N = round(spamProbability * 100)`; `MESSAGE_REQUIRE_ANSWER` не публикуется |
| отказ decision-модели (`RuntimeException`) | fail-open: WARNING без PII (только `peerId` и класс исключения), обработка продолжается по правилам ниже; после fail-open спам-этапа интент-этап делает ровно одну дополнительную попытку |
| иначе (личный чат, не-человек, kill-switch off, вероятность ниже порога) | обработка по правилам `requiredAnswer`; спам-проверка в личном чате и для не-человека не выполняется, но интент-вызов в личном чате возможен после `requiredAnswer=true`; kill-switch гасит только спам-этап и интент-вызов не выключает |

Третий вопрос decision-модели (`intent` = `help`/`news`/`general`) вычисляется в том же вызове и попадает
в `DecisionAnalysis.intent`. После `requiredAnswer=true` маршрутизация по итоговому интенту:

| Интент | Событие | Дальше |
|---|---|---|
| `HELP` | ровно один `ANSWER_MESSAGE` со справкой (`responseTo = Message`) | `vk-message-send`; `ai-answer` пропущен |
| `NEWS` | ровно один `NEWS_DIGEST_REQUESTED` с доменным `Message` | `chat-news-digest` — сводка «что нового» |
| `GENERAL` | ровно один `MESSAGE_REQUIRE_ANSWER` с доменным `Message` | `ai-answer` |

Точные `/help` и `/помощь` (trim, без учёта регистра, без аргументов) распознаются детерминированно на
интент-этапе — после спам-этапа и `requiredAnswer`, до ленивого вызова модели; `/help` и `/помощь` с
аргументами или `@`-суффиксом дают детерминированный `general`. Если анализа ещё нет (личный чат,
kill-switch off), интент-этап делает ровно один ленивый вызов модели; fail-open спам-этапа даёт ровно
одну дополнительную попытку (всего ровно два вызова) и `general` при двух отказах; при двух неудачах —
ровно две WARNING (по одной на отказ). Анализ, полученный спам-этапом, переиспользуется без второго
вызова. Порядок этапов: спам-этап → `requiredAnswer` → интент.

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
| координация: спам-этап, `requiredAnswer`, ленивый интент-вызов, маршрутизация и публикация `ANSWER_MESSAGE`/`NEWS_DIGEST_REQUESTED`/`MESSAGE_REQUIRE_ANSWER` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInput.kt:28-128` |
| правило порога спама | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isSpam/IsSpamCommand.kt:9-23` |
| decision-порт | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/port/output/decision/AnalyzeMessageOutputPort.kt:9-13` |
| клиент decision-модели: три вопроса, маппинг, ошибки | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/adapter/output/decision/DecisionModelOutputAdapter.kt:55-136` |
| правила решения | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33` |
| справка: текст и подстановка `vk.bot.mention` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/help/BuildHelpTextCommand.kt:10-36` |
| точная команда `/help`/`/помощь` и граница «с аргументами → `GENERAL`» | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/resolveIntent/ResolveMessageIntentCommand.kt:10-35` |
| предикаты чата и человека | `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/PeerId.kt:15-19` |

Конфигурация: `vk.bot.id`, `vk.bot.mention` (теперь читает и `BuildHelpTextCommandImpl`),
`decision.spam-threshold`, `decision.spam-reply.enabled` в `src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Читает доменное `Message` и настройки бота; состояние не пишет.
Decision-анализ эфемерный, в БД не сохраняется. Нормализованный текст сообщения уходит во внешний
System One (`POST /v1/systemone`, ADR 0010/0012): на спам-этапе — для применимого сообщения, после
`requiredAnswer=true` — и для интент-вызова, включая личные чаты (решение человека по личным чатам,
Q2=B), поэтому kill-switch гасит только спам-этап. WARNING содержит только `peerId` и простое имя класса
исключения, без текста; при двух отказах — ровно две записи на сообщение.

## Journey

`journey-answer`, шаг 3: между сохранением и генерацией ответа. Спам-ветка и ветка `HELP` идут из шага 3
сразу в шаг 5 (`vk-message-send`), минуя `ai-answer`. `NEWS` публикует событие и уходит в
`journey-news-digest` (шаг `chat-news-digest`). В `journey-summary` не участвует.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| доменное `Message` | `vk-long-poll` (producer), `message-storage`, `ai-answer` | `vk-long-poll` |
| `DecisionAnalysis`, `MessageClass`, `MessageIntent`, контракт трёх вопросов | тесты `processor` и `src/app` | `processor` (decision-клиент, ADR 0010/0012) |
| событие `MESSAGE_REQUIRE_ANSWER` | `ai-answer` (consumer); producer — `answer-decision` (ветка `general`) | `answer-decision` |
| событие `NEWS_DIGEST_REQUESTED` | consumer — `chat-news-digest` (сводка «что нового») | producer и payload — `answer-decision`; имя и routing key — `event-dispatch` |
| событие `ANSWER_MESSAGE`, payload `ResponseMessage` | `vk-message-send` (consumer); producers — `ai-answer` и `answer-decision` (спам и справка) | `ai-answer` (контракт/payload не меняется) |
| событие `MESSAGE_RECEIVED` | producer — `message-storage` | `message-storage` |
| `PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |

## Зависит от

- `event-dispatch` — получение `MESSAGE_RECEIVED` и публикация
  `ANSWER_MESSAGE`/`NEWS_DIGEST_REQUESTED`/`MESSAGE_REQUIRE_ANSWER`.
- внешний System One decision-endpoint — через `AnalyzeMessageOutputPort` и библиотечный
  `DecisionModel` (ADR 0010, ADR 0012).

## ADR

- ADR 0003 — решение живёт в `command`, входной адаптер ходит только в `port.input`.
- ADR 0004 — шаг связан с соседями событием RabbitMQ, а не вызовом use case другого модуля.
- ADR 0010 — decision-клиент: библиотечный `DecisionModel`, System One API, нормализация, PII-риск.
- ADR 0012 — decision-модель вызывается из runtime: спам-уведомление, kill-switch, fail-open, третий
  вопрос `intent`; инвариант «не более одного вызова» читается по попытке.

## Acceptance criteria

- **AC-answer-decision-1** — КОГДА `requiredAnswer=true` и итоговый интент `general`, СИСТЕМА ДОЛЖНА
  опубликовать ровно один `MESSAGE_REQUIRE_ANSWER` с исходным `Message`. Test:
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
  выполнять спам-проверку и не публиковать спам-ответ ни при какой вероятности; в личном чате после
  `requiredAnswer=true` модель вызывается для интента, а при `requiresAnswer=false` событий нет. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-5** — КОГДА `spamProbability` ниже порога или `decision.spam-reply.enabled =
  false`, СИСТЕМА ДОЛЖНА не публиковать спам-ответ; `kill-switch=false` выключает спам-вызов, но не
  интент-вызов после `requiredAnswer=true` (kill-switch гасит только спам-этап). Tests:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/command/isSpam/IsSpamCommandTest.kt`,
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-6** — КОГДА decision-модель отказала (`RuntimeException`), СИСТЕМА ДОЛЖНА не
  пробрасывать исключение и записать ровно одно WARNING-сообщение без текста/PII на каждый отказ: при
  одном отказе — одну, при двух (спам-этап, затем интент-этап) — ровно две на сообщение. Test:
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
- **AC-answer-decision-10** — КОГДА итоговый интент `general` (в том числе детерминированный `general`
  для `/help` с аргументами), СИСТЕМА ДОЛЖНА опубликовать ровно один `MESSAGE_REQUIRE_ANSWER` с исходным
  `Message`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.
- **AC-answer-decision-11** — КОГДА итоговый интент `news`, СИСТЕМА ДОЛЖНА опубликовать ровно один
  `NEWS_DIGEST_REQUESTED` с исходным `Message` и не публиковать `MESSAGE_REQUIRE_ANSWER`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt`.

## Пробелы приёмки

- Сами правила `IsRequireAnswerCommandImpl` не покрыты ни одним тестом: в тесте use case подставлен
  `FakeIsRequireAnswerCommand`, поэтому все пять ветвей таблицы выше держатся только на коде
  (`src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33`).
- Интент-вызов в личных чатах — новый PII-поток (решение человека по личным чатам, Q2=B); PII
  decision-логов принят ADR 0010; kill-switch гасит только спам-этап.
- `/help` с аргументами или `@`-суффиксом — детерминированный `general`: справки нет, аргументы не
  поддерживаются; `/start`, `!help` — не команды, интент решает модель.
- «Что нового» отвечает сводкой только после `requiredAnswer=true` (в группе — упоминание бота или
  forward от бота). LLM-вызов на каждый запрос, rate limit и дедупликации нет; календарный день сводки
  считается в offset-е сообщения (Long Poll отдаёт UTC) — подробности в `chat-news-digest.md`.
- Спам-ответ — только уведомление: модерации, удаления, бана и лимита ответов нет; доставка событий
  at-least-once, поэтому повторная доставка может дать повторный ответ.
- Ленивый интент-вызов достижим любому человеку в личном чате: rate limit и анти-флуда нет, на сообщение
  до двух порт-вызовов (OWASP API4:2023, CWE-770).
