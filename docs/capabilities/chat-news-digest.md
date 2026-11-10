# chat-news-digest — сводка «что нового» за период

- **id:** `chat-news-digest`
- **Статус:** delivered

## Что делает

Сводка «что нового» — ветка шага 3 `journey-answer` после `requiredAnswer=true`, когда итоговый интент
`news`: `answer-decision` публикует `NEWS_DIGEST_REQUESTED` с доменным `Message`, `chat-news-digest`
разбирает текст запроса, читает сохранённые данные чата, просит LLM русскую сводку и публикует ровно один
`ANSWER_MESSAGE` с `ResponseMessage(messageText, responseTo = исходное Message)`; доставляет его
`vk-message-send`.

1. Период `[from, to]` вычисляется чистым доменным парсером из текста запроса; при отсутствии срока он
   строится от последнего сообщения этого автора в чате (не более 7 суток).
2. Данные читаются тремя read-only in-process портами `message-storage` на существующих таблицах
   `messages`/`summaries`.
3. LLM собирает сводку из непустых `shortSummary` завершённых саммари периода и свежих несводённых
   сообщений периода (≤ `summary.batch-size` последних), с именами авторов как у саммаризации.
4. В `summaries` ничего не пишется, `SUMMARY_REQUESTED`/`SUMMARY_READY` не публикуются.

Фиксированные ответы без LLM: «Слишком длинный срок: сводка возможна максимум за неделю.» (явный срок
длиннее 7 суток), «Нет сообщений за этот период.» (данных нет), «Не удалось собрать сводку. Попробуйте
позже.» (финальный отказ LLM). Ошибки чтения БД и публикации не подменяются ответом — уходят в
retry-лестницу брокера.

| Форма в тексте (регистр не важен) | Период |
|---|---|
| «сегодня», «вчера», «позавчера» | границы календарного дня запроса, предыдущего, позапрошлого (в offset-е сообщения) |
| «за N дней», «за последние N дней» (N = 1..999) | `[reference.minusDays(N), reference]` |
| «за неделю», «за последнюю неделю», «эта неделя», «последняя неделя», «на этой неделе» | `[reference.minusDays(7), reference]` |
| `DD.MM`, `D <месяц>`, `DD.MM.YYYY`, `D <месяц> YYYY` (с «года»/«г.» и без) | границы одного дня; год без указания выводится не позже даты запроса |
| «с 1 по 3 ноября», «с 1 ноября по 3 ноября 2026» | включительные границы диапазона; переход года допустим |
| срок не найден, невалидная дата (`32.11`, `29.02.2025`), `from > to` | `null` → срок не указан |

Невалидный срок трактуется как «срок не указан»: `from` — дата последнего сообщения автора, если она не
позже даты запроса и не старше 7 суток, иначе `to.minusDays(7)`. Явно указанный будущий год допустим —
период уходит в пустоту и даёт «Нет сообщений за этот период.».

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `NEWS_DIGEST_REQUESTED` из `all-events-queue`: разбор `Message` и вызов входного порта | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/NewsDigestRequestedEventProcessor.kt:12-23` |
| сценарий, период, сборка данных и ровно одна публикация `ANSWER_MESSAGE` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/news/ProcessNewsDigestCommandImpl.kt:31-139` |
| период из текста запроса | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/news/NewsPeriodParser.kt:31-132` |
| LLM: `@RegisterAiService` без инструментов и памяти + 3 попытки и фолбэк-исключение | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/news/NewsDigestAiService.kt:8-24`, `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/news/NewsDigestAiAdapter.kt:10-47` |
| три read-only `share`-порта чтения | `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindLastStoredMessageByAuthorPort.kt:8-20`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredMessagesBetweenDatesPort.kt:9-22`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredSummariesInPeriodPort.kt:9-21` |
| in-process реализация чтений в `persistence` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt:212-266`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt:77-88` |
| доставка ответа в VK | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SendMessageEventProcessor.kt` |

## Конфигурация

| Ключ | Значение / env | Эффект |
|---|---|---|
| `aiNewsDigestSystemprompt` | `${NEWS_DIGEST_SYSTEM_PROMPT:Ты — помощник, который кратко пересказывает, что происходило в чате. Пиши по-русски, структурировано, без выдумок.}` | system-сообщение `NewsDigestAiService`; ключ объявлен в `src/app/src/main/resources/application.yml`, поэтому старт не зависит от env |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | лимит свежих сообщений периода (существующий ключ; читает и `ProcessSummaryCommandImpl`) |

Новых обязательных env нет.

## Данные

Владеет только текстом ответа; персистентных данных не пишет. Читает `messages` и `summaries` через три
read-only in-process порта, профили авторов — через существующие `FindStoredUserProfilesByIdsPort` и
`FindStoredGroupProfilesByIdsPort`; схема, jOOQ и Liquibase остаются у `message-storage`. Запись не
выполняется: в `summaries` новых строк нет, `SUMMARY_REQUESTED`/`SUMMARY_READY` не публикуются.

Текст чата уходит в LLM-промпт; глобальные `quarkus.langchain4j.log-requests/log-responses: true`
логируют его на INFO и экспортируют по OTLP — тот же принятый PII-риск, что у ответа и саммаризации
(ADR 0007/0010). Новый код текст не логирует: ERROR при отказе LLM содержит только `peerId` и простое имя
класса исключения. Собственных метрик и спанов нет.

## Journey

`journey-news-digest`, шаг `chat-news-digest`: триггер — ветка `news` шага `answer-decision` (событие
`NEWS_DIGEST_REQUESTED`), дальше — `vk-message-send`; транспорт событий — `event-dispatch`. В
`journey-summary` конвейер саммаризации не переиспользуется: сводка только читает готовые саммари.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| событие `NEWS_DIGEST_REQUESTED` и payload — доменный `Message` | producer — `answer-decision`; имя и routing key — `event-dispatch` | `answer-decision` (producer), `event-dispatch` (каталог `Event`) |
| событие `ANSWER_MESSAGE`, payload `ResponseMessage` | consumer — `vk-message-send`; producers — `ai-answer`, `answer-decision`, `chat-news-digest` | `ai-answer` (контракт/payload не меняется) |
| `PublishEventCommand`/`PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |
| `FindLastStoredMessageByAuthorPort`, `FindStoredMessagesBetweenDatesPort`, `FindStoredSummariesInPeriodPort` | реализация — `persistence`, потребитель — `ai` | `message-storage` (реализация и схема) |
| `summary.batch-size` | `ProcessSummaryCommandImpl` | composition root `:src:app` (ADR 0006) |
| `MessageAuthorFormatter` | `GenerateSummaryCommandImpl` | `ai` |

