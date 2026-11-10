# Модульная структура

12 Gradle-модулей (`settings.gradle.kts:22-33`), один деплой — `:src:app`
([ADR 0002](../adr/0002-single-deployable-app-module.md)). Модули — горизонтальные бизнес-модули вокруг
общего ядра `share`; фичи (приём событий VK, ответ, саммари, профили, поздравления) — вертикали внутри них. Компилятор делает
неправильный импорт ошибкой сборки, а ArchUnit-тесты проверяют правила слоёв
([ADR 0003](../adr/0003-module-boundaries-and-layer-rules.md)).

## Разрешённый граф зависимостей

Production-зависимости (`GradleDependenciesTest`):

```
share            -> (никого)
receiver         -> share
processor        -> share
ai               -> share
persistence      -> share
vk-facade        -> share
infrastructure   -> share
greetings        -> share
app              -> ai, greetings, share, infrastructure, persistence, vk-facade, processor, receiver
testing/test-fixtures -> share, vk-facade
testing/test-common   -> (никого)
testing/arch-tests    -> все бизнес-модули + app
```

Test-scope рёбра (не входят в production-граф, разрешены): `processor`, `receiver`, `persistence` берут
`:src:infrastructure` в `testImplementation` ради CDI-декораторов в тестах; `persistence` и `receiver` берут
`testFixtures` общего набора fake-объектов.

## `share` — домен и контракты

- **Ответственность:** доменные модели, value objects, события, базовые контракты и исходящие порты,
  которыми пользуются остальные модули. Никакой реализации и никаких адаптеров — слоя `adapter` здесь нет.
- **Публичный API:**
  - модели: `share/domain/model/{Message,StoredMessage,ResponseMessage,Summary,VkUserProfile,VkGroupProfile}.kt`
    (`VkUserProfile.profileData: Map<String, Any?>` — полный ответ VK без фото, ADR 2026-11-10);
  - VO: `share/domain/vo/{MessageText,PeerId,FromId,Date,ConversationMessageId,GroupId,Payload,LongPollTs}.kt` —
    `@JvmInline value class`, например `MessageText.kt:3-12`;
  - события: `share/domain/Event.kt:3-11` (7 значений) и payload'ы `share/domain/event/{SummaryRequestedPayload,SummaryReadyPayload}.kt`;
  - контракты: `share/port/Port.kt:3-9`, `share/port/input/InputPort.kt:7-13`, `share/port/output/OutputPort.kt:7-13`,
    `share/command/Command.kt:3-8`, `share/command/publishEvent/PublishEventCommand.kt:9-13`,
    `share/port/input/EventProcessor.kt:5-8`, `share/port/output/PublishEventOutputPort.kt:6-10`;
  - исходящие порты: 18 `persistence`-портов (сообщения, саммари, чаты, профили по дате рождения, claim
    поздравления, три read-only порта сводки «что нового»), 2 — для позиции Long Poll
    (`FindLongPollTsPort`, `SaveLongPollTsPort`), 1 ai (`GenerateGreetingTextPort`) и 3 vk
    (`share/port/output/vk/`, включая `SendGreetingMessagePort`) — каждый `OutputPort<REQ,RESP>` с одной
    операцией;
  - ошибки: `share/domain/exception/{VkBotAppException,ExceptionStatus,ValidationException}.kt` — единая
    иерархия: `ValidationException` наследует `VkBotAppException`, доменная ошибка добавляет класс на
    value object и лист на конкретную проверку ([ADR 0011](../adr/0011-exception-hierarchy.md)).
- **Зависимости:** нет проектных (только библиотеки: Jakarta CDI, Quarkus core/arc, RabbitMQ messaging,
  Jackson, REST client — `src/share/build.gradle.kts:16-33`).

## `receiver` — вход событий VK (Bots Long Poll)

- **Ответственность:** владелец входящего транспорта VK: опрашивает Bots Long Poll API
  (`groups.getLongPollServer` → `a_check`), парсит VK JSON в DMZ-DTO, маппит в доменное `Message`,
  публикует `MESSAGE_NEW` и сохраняет позицию опроса `ts`. HTTP-эндпоинта у модуля нет: `POST /vk/callback`
  отвечает 404. Исходящие вызовы VK API (`messages.send`, профили) остаются за `vk-facade`
  ([ADR 0008](../adr/0008-bots-long-poll-transport.md)).
