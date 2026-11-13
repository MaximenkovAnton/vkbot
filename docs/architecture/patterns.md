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
  Входной порт — `fun interface InputPort<REQ, RESP>` (`ReceiveMessageInputPort.kt:9-13`).
- **Сила, которую снимает:** Dependency Inversion — внешний мир зависит от контракта, а не наоборот.
  Направление проверяют L1–L5 (`LayerDependenciesTest.kt:51-116`) и L6 (`ModuleDependenciesTest.kt:49-60`);
  имена типов — L7 (`NamingConventionsTest.kt`), см. «Имя типа — ответственность».

## Имя типа — ответственность, а не слой

- **Проблема:** класс `*Controller`, `*Service` или `*Repository` собирает всё, что относится к теме, —
  транспорт, правила, SQL, кеш, события; тест тянет всё окружение, SRP и ISP нарушаются молча.
- **Правило:** в production-коде `com.simarel.vkbot..` такие суффиксы запрещены ArchUnit-правилом L7
  (`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/NamingConventionsTest.kt`);
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
- **Где:** `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` (адаптеры всех
  `share`-портов persistence: сообщения, саммари, профили, позиция Long Poll, чаты и claim поздравлений) и
  `vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36` (2 адаптера). Потребители видят только
  `share`-порты: `ConversationContextRestAdapter.kt:22-26`, `GenerateSummaryCommandImpl.kt:7-12`,
  `FetchUserProfileCommandImpl.kt:20-21`.
- **Сила:** Adapter + Dependency Inversion: поставщик реализует порт, потребитель о нём не знает.
  Тестовая замена — тот же порт с фейком (`PersistenceDataInProcessAdapterTest`).

## Фоновый poller (Bots Long Poll)

- **Проблема:** события VK нужно забирать самим, а не ждать HTTP-запроса; цикл не должен блокировать поток
  запуска и должен переживать сбои сети и брокера.
- **Где:** `receiver/adapter/input/vk/LongPollRunner.kt` — виртуальный поток, старт по `StartupEvent`,
  остановка по `ShutdownEvent`, паузы 5 с при сбое публикации и до 60 с при сетевых ошибках;
  включается ключом `vk.longpoll.enabled`.
- **Сила:** входящему транспорту не нужен публичный порт, а backoff изолирует отказ внешнего API от
  остального приложения.

## Command

- **Проблема:** атомарные действия нужны в нескольких сценариях и должны логироваться/декорироваться
  одинаково.
- **Где:** `share/command/Command.kt:3-8`; реализации — `GenerateAnswerCommand.kt:14-48`,
  `SendVkMessageCommandImpl.kt:8-19`, `IsRequireAnswerCommand.kt:10-34`, `PublishEventCommandImpl.kt:10-22`,
  `FetchUserProfileCommand.kt`, `GenerateSummaryCommand`, `ProcessSummaryCommand`, `RecognizeImagesCommand`,
  `VaneSearchCommand`, `PublishVkEventCommand`, `GetProfileCommand`, `CollectGreetingPlansCommand`,
  `GenerateGreetingTextCommand`.
- **Сила:** единый вход `execute(request)` — можно навесить декоратор, переиспользовать в use case,
  протестировать без каркаса.

## UseCase (реализация входящего порта)

- **Проблема:** сценарий координирует несколько команд и портов; он не должен жить в REST-ресурсе.
- **Где:** `ReceiveMessageUsecase.kt:10-21`, `SaveMessageUsecase.kt:22-68`,
  `PersistenceDataUsecase.kt:41-302`, `MessageReceivedUsecaseInput.kt:33-157`,
  `GenerateAnswerUsecase.kt:16-41`, `ProcessSummaryUsecase.kt:10-20`, `GenerateSummaryUsecase.kt:10-24`,
  `MessageSendUsecase.kt:15-44`, `VkProfileUsecase.kt:9-21`, `RunDailyGreetingsUsecase.kt`.
- **Сила:** Information Expert — координация у того, кто владеет сценарием; адаптеры только маппят.

## Decorator (CDI `@Decorator`)

- **Проблема:** сквозное поведение (TRACE-логирование) не должно засорять бизнес-код.
- **Где:** `CommandLoggingDecorator.kt:12-28` и `UsecaseLoggingDecorator.kt:12-28` (TRACE-логи
  запрос/ответ).
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

- **Проблема:** формат обновлений Bots Long Poll и словарь VK (`message_new`, `reply_message`,
  `attachments`) не должны протекать в домен.
- **Где:** `receiver/adapter/input/dto/MessageDto.kt:5-43` и `WallAttachmentDto`, парсинг —
  `MessageMapper.kt:13-58`; `jakarta.json.JsonObject` не покидает входной адаптер. На выходе — DTO VK API
  `vkFacade/adapter/output/vk/dto/VkResponseDto.kt` и маппинг в `GetProfileAdapter.kt:12-71`,
  DTO Vane — `VaneSearchClient.kt:27-71` и маппинг в `VaneSearchOutputAdapter.kt:30-59`.