## Зависит от

- `answer-decision` — producer события `NEWS_DIGEST_REQUESTED`.
- `event-dispatch` — доставка события и публикация `ANSWER_MESSAGE`.
- `message-storage` — in-process чтение саммари, сообщений и профилей.
- внешний LLM — через LangChain4j AI-сервис (ADR 0007).

## ADR

- ADR 0003 — command/usecase без фреймворка, вход только через `port.input`.
- ADR 0004 — шаг в событийном потоке, чтение данных через in-process `share`-порты.
- ADR 0007 — LLM через OpenAI-совместимый API, PII-риск провайдерских логов.
- ADR 0010 — decision-модель: третий вопрос `intent`, из которого рождается `news`.
- ADR 0011 — `NewsDigestGenerationException` в единой иерархии `VkBotAppException`.
- ADR 0013 — решение «что нового»: период, три read-порта, LLM с повторами и фолбэком, публикация из команды.

## Acceptance criteria

- **AC-news-digest-1** — КОГДА из `all-events-queue` приходит сообщение с routing key
  `NEWS_DIGEST_REQUESTED` и JSON-телом доменного `Message`, СИСТЕМА ДОЛЖНА разобрать тело в `Message`,
  передать его ровно один раз в `ProcessNewsDigestInputPort` и вернуть `Event.NEWS_DIGEST_REQUESTED` из
  `event()`. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/NewsDigestRequestedEventProcessorTest.kt`.
- **AC-news-digest-2** — КОГДА текст запроса содержит одну из форм периода, СИСТЕМА ДОЛЖНА вычислить
  `NewsPeriod(from, to)` по таблице форм; КОГДА срока нет или дата/диапазон невалидны (включая
  несуществующую дату и `from > to`) — вернуть `null`; границы календарного дня — в offset-е `reference`,
  начало `00:00:00.000000000`, конец `23:59:59.999999999` включительно; сравнение года — по календарной
  дате `reference`. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/news/NewsPeriodParserTest.kt`.
- **AC-news-digest-3** — КОГДА явный срок из текста длиннее 7 суток
  (`Duration.between(from, to) > Duration.ofDays(7)`), СИСТЕМА ДОЛЖНА опубликовать ровно один
  `ANSWER_MESSAGE` с дословным текстом «Слишком длинный срок: сводка возможна максимум за неделю.» и
  `responseTo = исходное Message`, не вызывая read-порты, LLM-порт и порты профилей. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/news/ProcessNewsDigestCommandTest.kt`.
- **AC-news-digest-4** — КОГДА парсер вернул `null`, СИСТЕМА ДОЛЖНА ровно один раз запросить
  `FindLastStoredMessageByAuthorPort` и построить период: `to` — дата сообщения-запроса; если последнее
  сообщение автора есть, не позже `to` и не старше 7 суток — `from` = его дата, иначе
  `from = to.minusDays(7)`. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/news/ProcessNewsDigestCommandTest.kt`.