- **Публичный API:**
  - цикл опроса — `receiver/adapter/input/vk/LongPollRunner.kt` (виртуальный поток, старт по
    `StartupEvent`, стоп по `ShutdownEvent`, включение ключом `vk.longpoll.enabled`);
  - `ReceiveMessageInputPort` — `receiver/port/input/ReceiveMessageInputPort.kt`
    (`VkUpdateInputPortRequest(type, message)`);
  - порты Long Poll — `receiver/port/output/LongPollPort.kt` (сессия и `a_check`), выходной адаптер —
    `receiver/adapter/output/vk/VkLongPollOutputAdapter.kt` (REST-клиент `configKey = "vk"`,
    read-timeout 60 с).
- **Ключевые классы:** `ReceiveMessageUsecase.kt` (тип обновления → `MESSAGE_NEW`),
  `PublishVkEventCommandImpl.kt:10-23` (публикует `MESSAGE_NEW`), `MessageMapper.kt:13-58` (DMZ, включая
  forward/reply/wall), `VkUpdateType.kt` (`MESSAGE_NEW`, `MESSAGE_REPLY`, `UNKNOWN`), DTO
  `adapter/input/dto/{MessageDto,WallAttachmentDto}.kt`.
- **Сборка:** `implementation(project(":src:share"))` (`src/receiver/build.gradle.kts:16`).

## `persistence` — хранение и запросы

- **Ответственность:** владелец схемы PostgreSQL и Liquibase; хранение сообщений/профилей/саммари,
  истории изменений профиля и позиции Long Poll (`long_poll_position`); публичный REST запросов
  (включая историю профиля и статистику автора); in-process реализация share-портов persistence;
  подтягивание профилей VK.
- **Публичный API:**
  - REST: `POST/GET /persistence/*`, включая `GET /persistence/profiles/users/{id}/history` и
    `GET /persistence/statistics/users/{id}` — `PersistenceDataController.kt:21-166`, контракт —
    `PersistenceService.kt:18-92` (+ DTO `CreatePendingSummaryRequest`/`CompleteSummaryRequest`, `:94-103`);
  - MQ-вход: `MessageNewEventProcessor.kt:12-22` (`MESSAGE_NEW`);
  - in-process: `PersistenceDataInProcessAdapter.kt` — 18 классов-адаптеров share-портов
    (9 прежних + `FindLongPollTsPort`/`SaveLongPollTsPort`, `FindUserProfilesByBirthDatePort`,
    `FindUserChatPeerIdsPort`, `FindGroupChatPeerIdsPort`, `ClaimDailyGreetingPort`, три read-only порта
    сводки «что нового» — `FindLastStoredMessageByAuthorPort`, `FindStoredMessagesBetweenDatesPort`,
    `FindStoredSummariesInPeriodPort`), делегируют в `PersistenceDataInputPort`;
  - вход сохранения: `SaveMessageInputPort` (`persistence/port/input/SaveMessageInputPort.kt`).
- **Ключевые классы:** `SaveMessageUsecase.kt:22-68` (профили → сохранение → `MESSAGE_RECEIVED` +
  `SUMMARY_REQUESTED`), `PersistenceDataUsecase.kt` (20 операций, включая три чтения для сводки «что
  нового», историю профиля и статистику автора; публикует `SUMMARY_READY` при сохранении завершённого
  саммари), `FetchUserProfileCommandImpl.kt:18-150` (читает сохранённые профили; отсутствующие и
  устаревшие старше месяца обновляет через in-process vk-порты, поля и снимок сливает с сохранёнными,
  diff пишет в историю), `domain/profile/ProfileDataMerger.kt` (слияние и diff снимка без Jackson),
  `StatisticsConfig.kt` (`statistics.timezone`), `JooqConfig.kt:11-20`,
  jOOQ-репозитории `adapter/output/persistence/jooq/Jooq*Repository.kt` (включая
  `JooqLongPollPositionRepository` — одна строка `long_poll_position`, и `JooqGreetingLogRepository` —
  claim `greeting_log`; `JooqMessageRepository`/`JooqSummaryRepository` читают последнее сообщение
  автора, диапазон дат и саммари периода для сводки «что нового»; `JooqVkUserProfileRepository` хранит
  `profile_data` и историю, `JooqUserStatisticsRepository` агрегирует `messages`), таблицы
  `.../jooq/Tables.kt`, changesets `005-create-long-poll-position-table`/
  `006-create-greeting-log-table`/`007-add-user-profile-data-and-history`, порты
  `persistence/port/output/persistence/` и `ForwardedMessagesSerializationPort.kt:8-16`.
