# Архитектурные паттерны

Паттерн здесь — ответ на конкретную силу, а не украшение. Ниже только то, что есть в коде, с указанием
места и проблемы, которую паттерн решает. Отдельный раздел — паттерны, которых в коде **нет**, чтобы их не
приписали проекту.

## Ports & Adapters (гексагональная архитектура)

- **Проблема:** бизнес-логика не должна зависеть от Quarkus, PostgreSQL, RabbitMQ и VK API; тест должен
  заменять внешний мир фейком.
- **Где:** контракты — `share/port/` (`Port.kt:3-9`, `InputPort.kt:7-13`, `OutputPort.kt:7-13`), реализации —
  `adapter/input/*` и `adapter/output/*` в каждом модуле.
- **Форма:** `fun interface OutputPort<REQ, RESP>` с единственным `execute`; каждый порт — одна операция
  (`FindStoredMessagesBeforePort`, `SaveMessagePort`, …), а не «репозиторий на десять методов».
  Входной порт — `fun interface InputPort<REQ, RESP>` (`ReceiveMessageInputPort.kt:10-15`).
- **Сила, которую снимает:** Dependency Inversion — внешний мир зависит от контракта, а не наоборот.
  Направление проверяют L1–L5 (`LayerDependenciesTest.kt:51-116`) и L6 (`ModuleDependenciesTest.kt:47-58`).

## In-process Adapter (вместо loopback-REST)

- **Проблема:** синхронный вызов между модулями одного деплоя не должен платить за HTTP-раундтрип, дубль
  контракта и сетевые таймауты ([ADR 0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md)).
- **Где:** `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt:17-144` (9 адаптеров) и
  `vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36` (2 адаптера). Потребители видят только
  `share`-порты: `ConversationContextRestAdapter.kt:22-26`, `GenerateSummaryCommandImpl.kt:7-12`,
  `FetchUserProfileCommandImpl.kt:20-21`.
- **Сила:** Adapter + Dependency Inversion: поставщик реализует порт, потребитель о нём не знает.
  Тестовая замена — тот же порт с фейком (`PersistenceDataInProcessAdapterTest`).

## Command

- **Проблема:** атомарные действия нужны в нескольких сценариях и должны логироваться/декорироваться
  одинаково.
- **Где:** `share/command/Command.kt:3-8`; реализации — `GenerateAnswerCommand.kt:14-48`,
  `SendVkMessageCommandImpl.kt:8-19`, `IsRequireAnswerCommand.kt:10-34`, `PublishEventCommandImpl.kt:10-22`,
  `FetchUserProfileCommand.kt`, `GenerateSummaryCommand`, `ProcessSummaryCommand`, `RecognizeImagesCommand`,
  `VaneSearchCommand`, `PublishVkEventCommand`, `GetProfileCommand`.
- **Сила:** единый вход `execute(request)` — можно навесить декоратор, переиспользовать в use case,
  протестировать без каркаса.

## UseCase (реализация входящего порта)

- **Проблема:** сценарий координирует несколько команд и портов; он не должен жить в контроллере.
- **Где:** `ReceiveMessageUsecase.kt:10-33`, `SaveMessageUsecase.kt:22-68`,
  `PersistenceDataUsecase.kt:34-207`, `MessageReceivedUsecaseInput.kt:15-45`,
  `GenerateAnswerUsecase.kt:16-41`, `ProcessSummaryUsecase.kt:10-20`, `GenerateSummaryUsecase.kt:10-24`,
  `MessageSendUsecase.kt:15-44`, `VkProfileUsecase.kt:9-21`.
- **Сила:** Information Expert — координация у того, кто владеет сценарием; адаптеры только маппят.

## Decorator (CDI `@Decorator`)

- **Проблема:** сквозное поведение (проверка секрета, TRACE-логирование) не должно засорять бизнес-код.
- **Где:** `VkMediatorRouterSecurityDecorator.kt:11-29` (секрет VK до вызова use case),
  `CommandLoggingDecorator.kt:12-28` и `UsecaseLoggingDecorator.kt:12-28` (TRACE-логи запрос/ответ).
