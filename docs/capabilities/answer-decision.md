# answer-decision — нужен ли AI-ответ

- **id:** `answer-decision`
- **Статус:** delivered

## Что делает

По событию `MESSAGE_RECEIVED` прогоняет правила и решает, отвечать ли боту в этом чате. Если нужен ответ —
публикуется `MESSAGE_REQUIRE_ANSWER` с тем же доменным `Message`; если нет — поток на этом шаге
заканчивается, и сообщение остаётся только сохранённым.

Правила (`IsRequireAnswerCommandImpl`):

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
| координация: решение + публикация `MESSAGE_REQUIRE_ANSWER` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInput.kt:21-44` |
| правила решения | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33` |
| предикаты чата и человека | `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/PeerId.kt:15-19` |

Конфигурация: `vk.bot.id`, `vk.bot.mention` в `src/app/src/main/resources/application.yml`.

## Данные

Персистентных данных не владеет. Читает доменное `Message` и настройки бота; состояние не пишет.

## Journey

`journey-answer`, шаг 3: между сохранением и генерацией ответа. В `journey-summary` не участвует.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| доменное `Message` | `vk-webhook` (producer), `message-storage`, `ai-answer` | `vk-webhook` |
| событие `MESSAGE_REQUIRE_ANSWER` | `ai-answer` (consumer) | `answer-decision` |
| событие `MESSAGE_RECEIVED` | producer — `message-storage` | `message-storage` |
| `PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |

## Зависит от

- `event-dispatch` — получение `MESSAGE_RECEIVED` и публикация `MESSAGE_REQUIRE_ANSWER`.

## ADR

- ADR 0003 — решение живёт в `command`, входной адаптер ходит только в `port.input`.
- ADR 0004 — шаг связан с соседями событием RabbitMQ, а не вызовом use case другого модуля.

## Acceptance criteria

- **AC-answer-decision-1** — КОГДА решение «ответ нужен», СИСТЕМА ДОЛЖНА опубликовать
  `MESSAGE_REQUIRE_ANSWER` ровно один раз с исходным `Message`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt:12-30`.
- **AC-answer-decision-2** — КОГДА решение «ответ не нужен», СИСТЕМА ДОЛЖНА не публиковать событие и
  вернуть `ok`. Test:
  `src/processor/src/test/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInputTest.kt:32-47`.

## Пробелы приёмки

- Сами правила `IsRequireAnswerCommandImpl` не покрыты ни одним тестом: в тесте use case подставлен
  `FakeIsRequireAnswerCommand`, поэтому все пять ветвей таблицы выше держатся только на коде
  (`src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33`).