- **Сборка:** `share`, jOOQ 3.19.10, Liquibase, Agroal, PostgreSQL, REST (`src/persistence/build.gradle.kts:13-55`).
  Кодогенерация jOOQ выключена без `JOOQ_JDBC_URL` (`:89-125`), таблицы описаны вручную.

## `processor` — решение об ответе, маршрутизация интентов, спам/jailbreak-уведомления и клиент decision-модели

- **Ответственность:** по `MESSAGE_RECEIVED` пройти два этапа. **Decision-этап:** для человека в
  групповом чате при включённой хотя бы одной ветке (`decision.spam-reply.enabled=true` или
  `decision.jailbreak-reply.enabled=true`) вызвать decision-модель **один раз**; при спаме опубликовать
  спам-`ANSWER_MESSAGE`, иначе при попытке взлома ИИ — jailbreak-`ANSWER_MESSAGE`, и остановить
  обработку (приоритет `spam → jailbreak`). **Интент-этап:** после `requiredAnswer=true` маршрутизировать
  итоговый интент — `HELP` → `ANSWER_MESSAGE` со справкой, `NEWS` → `NEWS_DIGEST_REQUESTED`,
  `GENERAL` → `MESSAGE_REQUIRE_ANSWER`; если анализа ещё нет (личный чат, обе ветки off, fail-open),
  интент-этап делает ровно один (после fail-open — одну дополнительную) вызов и отправляет
  нормализованный текст в System One (PII, ADR 0010/0012); интент-вызов не гасится
  спам/jailbreak-kill-switch'ами. Клиент decision-модели вызывается из runtime
  ([ADR 0012](../adr/0012-decision-runtime-spam-policy.md),
  [ADR 0014](../adr/0014-decision-jailbreak-detection.md)).
- **Публичный API:** `MessageReceivedEventProcessor.kt:12-22` (`MESSAGE_RECEIVED`), вход —
  `processor/port/input/messageReceived/MessageReceivedInputPort.kt`.