- **Сила:** изменение внешнего API ломает один адаптер, а не домен.

## Publisher (события RabbitMQ)

- **Проблема:** приём событий VK не должен ждать AI, а AI и запись в БД не должны быть в его таймауте.
- **Где:** публикация — `PublishEventOutputAdapter.kt:15-33` (routing key = `Event.name`), потребление —
  `EventDispatcher.kt:63-85` (карта `Event.name → EventProcessor`), `@Incoming("all-events-queue")`.
  События — `Event.kt:3-10`; payload'ы — `share/domain/event/`.
- **Сила:** развязка по времени (Protected Variations от недоступности AI/БД). Fan-out не заявляется:
  диспетчер держит по одному процессору на событие — новый обработчик добавляется новым событием/очередью.

## Retry with backoff (лестница) + Dead Letter

- **Проблема:** временный отказ (VK API, LLM, БД) не должен терять сообщение и не должен крутиться вечно.
- **Где:** `EventDispatcher.kt:77-83` ловит исключение обработки, `:91-113` перекладывает сообщение в
  очередь следующего уровня по заголовку `x-retry-count`; уровни и TTL —
  `src/app/src/main/resources/application-queues.yml:1-32` (5s → 30s → 1m → 15m → 1h → `dlq-final`),
  `DlqConfig.kt:6-16` связывает YAML с кодом.
- **Сила:** отказоустойчивость при at-least-once доставке; финальный DLQ требует ручного разбора.

## Table Data Gateway (collection semantics над jOOQ)

- **Проблема:** SQL/jOOQ не должен протекать в use case; операции чтения/записи должны выглядеть как работа
  с коллекцией.
- **Где:** табличные шлюзы `persistence/adapter/output/persistence/jooq/*Table.kt` (`MessageTable`,
  `SummaryTable`, `VkUserProfileTable`, `VkGroupProfileTable`, `UserKnowledgeTable`,
  `KnowledgeSummaryTable`, `LongPollPositionTable`, `GreetingLogTable`), чтение и запись агрегатов —
  `UserStatisticsReader.kt`/`UserStatisticsWriter.kt`; порты-адаптеры
  `persistence/adapter/output/persistence/{Save,Find,Insert,Update,Filter,Has}*.kt`;
  таблицы — `jooq/Tables.kt:6-82` (ручные константы, кодогенерация выключена).
- **Сила:** Dependency Inversion — бизнес-слой видит порт, а не DSLContext; замена хранилища — новый адаптер.

## Singleton / Dependency Injection (Quarkus CDI)

- **Проблема:** связывание реализаций с портами должно быть в одном месте и проверяться на сборке.
- **Где:** `@ApplicationScoped`/`@Singleton` на реализациях; composition root — деплой `:src:app`;
  явные продюсеры — `JooqConfig.kt:11-20`, `MapperConfig.kt:9-18`.
- **Сила:** ArC валидирует injection points на этапе сборки: забытая реализация порта валит `build`, а не прод.

## Чего в коде нет (не приписывать проекту)

| Паттерн | Почему нет |
|---|---|
| Event Sourcing / CQRS | События не сохраняются, event store отсутствует; чтение и запись — одни и те же модели |
| Circuit Breaker | Расширение fault-tolerance объявлено (`src/vk-facade/build.gradle.kts:21`), пакет `adapter/output/vk/circuitbreaker`, но аннотаций `@CircuitBreaker`/`@Retry` нет; блок `circuit-breaker.vk-profile` (`application.yml:193-198`) никем не читается |
| Strategy для AI-провайдеров | Провайдер один — OpenAI-совместимый API (vLLM) через LangChain4j, ADR 2026-11-05-llm-provider-vllm-openai; выбор модели — конфиг, а не рантайм-стратегия |
| Chain of Responsibility | Валидация не выстроена в цепочку; правила решения — один `when` в `IsRequireAnswerCommand.kt:22-33` |
| Observer / fan-out | Один процессор на событие; множество подписчиков не поддерживается картой диспетчера |
| Saga / Outbox | Согласованность между БД и RabbitMQ не гарантируется; сообщение сохраняется и публикуется в одном use case без outbox (`SaveMessageUsecase.kt:28-51`) |
| Отдельный сервис/брокер на модуль | Нет требования независимого деплоя; см. ADR 2026-10-28-single-deployable-app-module, ADR 2026-10-28-module-boundaries-and-layer-rules |

## Как выбирать паттерн здесь

- Сначала проверить, есть ли уже порт/команда/адаптер под задачу — переиспользовать.
- Новая зависимость на внешний мир = новый `port/output` + адаптер, не прямой вызов из use case.
- Новое сквозное поведение = CDI-декоратор, не правка каждого use case.
- Новая фича = use case + команды в существующем модуле; новый модуль — только если у него свой владелец
  данных и он входит в allowlist (ADR 2026-10-28-module-boundaries-and-layer-rules).