- **AC-news-digest-5** — КОГДА период определён, СИСТЕМА ДОЛЖНА прочитать саммари периода и свежие
  сообщения (лимит `summary.batch-size`), пропустить пустые `shortSummary`, исключить сообщение-запрос и
  сообщения не новее `max(lastMessageId)` саммари периода, сформировать данные для LLM с именами авторов
  (fallback «Пользователь N»/«Группа N»), вызвать LLM один раз, опубликовать ровно один `ANSWER_MESSAGE` и
  не вызывать write-порты `summaries` и не публиковать `SUMMARY_*`. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/news/ProcessNewsDigestCommandTest.kt`.
- **AC-news-digest-6** — КОГДА данные есть и LLM вернул непустой текст, СИСТЕМА ДОЛЖНА опубликовать ровно
  один `ANSWER_MESSAGE` с этим текстом и `responseTo = исходное Message`; КОГДА LLM бросил
  `NewsDigestGenerationException` (после трёх попыток, включая пустой ответ), СИСТЕМА ДОЛЖНА опубликовать
  «Не удалось собрать сводку. Попробуйте позже.» и не пробрасывать исключение в брокер; КОГДА данных нет,
  СИСТЕМА ДОЛЖНА опубликовать «Нет сообщений за этот период.» без вызова LLM; ошибку другого типа —
  пробросить. Tests:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/news/ProcessNewsDigestCommandTest.kt`,
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/news/NewsDigestAiAdapterTest.kt`.
- **AC-news-digest-7** — КОГДА read-порты вызваны через `share`-контракт, СИСТЕМА ДОЛЖНА вернуть последнее
  сообщение автора до заданного id; не более `limit` последних по `conversationMessageId` сообщений с
  `date ∈ [from, to]` по возрастанию id; только `COMPLETED`-саммари, чей id-диапазон пересекает период, по
  `createdAt` ASC. Tests:
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/NewsDigestIntegrationTest.kt`.
- **AC-news-digest-8** — КОГДА изменение доставлено, СИСТЕМА ДОЛЖНА содержать capability-файл со статусом
  `delivered`, строки в `docs/capabilities/index.md` (возможность и journey `journey-news-digest`), ADR
  0013 и строку в `docs/README.md`, обновлённые `docs/architecture/modules.md`,
  `docs/architecture/data-flow.md`, `docs/architecture/overview.md`, `docs/development/configuration.md`,
  `docs/development/deployment.md`, `docs/development/testing.md`, `README.md`, правки allowlist-дельты и
  ключ `aiNewsDigestSystemprompt` в `src/app/src/main/resources/application.yml`; проверка — Tests:
  `./scripts/check-capabilities.sh` exit 0 и
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/NewsDigestIntegrationTest.kt` (проводка и
  конфигурация на реальном деплое).
- **AC-news-digest-9** — КОГДА выполнены гейты `./gradlew :src:persistence:test`, `./gradlew :src:ai:test`,
  `./gradlew :src:app:test --tests "*NewsDigestIntegrationTest*"`, `./gradlew build` и
  `./scripts/check-capabilities.sh`, СИСТЕМА ДОЛЖНА завершить каждую команду с exit code 0 и записать в
  ledger дословную команду, exit code и хвост вывода. Tests:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/news/ProcessNewsDigestCommandTest.kt`,
  `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/NewsDigestIntegrationTest.kt`.

## Пробелы приёмки

- Нет rate limit/анти-флуда: LLM-вызов (до трёх попыток порта) на каждый запрос; at-least-once может дать
  повторный вызов и повторный ответ, собственного дедупа и outbox нет — дубли ограничивает только
  детерминированный `random_id` на стороне VK.
- Календарный день считается в offset-е сообщения; Long Poll отдаёт UTC, поэтому «сегодня»/«вчера» — UTC-дни.
- Саммари периода не клипуются по датам: на границе периода в сводке возможно лишнее.
- `max-tokens` не задан, усечения промпта нет: отказ провайдера по длине контекста даёт фолбэк.
- Тексты ответов — литералы кода; отдельного INFO-лога успеха, метрик и спанов нет.
- Нет индексов под новые чтения: `findLastByAuthor` сортирует по `conversation_message_id` без индекса, а
  `findByPeriod` ограничивает `EXISTS` окном периода, но всё ещё сканирует саммари peer'а. Условие
  апгрейда — changeset `007` с `messages(peer_id, conversation_message_id)` (и `summaries(peer_id,
  first_message_id)` при росте числа саммари) при медленном p95 (вне allowlist этой задачи).
