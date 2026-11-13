# Архитектурные паттерны

Паттерн здесь — ответ на конкретную силу, а не украшение. Ниже только то, что есть в коде, с указанием
места и проблемы, которую паттерн решает. Отдельный раздел — паттерны, которых в коде **нет**, чтобы их не
приписали проекту.

## Ports & Adapters (гексагональная архитектура)

- **Проблема:** бизнес-логика не должна зависеть от Quarkus, PostgreSQL, RabbitMQ и VK API; тест должен
  заменять внешний мир фейком.
- **Где:** контракты — `share/port/` ([`Port.kt:3-9`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/Port.kt#L3-L9), [`InputPort.kt:7-13`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/input/InputPort.kt#L7-L13), [`OutputPort.kt:7-13`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/OutputPort.kt#L7-L13)), реализации —
  `adapter/input/*` и `adapter/output/*` в каждом модуле.
- **Форма:** `fun interface OutputPort<REQ, RESP>` с единственным `execute`; каждый порт — одна операция
  (`FindStoredMessagesBeforePort`, `SaveMessagePort`, …), а не «репозиторий на десять методов».
  Входной порт — `fun interface InputPort<REQ, RESP>` ([`ReceiveMessageInputPort.kt:9-13`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/port/input/ReceiveMessageInputPort.kt#L9-L13)).
- **Сила, которую снимает:** Dependency Inversion — внешний мир зависит от контракта, а не наоборот.
  Направление проверяют L1–L5 ([`LayerDependenciesTest.kt:51-116`](../../src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/LayerDependenciesTest.kt#L51-L116)) и L6 ([`ModuleDependenciesTest.kt:49-60`](../../src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/ModuleDependenciesTest.kt#L49-L60));
  имена типов — L7 ([`NamingConventionsTest.kt`](../../src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/NamingConventionsTest.kt)), см. «Имя типа — ответственность».

## Имя типа — ответственность, а не слой

- **Проблема:** класс `*Controller`, `*Service` или `*Repository` собирает всё, что относится к теме, —
  транспорт, правила, SQL, кеш, события; тест тянет всё окружение, SRP и ISP нарушаются молча.
- **Правило:** в production-коде `com.simarel.vkbot..` такие суффиксы запрещены ArchUnit-правилом L7
  ([`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/NamingConventionsTest.kt`](../../src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/NamingConventionsTest.kt));
  `./gradlew build` падает на новом классе. Имя отвечает на «что класс делает», а не «в каком он слое»:
  `XxxUsecase`, `XxxAdapter`, `XxxCommand`, `XxxProcessor`, `XxxTable`, `XxxReader`, `XxxWriter`,
  `XxxGenerator`, `XxxFormatter`, `XxxPolicy`, `XxxCalculator`, `XxxMapper`.
- **Где:** `PersistenceDataResource` (REST-адаптер) зовёт `PersistenceDataUsecase`; `MessageTable` —
  jOOQ-доступ к `messages`; `UserStatisticsReader`/`UserStatisticsWriter` — чтение/запись агрегатов;
  `UserAnswerGenerator`/`ConversationSummarizer`/`KnowledgeFactExtractor` — LangChain4j-интерфейсы;
  `VkProfileResource` — REST vk-facade.
- **Сила:** если для класса не находится имени без слова-слоя, у него нет одной ответственности — его
  делят на операции за однооперационными портами (`OutputPort<REQ, RESP>`), как требует ISP; fake в тесте
  остаётся один интерфейс, а не десять методов. Решение и таблица переименований —
  [ADR 2026-11-13-responsibility-type-names](../adr/2026-11-13-responsibility-type-names.md).

## In-process Adapter (вместо loopback-REST)

- **Проблема:** синхронный вызов между модулями одного деплоя не должен платить за HTTP-раундтрип, дубль
  контракта и сетевые таймауты ([ADR 2026-10-28-in-process-ports-instead-of-loopback-rest](../adr/2026-10-28-in-process-ports-instead-of-loopback-rest.md)).
- **Где:** [`persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt) (адаптеры всех
  `share`-портов persistence: сообщения, саммари, профили, позиция Long Poll, чаты и claim поздравлений) и
  [`vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt#L8-L36) (2 адаптера). Потребители видят только
  `share`-порты: [`ConversationContextRestAdapter.kt:22-26`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapter.kt#L22-L26), [`GenerateSummaryCommandImpl.kt:14-19`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImpl.kt#L14-L19),
  [`FetchUserProfileCommandImpl.kt:24-25`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt#L24-L25).
- **Сила:** Adapter + Dependency Inversion: поставщик реализует порт, потребитель о нём не знает.
  Тестовая замена — тот же порт с фейком (`PersistenceDataInProcessAdapterTest`).

## Фоновый poller (Bots Long Poll)

- **Проблема:** события VK нужно забирать самим, а не ждать HTTP-запроса; цикл не должен блокировать поток
  запуска и должен переживать сбои сети и брокера.
- **Где:** [`receiver/adapter/input/vk/LongPollRunner.kt`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/LongPollRunner.kt) — виртуальный поток, старт по `StartupEvent`,
  остановка по `ShutdownEvent`, паузы 5 с при сбое публикации и до 60 с при сетевых ошибках;
  включается ключом `vk.longpoll.enabled`.
- **Сила:** входящему транспорту не нужен публичный порт, а backoff изолирует отказ внешнего API от
  остального приложения.

## Command

- **Проблема:** атомарные действия нужны в нескольких сценариях и должны логироваться/декорироваться
  одинаково.
- **Где:** [`share/command/Command.kt:3-8`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/command/Command.kt#L3-L8); реализации — [`GenerateAnswerCommand.kt:30-74`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommand.kt#L30-L74),
  [`SendVkMessageCommandImpl.kt:8-19`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/command/sendVkMessage/SendVkMessageCommandImpl.kt#L8-L19), [`IsRequireAnswerCommand.kt:10-34`](../../src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt#L10-L34), [`PublishEventCommandImpl.kt:10-22`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/command/publishEvent/PublishEventCommandImpl.kt#L10-L22),
  [`FetchUserProfileCommand.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommand.kt), `GenerateSummaryCommand`, `ProcessSummaryCommand`, `RecognizeImagesCommand`,
  `VaneSearchCommand`, `PublishVkEventCommand`, `GetProfileCommand`, `CollectGreetingPlansCommand`,
  `GenerateGreetingTextCommand`.
- **Сила:** единый вход `execute(request)` — можно навесить декоратор, переиспользовать в use case,
  протестировать без каркаса.

## UseCase (реализация входящего порта)

- **Проблема:** сценарий координирует несколько команд и портов; он не должен жить в REST-ресурсе.
- **Где:** [`ReceiveMessageUsecase.kt:10-21`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecase.kt#L10-L21), [`SaveMessageUsecase.kt:22-68`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecase.kt#L22-L68),
  [`PersistenceDataUsecase.kt:62-302`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/PersistenceDataUsecase.kt#L62-L302), [`MessageReceivedUsecaseInput.kt:33-157`](../../src/processor/src/main/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInput.kt#L33-L157),
  [`GenerateAnswerUsecase.kt:16-41`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecase.kt#L16-L41), [`ProcessSummaryUsecase.kt:10-20`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/summary/ProcessSummaryUsecase.kt#L10-L20), [`GenerateSummaryUsecase.kt:10-24`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/summary/GenerateSummaryUsecase.kt#L10-L24),
  [`MessageSendUsecase.kt:15-44`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/usecase/message/MessageSendUsecase.kt#L15-L44), [`VkProfileUsecase.kt:9-21`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/usecase/profile/VkProfileUsecase.kt#L9-L21), [`RunDailyGreetingsUsecase.kt`](../../src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecase.kt).
- **Сила:** Information Expert — координация у того, кто владеет сценарием; адаптеры только маппят.

## Decorator (CDI `@Decorator`)

- **Проблема:** сквозное поведение (TRACE-логирование) не должно засорять бизнес-код.
- **Где:** [`CommandLoggingDecorator.kt:12-28`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/decorator/logging/CommandLoggingDecorator.kt#L12-L28) и [`UsecaseLoggingDecorator.kt:12-28`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/decorator/logging/UsecaseLoggingDecorator.kt#L12-L28) (TRACE-логи
  запрос/ответ).
- **Сила:** Protected Variations — граница защищена декоратором, а не копипастой в каждом адаптере.

## Value Object

- **Проблема:** `Long`/`String` без типа легко перепутать (`peerId` vs `fromId`), а невалидное значение
  должно нормализоваться в одном месте.
- **Где:** `share/domain/vo/` — [`MessageText.kt:3-12`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/MessageText.kt#L3-L12), [`PeerId.kt:5-17`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/PeerId.kt#L5-L17), [`FromId.kt:5-18`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/FromId.kt#L5-L18),
  [`Date.kt:6-11`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/Date.kt#L6-L11), [`ConversationMessageId.kt:5-13`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/ConversationMessageId.kt#L5-L13), [`GroupId.kt:5-10`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/GroupId.kt#L5-L10), [`Payload.kt:3-4`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/Payload.kt#L3-L4);
  в `persistence` — `MessageEntity`/`SummaryEntity` (простые носители строк БД).
- **Сила:** illegal state не пишется случайно; `FromId.isHuman()/isGroup()/isGroupChat()` инкапсулируют
  правило знака VK ([`FromId.kt:16-18`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/vo/FromId.kt#L16-L18)).

## Factory (companion `of`)

- **Проблема:** доменный объект создаётся из внешних nullable-данных; нормализация должна быть одна.
- **Где:** `Message.of(...)` ([`share/domain/model/Message.kt:21-38`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/Message.kt#L21-L38)), `VkUserProfile.of`,
  `VkGroupProfile.of`, `MessageText.of`, `PeerId.of`, `FromId.of`. В тестах ту же роль играют фабрики
  `*Provider` (конвенция имени — `Fake*Provider`, у persistence — `PersistenceData*Provider`): общие типы
  (`share`, `vk-facade`) — в `:src:testing:test-fixtures` ([`FakeVoProvider.kt:18-69`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt#L18-L69), [`FakeModelProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeModelProvider.kt),
  [`FakeVkFacadeProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/vkFacade/FakeVkFacadeProvider.kt)), модульные — в `<module>/src/testFixtures` ([`PersistenceDataRequestProvider.kt`](../../src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataRequestProvider.kt),
  [`PersistenceDataResponseProvider.kt`](../../src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataResponseProvider.kt), [`FakePublishVkEventCommandProvider.kt`](../../src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/FakePublishVkEventCommandProvider.kt)).
- **В тестах:** прямое создание `of(...)` и конструкторов запрещено для доменных моделей, VO и событий
  `share`, DTO под `**/adapter/output/**/dto/**` и request/response `**/port/**`/`**/command/**`, кроме
  теста самого типа (`<Type>Test.kt`, для вложенного контракта — `<Owner>Test.kt`). Данные берутся из
  этих фабрик, у каждого параметра `create*` есть дефолт: добавили поле в тип — добавили дефолтный
  параметр в фабрику, тесты не меняются. Нарушение ловит `TestDataFactoriesTest` в `:src:testing:arch-tests`.
- **Сила:** Single Responsibility — создание и валидация в одной точке; вызывающий код не повторяет `?: ""`.

## DMZ / Anti-Corruption Layer (граница VK)

- **Проблема:** формат обновлений Bots Long Poll и словарь VK (`message_new`, `reply_message`,
  `attachments`) не должны протекать в домен.
- **Где:** [`receiver/adapter/input/dto/MessageDto.kt:5-43`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/dto/MessageDto.kt#L5-L43) и `WallAttachmentDto`, парсинг —
  [`MessageMapper.kt:13-58`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/mapper/MessageMapper.kt#L13-L58); `jakarta.json.JsonObject` не покидает входной адаптер. На выходе — DTO VK API
  [`vkFacade/adapter/output/vk/dto/VkResponseDto.kt`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/dto/VkResponseDto.kt) и маппинг в [`GetProfileAdapter.kt:12-71`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/circuitbreaker/GetProfileAdapter.kt#L12-L71),
  DTO Vane — [`VaneSearchClient.kt:27-71`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchClient.kt#L27-L71) и маппинг в [`VaneSearchOutputAdapter.kt:30-59`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchOutputAdapter.kt#L30-L59).
- **Сила:** изменение внешнего API ломает один адаптер, а не домен.

## Publisher (события RabbitMQ)

- **Проблема:** приём событий VK не должен ждать AI, а AI и запись в БД не должны быть в его таймауте.
- **Где:** публикация — [`PublishEventOutputAdapter.kt:15-33`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/adapter/output/mq/PublishEventOutputAdapter.kt#L15-L33) (routing key = `Event.name`), потребление —
  [`EventDispatcher.kt:63-85`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt#L63-L85) (карта `Event.name → EventProcessor`), `@Incoming("all-events-queue")`.
  События — [`Event.kt:3-15`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/domain/Event.kt#L3-L15); payload'ы — `share/domain/event/`.
- **Сила:** развязка по времени (Protected Variations от недоступности AI/БД). Fan-out не заявляется:
  диспетчер держит по одному процессору на событие — новый обработчик добавляется новым событием/очередью.

## Retry with backoff (лестница) + Dead Letter

- **Проблема:** временный отказ (VK API, LLM, БД) не должен терять сообщение и не должен крутиться вечно.
- **Где:** [`EventDispatcher.kt:77-83`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt#L77-L83) ловит исключение обработки, `:91-113` перекладывает сообщение в
  очередь следующего уровня по заголовку `x-retry-count`; уровни и TTL —
  [`src/app/src/main/resources/application-queues.yml:1-32`](../../src/app/src/main/resources/application-queues.yml#L1-L32) (5s → 30s → 1m → 15m → 1h → `dlq-final`),
  [`DlqConfig.kt:6-16`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/config/DlqConfig.kt#L6-L16) связывает YAML с кодом.
- **Сила:** отказоустойчивость при at-least-once доставке; финальный DLQ требует ручного разбора.

## Table Data Gateway (collection semantics над jOOQ)

- **Проблема:** SQL/jOOQ не должен протекать в use case; операции чтения/записи должны выглядеть как работа
  с коллекцией.
- **Где:** табличные шлюзы `persistence/adapter/output/persistence/jooq/*Table.kt` (`MessageTable`,
  `SummaryTable`, `VkUserProfileTable`, `VkGroupProfileTable`, `UserKnowledgeTable`,
  `KnowledgeSummaryTable`, `LongPollPositionTable`, `GreetingLogTable`), чтение и запись агрегатов —
  [`UserStatisticsReader.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/UserStatisticsReader.kt)/[`UserStatisticsWriter.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/UserStatisticsWriter.kt); порты-адаптеры
  `persistence/adapter/output/persistence/{Save,Find,Insert,Update,Filter,Has}*.kt`;
  таблицы — [`jooq/Tables.kt:6-178`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/Tables.kt#L6-L178) (ручные константы, кодогенерация выключена).
- **Сила:** Dependency Inversion — бизнес-слой видит порт, а не DSLContext; замена хранилища — новый адаптер.

## Singleton / Dependency Injection (Quarkus CDI)

- **Проблема:** связывание реализаций с портами должно быть в одном месте и проверяться на сборке.
- **Где:** `@ApplicationScoped`/`@Singleton` на реализациях; composition root — деплой `:src:app`;
  явные продюсеры — [`JooqConfig.kt:11-20`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/config/JooqConfig.kt#L11-L20), [`MapperConfig.kt:9-18`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/config/MapperConfig.kt#L9-L18).
- **Сила:** ArC валидирует injection points на этапе сборки: забытая реализация порта валит `build`, а не прод.

## Чего в коде нет (не приписывать проекту)

| Паттерн | Почему нет |
|---|---|
| Event Sourcing / CQRS | События не сохраняются, event store отсутствует; чтение и запись — одни и те же модели |
| Circuit Breaker | Расширение fault-tolerance объявлено ([`src/vk-facade/build.gradle.kts:21`](../../src/vk-facade/build.gradle.kts#L21)), пакет `adapter/output/vk/circuitbreaker`, но аннотаций `@CircuitBreaker`/`@Retry` нет; блок `circuit-breaker.vk-profile` ([`application.yml:236-240`](../../src/app/src/main/resources/application.yml#L236-L240)) никем не читается |
| Strategy для AI-провайдеров | Провайдер один — OpenAI-совместимый API (в деплое llama.cpp, `llama-server`) через LangChain4j, ADR 2026-11-05-llm-provider-vllm-openai; выбор модели — конфиг, а не рантайм-стратегия |
| Chain of Responsibility | Валидация не выстроена в цепочку; правила решения — один `when` в [`IsRequireAnswerCommand.kt:22-33`](../../src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt#L22-L33) |
| Observer / fan-out | Один процессор на событие; множество подписчиков не поддерживается картой диспетчера |
| Saga / Outbox | Согласованность между БД и RabbitMQ не гарантируется; сообщение сохраняется и публикуется в одном use case без outbox ([`SaveMessageUsecase.kt:30-54`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecase.kt#L30-L54)) |
| Отдельный сервис/брокер на модуль | Нет требования независимого деплоя; см. ADR 2026-10-28-single-deployable-app-module, ADR 2026-10-28-module-boundaries-and-layer-rules |

## Как выбирать паттерн здесь

- Сначала проверить, есть ли уже порт/команда/адаптер под задачу — переиспользовать.
- Новая зависимость на внешний мир = новый `port/output` + адаптер, не прямой вызов из use case.
- Новое сквозное поведение = CDI-декоратор, не правка каждого use case.
- Новая фича = use case + команды в существующем модуле; новый модуль — только если у него свой владелец
  данных и он входит в allowlist (ADR 2026-10-28-module-boundaries-and-layer-rules).