- **Сила:** Protected Variations — граница защищена декоратором, а не копипастой в каждом адаптере.

## Value Object

- **Проблема:** `Long`/`String` без типа легко перепутать (`peerId` vs `fromId`), а невалидное значение
  должно нормализоваться в одном месте.
- **Где:** `share/domain/vo/` — `MessageText.kt:3-12`, `PeerId.kt:5-17`, `FromId.kt:5-18`,
  `Date.kt:6-11`, `ConversationMessageId.kt:5-13`, `GroupId.kt:5-10`, `Payload.kt:3-4`;
  в `persistence` — `MessageEntity`/`SummaryEntity` (простые носители строк БД).
- **Сила:** illegal state не пишется случайно; `FromId.isHuman()/isGroup()/isGroupChat()` инкапсулируют
  правило знака VK (`FromId.kt:16-18`).

## Factory (companion `of`)

- **Проблема:** доменный объект создаётся из внешних nullable-данных; нормализация должна быть одна.
- **Где:** `Message.of(...)` (`share/domain/model/Message.kt:21-38`), `VkUserProfile.of`,
  `VkGroupProfile.of`, `MessageText.of`, `PeerId.of`, `FromId.of`. В тестах ту же роль играют фабрики
  `*Provider` (конвенция имени — `Fake*Provider`, у persistence — `PersistenceData*Provider`): общие типы
  (`share`, `vk-facade`) — в `:src:testing:test-fixtures` (`FakeVoProvider.kt:18-69`, `FakeModelProvider.kt`,
  `FakeVkFacadeProvider.kt`), модульные — в `<module>/src/testFixtures` (`PersistenceDataRequestProvider.kt`,
  `PersistenceDataResponseProvider.kt`, `FakePublishVkEventCommandProvider.kt`).
- **В тестах:** прямое создание `of(...)` и конструкторов запрещено для доменных моделей, VO и событий
  `share`, DTO под `**/adapter/output/**/dto/**` и request/response `**/port/**`/`**/command/**`, кроме
  теста самого типа (`<Type>Test.kt`, для вложенного контракта — `<Owner>Test.kt`). Данные берутся из
  этих фабрик, у каждого параметра `create*` есть дефолт: добавили поле в тип — добавили дефолтный
  параметр в фабрику, тесты не меняются. Нарушение ловит `TestDataFactoriesTest` в `:src:testing:arch-tests`.
- **Сила:** Single Responsibility — создание и валидация в одной точке; вызывающий код не повторяет `?: ""`.

## DMZ / Anti-Corruption Layer (граница VK)

- **Проблема:** формат VK Callback и словарь VK (`message_new`, `reply_message`, `attachments`) не должны
  протекать в домен.
- **Где:** `receiver/adapter/input/dto/MessageDto.kt:5-43` и `WallAttachmentDto`, парсинг —
  `MessageMapper.kt:13-58`; `jakarta.json.JsonObject` не покидает входной адаптер. На выходе — DTO VK API
  `vkFacade/adapter/output/vk/dto/VkResponseDto.kt` и маппинг в `GetProfileAdapter.kt:12-71`,
  DTO Vane — `VaneSearchClient.kt:27-71` и маппинг в `VaneSearchOutputAdapter.kt:30-59`.
- **Сила:** изменение внешнего API ломает один адаптер, а не домен.

## Publisher (события RabbitMQ)

- **Проблема:** вебхук VK должен отвечать быстро; AI и запись в БД не должны быть в его таймауте.
- **Где:** публикация — `PublishEventOutputAdapter.kt:15-33` (routing key = `Event.name`), потребление —
  `EventDispatcher.kt:63-84` (карта `Event.name → EventProcessor`), `@Incoming("all-events-queue")`.
  События — `Event.kt:3-10`; payload'ы — `share/domain/event/`.