- **Ключевые классы:** `MessageReceivedUsecaseInput.kt:31-141` (decision-этап с приоритетом
  `spam → jailbreak`, `requiredAnswer`, ленивый интент-вызов, fail-open, маршрутизация и публикация
  `ANSWER_MESSAGE`/`NEWS_DIGEST_REQUESTED`/`MESSAGE_REQUIRE_ANSWER`), `IsSpamCommand.kt:9-23` (правило
  `spamProbability >= decision.spam-threshold`), `IsJailbreakCommand.kt:9-23` (правило
  `jailbreakProbability >= decision.jailbreak-threshold`), `IsRequireAnswerCommand.kt:10-34` (правила: не
  человек → нет; личный чат → да; `!`/`\` → нет; упоминание бота или forward от бота → да),
  `command/help/BuildHelpTextCommand.kt:10-36` (текст справки и `vk.bot.mention`),
  `command/resolveIntent/ResolveMessageIntentCommand.kt:10-35` (точные `/help`/`/помощь` и граница «с
  аргументами → `GENERAL`»). В коде есть TODO-комментарий о будущем контексте (RAG) — это не реализовано.
- **Decision-клиент:** порт `processor/port/output/decision/AnalyzeMessageOutputPort.kt`
  (`OutputPort<AnalyzeMessageRequest, AnalyzeMessageResponse>`), адаптер
  `processor/adapter/output/decision/DecisionModelOutputAdapter.kt` над библиотечным
  `dev.langchain4j.model.decision.DecisionModel`, доменные `MessageClass`, `MessageIntent`,
  `DecisionAnalysis` (включая `jailbreakProbability`) и `MessageTextNormalizer`
  (`processor/domain/decision/`), отказ —
  `DecisionModelException` (`EXTERNAL_FAILURE`). Один `decide` задаёт четыре вопроса — `spam`, `class`,
  `intent`, `jailbreak` — и отправляет их одним `POST /v1/systemone`; адаптер маппит `intent` в
  `MessageIntent`, и процессор маршрутизирует его после `requiredAnswer=true`; `NEWS_DIGEST_REQUESTED`
  публикует processor, потребляет `ai` (`NewsDigestRequestedEventProcessor`). Kill-switch'и гасят ветку
  реакции, а не вопрос: decision-этап вызывается при включённой хотя бы одной ветке. Детект смотрит
  только `message.messageText` — forward/вложения не анализируются (named gap `answer-decision`).
- **Сборка:** `share`; внешняя зависимость `quarkus-langchain4j-ollama` для decision-модели (как у `ai` —
  `quarkus-langchain4j-openai`); `testImplementation` infrastructure для декораторов
  (`src/processor/build.gradle.kts:13`). Project-рёбра при этом не меняются.

## `ai` — ответы, саммаризация, сводка «что нового», OCR, Vane

- **Ответственность:** генерация AI-ответа, саммаризация переписки, сводка «что нового» (период из
  текста запроса, чтение сохранённых саммари и сообщений, LLM-пересказ), текст поздравлений, OCR-команда,
  веб-инструменты Vane; потребление `MESSAGE_REQUIRE_ANSWER`/`SUMMARY_REQUESTED`/`NEWS_DIGEST_REQUESTED`,
  публикация `ANSWER_MESSAGE`; in-process `GenerateGreetingTextPort` для `greetings`.
- **Публичный API:**
  - MQ-входы: `MessageRequireAnswerEventProcessor.kt:12-22`, `SummaryRequestedEventProcessor.kt:12-28`,
    `NewsDigestRequestedEventProcessor.kt:12-22` (`NEWS_DIGEST_REQUESTED`, тело — доменный `Message`);
  - входы use case: `port/input/messageRequireAnswer/MessageRequireAnswerInputPort.kt`,
    `port/input/summary/{GenerateSummaryInputPort,ProcessSummaryInputPort}.kt`,
    `port/input/news/ProcessNewsDigestInputPort.kt`;
  - in-process: `adapter/output/ai/greeting/GreetingTextOutputAdapter.kt` — `GenerateGreetingTextPort`.
- **Ключевые классы:**
  - ответ: `GenerateAnswerUsecase.kt:16-41` → `GenerateAnswerCommand.kt:14-48` (контекст через
    `ConversationContextOutputPort` + LLM через `GenerateAnswerOutputPort`); контекст —
    `ConversationContextRestAdapter.kt:21-104` (20 последних сообщений, профили, парсинг JSONB
    `forwardedMessages`); LLM — `UserAnswerAiService.kt:13-45` + `GenerateAnswerOutputAdapter.kt:15-120`;
  - саммаризация: `ProcessSummaryCommandImpl.kt:11-128` (включённые чаты, порог, pending, батч),
    `GenerateSummaryCommandImpl.kt:13-61` (pending-запись → форматирование общим `MessageAuthorFormatter` →
    LLM → completed или failed), `SummarizationAiAdapter.kt:12-42` (3 попытки), `SummarizationAiService.kt:8-37`;
  - Vane: `VaneSearchToolAdapter.kt:10-32` (`@Tool search_web/search_academic/search_discussions`),
    `VaneSearchCommand` → `VaneSearchOutputAdapter.kt:10-72` (выбор провайдера) →
    `VaneSearchClient.kt:11-25` (`/api/search`, `/api/providers`);
  - OCR: `RecognizeImagesCommandImpl.kt:8-27`, `ImageRecognitionOutputAdapter.kt:12-38`, `OcrAiService.kt:9-46`;
  - поздравления: `GreetingAiService.kt` (`@RegisterAiService`, `@SystemMessage("{config:aiGreetingSystemprompt}")`)
    + `GreetingTextOutputAdapter.kt` (`GenerateGreetingTextPort`, формат даты и списков);
  - сводка «что нового»: `ProcessNewsDigestUsecase` → `ProcessNewsDigestCommandImpl` (период через
    `NewsPeriodParser`/`NewsPeriod`, три read-only in-process порта к `persistence`, ровно один
    `ANSWER_MESSAGE`), `NewsDigestAiAdapter`/`NewsDigestAiService` (`@RegisterAiService` без инструментов,
    system-промпт `{config:aiNewsDigestSystemprompt}`, 3 попытки, `NewsDigestGenerationException`),
    `MessageAuthorFormatter` — общий форматтер авторов (`GenerateSummaryCommandImpl` и сводка);
  - память чата: `TransientChatMemoryProvider.kt:10-17` (in-memory, 10 сообщений, не персистится).
- **Сборка:** `share`, `quarkus-arc`, `quarkus-rest-client-jackson`, LangChain4j OpenAI-совместимый
  провайдер (`quarkus-langchain4j-openai`, ADR 0007) (`src/ai/build.gradle.kts:14-46`).

## `vk-facade` — выход в VK API и профили

- **Ответственность:** отправка сообщений в VK (forward-ветка и плоская ветка), получение профилей,
  публичный REST профилей, in-process реализация share-портов vk; потребление `ANSWER_MESSAGE`/
  `SUMMARY_READY`.
- **Публичный API:**
  - REST: `GET /api/profiles/users/batch`, `GET /api/profiles/groups/batch` — `VkFacadeController.kt:14-32`;
  - MQ-входы: `SendMessageEventProcessor.kt:12-22` (`ANSWER_MESSAGE`),
    `SummaryReadyEventProcessor.kt:18-57` (`SUMMARY_READY`, формирует forward-блок);
  - in-process: `VkProfileInProcessAdapter.kt:8-36` — 2 адаптера `GetUserProfilesBatchPort`/
    `GetGroupProfilesBatchPort` через `VkProfileInputPort`; `SendGreetingMessageInProcessAdapter.kt` —
    `SendGreetingMessagePort` через `VkSendPlainMessageInputPort`;
  - входы: `port/input/vk/{VkSendMessageInputPort,VkSendPlainMessageInputPort,VkProfileInputPort}.kt`.
- **Ключевые классы:** `MessageSendUsecase.kt:15-44` (peer_id/message/forward/random_id),
  `PlainMessageSendUsecase.kt` (плоская отправка: `forwardedMessages = null`, `random_id` от текста и peer),
  `RandomId.kt` (две формулы: ответ — текст+peer+дата, плоская — текст+peer),
  `VkProfileUsecase.kt:9-21`, `SendVkMessageCommandImpl.kt:8-19`, `VkSendMessageOutputAdapter.kt:12-29`
  (ошибка VK → `VkException`), `GetProfileAdapter.kt:12-77` (VK `users.get` со всеми полями пользователя,
  кроме фото/аватарки, и `groups.getById`; группы хранятся с отрицательным id),
  `VkClient.kt:12-39` (`configKey = "vk"`, `v=${vk.api.version}`,
  `disable_mentions`, `dont_parse_links`).
- **Сборка:** `share`, REST/REST client, fault-tolerance, RabbitMQ (`src/vk-facade/build.gradle.kts:13-23`).

## `greetings` — поздравления по cron-расписанию

- **Ответственность:** прогон поздравлений по cron-расписанию Quarkus (`greetings.cron`, по умолчанию
  00:00) в зоне `greetings.timezone` (по умолчанию `Europe/Minsk`); правила праздников и дней рождения;
  отдельное сообщение на каждый праздник и одно на всех именинников чата; at-most-once через claim на чат;
  текст ИИ с шаблоном-фолбэком; спан `greetings.daily-run`. Событий RabbitMQ и HTTP-входов нет.
- **Публичный API:** `port/input/RunDailyGreetingsInputPort.kt` (`Request(date)` → `Response(planned, sent,
  failed)`), `adapter/input/schedule/DailyGreetingRunner.kt` (`@Scheduled` с `cron = "{greetings.cron}"`,
  `timeZone = "{greetings.timezone}"`, ключи `greetings.cron`/`greetings.timezone`).
- **Ключевые классы:** `usecase/RunDailyGreetingsUsecase.kt` (план → claim на чат → generate → send по
  планам; ошибка одного сообщения/чата не роняет остальные), `command/collect/CollectGreetingPlansCommand.kt`
  (групповые чаты + профили по дате рождения, один план на праздник и один на именинников чата),
  `command/text/GenerateGreetingTextCommand.kt` (ИИ + детерминированный шаблон),
  `domain/{Holidays,BirthdayDates,NameFormatter,GreetingPlan,Holiday}.kt`.
- **Сборка:** `share`, `quarkus-scheduler`, `quarkus-arc`, `jakarta.enterprise.cdi-api`, `opentelemetry-api`
  (`src/greetings/build.gradle.kts`).

## `infrastructure` — брокер и декораторы

- **Ответственность:** диспетчер RabbitMQ, retry-лестница и финальный DLQ, публикация событий, logging
  декораторы, Jackson-конфигурация.
- **Публичный API:**
  - MQ-вход: `@Incoming("all-events-queue")` — `EventDispatcher.kt:63-85`;
  - реализация `PublishEventOutputPort` — `PublishEventOutputAdapter.kt:15-33` (JSON + routing key = `Event.name`);
  - CDI-декораторы: `CommandLoggingDecorator.kt:12-28`, `UsecaseLoggingDecorator.kt:12-28`.
- **Ключевые классы:** `EventDispatcher` (карта `Event.name → EventProcessor`, `@RunOnVirtualThread`,
  retry по заголовку `x-retry-count`, `:91-113`), `DlqConfig.kt:6-16` (`dlq.levels[]`),
  `MapperConfig.kt:9-18` (Kotlin-модуль Jackson).
- **Сборка:** `share`, Quarkus arc/jackson/messaging-rabbitmq/logging-json, SmallRye RabbitMQ
  (`src/infrastructure/build.gradle.kts:13-26`).

## `app` — деплой и composition root

- **Ответственность:** единственный Quarkus-деплой, runtime-конфигурация, интеграционные тесты.
- **Публичный API:** `@QuarkusMain` — `app/Application.kt:6-12`; конфиги
  `src/app/src/main/resources/{application.yml,application-queues.yml}`.
- **Сборка:** все 7 бизнес-модулей + share (`src/app/build.gradle.kts:16-23`), deployable-расширения
  (`:26-37`).

## `testing/test-fixtures`, `testing/test-common`, `testing/arch-tests`

- **`test-fixtures`** — общие fake-объекты и провайдеры данных:
  `testfixtures/adapter/output/vk/FakeVkClient.kt:9-41`, `.../adapter/output/mq/FakePublishEventOutputPort.kt:8-16`,
  `.../command/vkFacade/FakePublishEventCommand.kt:8-16`, `.../domain/FakeVoProvider.kt:18-69`.
  Модуль зависит от `share` и `vk-facade`, но подключается только в test-конфигурациях.
- **`test-common`** — общий тестовый `application.yaml` (`vk.*`, `vane.url`, DevServices off).
- **`arch-tests`** — `ModuleDependenciesTest` (модуль видит только share), `LayerDependenciesTest` (L1–L5),
  `GradleDependenciesTest` (allowlist, 13 build-файлов, testing-модули только в тестах). Подключает все
  модули + `app` и передаёт тесту корень репозитория (`systemProperty("project.root", rootDir.absolutePath)`).

## Межмодульное взаимодействие

### События RabbitMQ

Обменник `all-events`; routing key = имя события (`PublishEventOutputAdapter.kt:20-30`), консьюмеры
маршрутизируются по `EventProcessor.event()` (`EventDispatcher.kt:21-24`).

| Событие | Публикует | Потребляет |
|---|---|---|
| `MESSAGE_NEW` | receiver (`PublishVkEventCommandImpl.kt:15-23`) | persistence (`MessageNewEventProcessor.kt:12-22`) |
| `MESSAGE_RECEIVED` | persistence (`SaveMessageUsecase.kt:33-38`) | processor (`MessageReceivedEventProcessor.kt:12-22`) |
| `SUMMARY_REQUESTED` | persistence (`SaveMessageUsecase.kt:39-49`) | ai (`SummaryRequestedEventProcessor.kt:12-28`) |
| `MESSAGE_REQUIRE_ANSWER` | processor — ветка `general` (`MessageReceivedUsecaseInput.kt:74-78`) | ai (`MessageRequireAnswerEventProcessor.kt:12-22`) |
| `ANSWER_MESSAGE` | ai (`GenerateAnswerUsecase.kt:28-38`, сводка «что нового» — `ProcessNewsDigestCommandImpl`), processor — спам-ветка, jailbreak-ветка и справка (`MessageReceivedUsecaseInput.kt:61-67,98-101`) | vk-facade (`SendMessageEventProcessor.kt:12-22`) |
| `NEWS_DIGEST_REQUESTED` | processor — ветка `news` (`MessageReceivedUsecaseInput.kt:74-78`) | ai (`NewsDigestRequestedEventProcessor.kt:12-22`) |
| `SUMMARY_READY` | persistence (`PersistenceDataUsecase.kt:238-269`) | vk-facade (`SummaryReadyEventProcessor.kt:18-57`) |

### In-process порты

| Порт (`share`) | Реализация | Потребитель |
|---|---|---|
| `port/output/persistence/*` (сообщения, саммари, профили) | `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` | `ai` (`GenerateSummaryCommandImpl.kt:14-19`, `ConversationContextRestAdapter.kt:22-26`) |
| `FindLastStoredMessageByAuthorPort`, `FindStoredMessagesBetweenDatesPort`, `FindStoredSummariesInPeriodPort` (read-only, сводка «что нового») | `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` | `ai` (`ProcessNewsDigestCommandImpl`) |
| `FindLongPollTsPort`, `SaveLongPollTsPort` | `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` (2 адаптера) | `receiver` (`LongPollRunner`) — позиция опроса `ts` |
| `FindGroupChatPeerIdsPort`, `FindUserChatPeerIdsPort`, `FindUserProfilesByBirthDatePort`, `ClaimDailyGreetingPort` | `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` (4 адаптера) | `greetings` (`CollectGreetingPlansCommand`, `RunDailyGreetingsUsecase`) |
| `GetUserProfilesBatchPort`, `GetGroupProfilesBatchPort` | `vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36` | `persistence` (`FetchUserProfileCommandImpl.kt:20-21`) |
| `GenerateGreetingTextPort` | `ai/adapter/output/ai/greeting/GreetingTextOutputAdapter.kt` | `greetings` (`GenerateGreetingTextCommand`) |
| `SendGreetingMessagePort` | `vkFacade/adapter/input/inprocess/SendGreetingMessageInProcessAdapter.kt` | `greetings` (`RunDailyGreetingsUsecase`) |

Проводка валидируется ArC на этапе сборки деплоя и полным интеграционным тестом
(`VkLongPollIntegrationTest.kt`).

### Публичный REST

`persistence` (`/persistence/*`) и `vk-facade` (`/api/profiles/*`) остаются публичным API: их контроллеры —
второй входной адаптер к тому же `port.input`, что и in-process адаптер (Information Expert: логика в
use case, адаптеры только маппят). Удаление этих эндпоинтов — вне текущего прохода.

## Принципы организации

- **Разделение use case и command.** Use case реализует входящий порт и координирует сценарий; command —
  атомарное действие, переиспользуемое разными use case. Use case не зовёт другой use case через входной
  порт (правило из конституции) — только command.
- **Слабая связанность.** Модуль видит только `share`; межмодульные вызовы — порты, события или публичный REST.
- **Высокая связность.** Внутри модуля слои собраны вокруг своей фичи: `domain` + `port` + `usecase` +
  `command` + адаптеры одной ответственности.
- **Один владелец данных.** Схема и jOOQ — только в `persistence`; `share` не владеет JSONB
  (`StoredMessage.forwardedMessages: String?` — известный долг, парсится на границе в
  `ConversationContextRestAdapter.kt:88-95`).
