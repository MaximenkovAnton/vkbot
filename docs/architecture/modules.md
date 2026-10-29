# Модульная структура

11 Gradle-модулей (`settings.gradle.kts:22-32`), один деплой — `:src:app`
([ADR 0002](../adr/0002-single-deployable-app-module.md)). Модули — горизонтальные бизнес-модули вокруг
общего ядра `share`; фичи (вебхук, ответ, саммари, профили) — вертикали внутри них. Компилятор делает
неправильный импорт ошибкой сборки, а ArchUnit-тесты проверяют правила слоёв
([ADR 0003](../adr/0003-module-boundaries-and-layer-rules.md)).

## Разрешённый граф зависимостей

Production-зависимости (`GradleDependenciesTest.kt:35-65`):

```
share            -> (никого)
receiver         -> share
processor        -> share
ai               -> share
persistence      -> share
vk-facade        -> share
infrastructure   -> share
app              -> ai, share, infrastructure, persistence, vk-facade, processor, receiver
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
  - модели: `share/domain/model/{Message,StoredMessage,ResponseMessage,Summary,VkUserProfile,VkGroupProfile}.kt`;
  - VO: `share/domain/vo/{MessageText,PeerId,FromId,Date,ConversationMessageId,GroupId,Payload}.kt` —
    `@JvmInline value class`, например `MessageText.kt:3-12`;
  - события: `share/domain/Event.kt:3-10` (6 значений) и payload'ы `share/domain/event/{SummaryRequestedPayload,SummaryReadyPayload}.kt`;
  - контракты: `share/port/Port.kt:3-9`, `share/port/input/InputPort.kt:7-13`, `share/port/output/OutputPort.kt:7-13`,
    `share/command/Command.kt:3-8`, `share/command/publishEvent/PublishEventCommand.kt:9-13`,
    `share/port/input/EventProcessor.kt:5-8`, `share/port/output/PublishEventOutputPort.kt:6-10`;
  - исходящие порты: 9 persistence (`share/port/output/persistence/`) и 2 vk
    (`share/port/output/vk/`) — каждый `OutputPort<REQ,RESP>` с одной операцией;
  - ошибки: `share/domain/exception/{VkBotAppException,ExceptionStatus,ValidationException}.kt`.
- **Зависимости:** нет проектных (только библиотеки: Jakarta CDI, Quarkus core/arc, RabbitMQ messaging,
  Jackson, REST client — `src/share/build.gradle.kts:16-33`).

## `receiver` — вход VK Callback

- **Ответственность:** принять `POST /vk/callback`, проверить секрет, распарсить VK JSON в DMZ-DTO,
  смаппить в доменное `Message` и опубликовать `MESSAGE_NEW`.
- **Публичный API:**
  - `POST /vk/callback` — `receiver/adapter/input/vk/VkMediatorRouterImpl.kt:16-40` (тип события:
    `VkCallbackEvent.mapOrUnknown`, `VkCallbackEvent.kt:10-14`);
  - `ReceiveMessageInputPort` — `receiver/port/input/ReceiveMessageInputPort.kt:10-15`;
  - CDI-декоратор проверки секрета — `VkMediatorRouterSecurityDecorator.kt:11-29`;
  - единый маппинг ошибок в HTTP — `ExceptionHandler.kt:10-39`.
- **Ключевые классы:** `ReceiveMessageUsecase.kt:10-33` (confirmation/unknown/message),
  `PublishVkEventCommandImpl.kt:10-23` (публикует `MESSAGE_NEW`), `MessageMapper.kt:13-58` (DMZ, включая
  forward/reply/wall), DTO `adapter/input/dto/{MessageDto,WallAttachmentDto}.kt`.
- **Сборка:** `implementation(project(":src:share"))` (`src/receiver/build.gradle.kts:16`).

## `persistence` — хранение и запросы

- **Ответственность:** владелец схемы PostgreSQL и Liquibase; хранение сообщений/профилей/саммари;
  публичный REST запросов; in-process реализация share-портов persistence; подтягивание профилей VK.
- **Публичный API:**
  - REST: `POST/GET /persistence/*` — `PersistenceDataController.kt:21-127`, контракт —
    `PersistenceService.kt:18-79` (+ DTO `CreatePendingSummaryRequest`/`CompleteSummaryRequest`, `:81-90`);
  - MQ-вход: `MessageNewEventProcessor.kt:12-22` (`MESSAGE_NEW`);
  - in-process: `PersistenceDataInProcessAdapter.kt:17-144` — 9 классов-адаптеров share-портов,
    делегируют в `PersistenceDataInputPort`;
  - вход сохранения: `SaveMessageInputPort` (`persistence/port/input/SaveMessageInputPort.kt`).
- **Ключевые классы:** `SaveMessageUsecase.kt:22-68` (профили → сохранение → `MESSAGE_RECEIVED` +
  `SUMMARY_REQUESTED`), `PersistenceDataUsecase.kt:34-207` (9 операций; публикует `SUMMARY_READY` при
  сохранении завершённого саммари, `:132-144`), `FetchUserProfileCommandImpl.kt:15-96` (сначала локальные
  профили, недостающие — через in-process vk-порты), `JooqConfig.kt:11-20`,
  jOOQ-репозитории `adapter/output/persistence/jooq/Jooq*Repository.kt`, таблицы `.../jooq/Tables.kt:6-57`,
  порты `persistence/port/output/persistence/` (15) и `ForwardedMessagesSerializationPort.kt:8-16`.
- **Сборка:** `share`, jOOQ 3.19.10, Liquibase, Agroal, PostgreSQL, REST (`src/persistence/build.gradle.kts:13-55`).
  Кодогенерация jOOQ выключена без `JOOQ_JDBC_URL` (`:89-125`), таблицы описаны вручную.

## `processor` — решение об ответе

- **Ответственность:** по `MESSAGE_RECEIVED` решить, нужен ли AI-ответ, и опубликовать
  `MESSAGE_REQUIRE_ANSWER`.
- **Публичный API:** `MessageReceivedEventProcessor.kt:12-22` (`MESSAGE_RECEIVED`), вход —
  `processor/port/input/messageReceived/MessageReceivedInputPort.kt`.
- **Ключевые классы:** `MessageReceivedUsecaseInput.kt:15-45` (координация),
  `IsRequireAnswerCommand.kt:10-34` (правила: не человек → нет; личный чат → да; `!`/`\` → нет;
  упоминание бота или forward от бота → да). В коде есть TODO-комментарий о будущем контексте (RAG) —
  это не реализовано.
- **Сборка:** `share`; `testImplementation` infrastructure для декораторов (`src/processor/build.gradle.kts:13`).

## `ai` — ответы, саммаризация, OCR, Vane

- **Ответственность:** генерация AI-ответа, саммаризация переписки, OCR-команда, веб-инструменты Vane;
  потребление `MESSAGE_REQUIRE_ANSWER`/`SUMMARY_REQUESTED`, публикация `ANSWER_MESSAGE`.
- **Публичный API:**
  - MQ-входы: `MessageRequireAnswerEventProcessor.kt:12-22`, `SummaryRequestedEventProcessor.kt:12-28`;
  - входы use case: `port/input/messageRequireAnswer/MessageRequireAnswerInputPort.kt`,
    `port/input/summary/{GenerateSummaryInputPort,ProcessSummaryInputPort}.kt`.
- **Ключевые классы:**
  - ответ: `GenerateAnswerUsecase.kt:16-41` → `GenerateAnswerCommand.kt:14-48` (контекст через
    `ConversationContextOutputPort` + LLM через `GenerateAnswerOutputPort`); контекст —
    `ConversationContextRestAdapter.kt:21-104` (20 последних сообщений, профили, парсинг JSONB
    `forwardedMessages`); LLM — `UserAnswerAiService.kt:13-45` + `GenerateAnswerOutputAdapter.kt:15-120`;
  - саммаризация: `ProcessSummaryCommandImpl.kt:11-128` (включённые чаты, порог, pending, батч),
    `GenerateSummaryCommandImpl.kt:16-90` (pending-запись → форматирование с профилями → LLM → completed
    или failed), `SummarizationAiAdapter.kt:12-42` (3 попытки), `SummarizationAiService.kt:8-37`;
  - Vane: `VaneSearchToolAdapter.kt:10-32` (`@Tool search_web/search_academic/search_discussions`),
    `VaneSearchCommand` → `VaneSearchOutputAdapter.kt:10-72` (выбор провайдера) →
    `VaneSearchClient.kt:11-25` (`/api/search`, `/api/providers`);
  - OCR: `RecognizeImagesCommandImpl.kt:8-27`, `ImageRecognitionOutputAdapter.kt:12-38`, `OcrAiService.kt:9-46`;
  - память чата: `TransientChatMemoryProvider.kt:10-17` (in-memory, 10 сообщений, не персистится).
- **Сборка:** `share`, `quarkus-arc`, `quarkus-rest-client-jackson`, LangChain4j Ollama
  (`src/ai/build.gradle.kts:13-24`).

## `vk-facade` — выход в VK API и профили

- **Ответственность:** отправка сообщений в VK, получение профилей, публичный REST профилей, in-process
  реализация share-портов vk; потребление `ANSWER_MESSAGE`/`SUMMARY_READY`.
- **Публичный API:**
  - REST: `GET /api/profiles/users/batch`, `GET /api/profiles/groups/batch` — `VkFacadeController.kt:14-32`;
  - MQ-входы: `SendMessageEventProcessor.kt:12-22` (`ANSWER_MESSAGE`),
    `SummaryReadyEventProcessor.kt:18-57` (`SUMMARY_READY`, формирует forward-блок);
  - in-process: `VkProfileInProcessAdapter.kt:8-36` — 2 адаптера `GetUserProfilesBatchPort`/
    `GetGroupProfilesBatchPort` через `VkProfileInputPort`;
  - входы: `port/input/vk/{VkSendMessageInputPort,VkProfileInputPort}.kt`.
- **Ключевые классы:** `MessageSendUsecase.kt:15-44` (peer_id/message/forward/random_id),
  `VkProfileUsecase.kt:9-21`, `SendVkMessageCommandImpl.kt:8-19`, `VkSendMessageOutputAdapter.kt:12-29`
  (ошибка VK → `VkException`), `GetProfileAdapter.kt:12-73` (VK `users.get`/`groups.getById`; группы
  хранятся с отрицательным id), `VkClient.kt:12-39` (`configKey = "vk"`, `v=${vk.api.version}`,
  `disable_mentions`, `dont_parse_links`).
- **Сборка:** `share`, REST/REST client, fault-tolerance, RabbitMQ (`src/vk-facade/build.gradle.kts:13-23`).

## `infrastructure` — брокер и декораторы

- **Ответственность:** диспетчер RabbitMQ, retry-лестница и финальный DLQ, публикация событий, logging
  декораторы, Jackson-конфигурация.
- **Публичный API:**
  - MQ-вход: `@Incoming("all-events-queue")` — `EventDispatcher.kt:63-84`;
  - реализация `PublishEventOutputPort` — `PublishEventOutputAdapter.kt:15-33` (JSON + routing key = `Event.name`);
  - CDI-декораторы: `CommandLoggingDecorator.kt:12-28`, `UsecaseLoggingDecorator.kt:12-28`.
- **Ключевые классы:** `EventDispatcher` (карта `Event.name → EventProcessor`, `@RunOnVirtualThread`,
  retry по заголовку `x-retry-count`, `:90-111`), `DlqConfig.kt:6-16` (`dlq.levels[]`),
  `MapperConfig.kt:9-18` (Kotlin-модуль Jackson).
- **Сборка:** `share`, Quarkus arc/jackson/messaging-rabbitmq/logging-json, SmallRye RabbitMQ
  (`src/infrastructure/build.gradle.kts:13-26`).

## `app` — деплой и composition root

- **Ответственность:** единственный Quarkus-деплой, runtime-конфигурация, интеграционные тесты.
- **Публичный API:** `@QuarkusMain` — `app/Application.kt:6-12`; конфиги
  `src/app/src/main/resources/{application.yml,application-queues.yml}`.
- **Сборка:** все 6 бизнес-модулей + share (`src/app/build.gradle.kts:16-22`), deployable-расширения
  (`:25-36`).

## `testing/test-fixtures`, `testing/test-common`, `testing/arch-tests`

- **`test-fixtures`** — общие fake-объекты и провайдеры данных:
  `testfixtures/adapter/output/vk/FakeVkClient.kt:9-41`, `.../adapter/output/mq/FakePublishEventOutputPort.kt:8-16`,
  `.../command/vkFacade/FakePublishEventCommand.kt:8-16`, `.../domain/FakeVoProvider.kt:14-62`.
  Модуль зависит от `share` и `vk-facade`, но подключается только в test-конфигурациях.
- **`test-common`** — общий тестовый `application.yaml` (`vk.*`, `vane.url`, DevServices off).
- **`arch-tests`** — `ModuleDependenciesTest` (модуль видит только share), `LayerDependenciesTest` (L1–L5),
  `GradleDependenciesTest` (allowlist, 12 build-файлов, testing-модули только в тестах). Подключает все
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
| `MESSAGE_REQUIRE_ANSWER` | processor (`MessageReceivedUsecaseInput.kt:35-42`) | ai (`MessageRequireAnswerEventProcessor.kt:12-22`) |
| `ANSWER_MESSAGE` | ai (`GenerateAnswerUsecase.kt:28-38`) | vk-facade (`SendMessageEventProcessor.kt:12-22`) |
| `SUMMARY_READY` | persistence (`PersistenceDataUsecase.kt:132-144`) | vk-facade (`SummaryReadyEventProcessor.kt:18-57`) |

### In-process порты

| Порт (`share`) | Реализация | Потребитель |
|---|---|---|
| 9 `port/output/persistence/*` | `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt:17-144` | `ai` (`GenerateSummaryCommandImpl.kt:7-12`, `ConversationContextRestAdapter.kt:22-26`) |
| `GetUserProfilesBatchPort`, `GetGroupProfilesBatchPort` | `vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36` | `persistence` (`FetchUserProfileCommandImpl.kt:20-21`) |

Проводка валидируется ArC на этапе сборки деплоя и полным интеграционным тестом
(`VkWebhookIntegrationTest.kt:42-177`).

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