- **Сила:** развязка по времени (Protected Variations от недоступности AI/БД). Fan-out не заявляется:
  диспетчер держит по одному процессору на событие — новый обработчик добавляется новым событием/очередью.

## Retry with backoff (лестница) + Dead Letter

- **Проблема:** временный отказ (VK API, LLM, БД) не должен терять сообщение и не должен крутиться вечно.
- **Где:** `EventDispatcher.kt:76-82` ловит исключение обработки, `:90-111` перекладывает сообщение в
  очередь следующего уровня по заголовку `x-retry-count`; уровни и TTL —
  `src/app/src/main/resources/application-queues.yml:1-32` (5s → 30s → 1m → 15m → 1h → `dlq-final`),
  `DlqConfig.kt:6-16` связывает YAML с кодом.
- **Сила:** отказоустойчивость при at-least-once доставке; финальный DLQ требует ручного разбора.

## Repository (collection semantics над jOOQ)

- **Проблема:** SQL/jOOQ не должен протекать в use case; операции чтения/записи должны выглядеть как работа
  с коллекцией.
- **Где:** jOOQ-репозитории `persistence/adapter/output/persistence/jooq/Jooq{Message,Summary,VkUserProfile,VkGroupProfile}Repository.kt`
  и порты-адаптеры `persistence/adapter/output/persistence/{Save,Find,Insert,Update,Filter,Has}*.kt`;
  таблицы — `jooq/Tables.kt:6-57` (ручные константы, кодогенерация выключена).
- **Сила:** Dependency Inversion — бизнес-слой видит порт, а не DSLContext; замена хранилища — новый адаптер.

## Singleton / Dependency Injection (Quarkus CDI)

- **Проблема:** связывание реализаций с портами должно быть в одном месте и проверяться на сборке.
- **Где:** `@ApplicationScoped`/`@Singleton` на реализациях; composition root — деплой `:src:app`;
  явные продюсеры — `UsecaseConfiguration.kt:12-25`, `ConstantConfiguration.kt:9-15`,
  `JooqConfig.kt:11-20`, `MapperConfig.kt:9-18`.
- **Сила:** ArC валидирует injection points на этапе сборки: забытая реализация порта валит `build`, а не прод.

## Чего в коде нет (не приписывать проекту)

| Паттерн | Почему нет |
|---|---|
| Event Sourcing / CQRS | События не сохраняются, event store отсутствует; чтение и запись — одни и те же модели |
| Circuit Breaker | Расширение fault-tolerance объявлено (`src/vk-facade/build.gradle.kts:21`), пакет `adapter/output/vk/circuitbreaker`, но аннотаций `@CircuitBreaker`/`@Retry` нет; блок `circuit-breaker.vk-profile` (`application.yml:174-179`) никем не читается |
| Strategy для AI-провайдеров | Провайдер один — Ollama через LangChain4j; выбор модели — конфиг, а не рантайм-стратегия |
| Chain of Responsibility | Валидация не выстроена в цепочку; правила решения — один `when` в `IsRequireAnswerCommand.kt:22-33` |
| Observer / fan-out | Один процессор на событие; множество подписчиков не поддерживается картой диспетчера |
| Saga / Outbox | Согласованность между БД и RabbitMQ не гарантируется; сообщение сохраняется и публикуется в одном use case без outbox (`SaveMessageUsecase.kt:28-51`) |
| Отдельный сервис/брокер на модуль | Нет требования независимого деплоя; см. ADR 0002/0003 |

## Как выбирать паттерн здесь

- Сначала проверить, есть ли уже порт/команда/адаптер под задачу — переиспользовать.
- Новая зависимость на внешний мир = новый `port/output` + адаптер, не прямой вызов из use case.
- Новое сквозное поведение = CDI-декоратор, не правка каждого use case.
- Новая фича = use case + команды в существующем модуле; новый модуль — только если у него свой владелец
  данных и он входит в allowlist (ADR 0003).
