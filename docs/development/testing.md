# Тестирование

Стратегия: **Fake Objects вместо моков**, unit-тесты без каркаса, интеграционные тесты через
`@QuarkusTest` с DevServices и WireMock, архитектурные правила как тесты. Всё, что ниже, — из кода и
прогонов этого прохода.

## Команды

```bash
./gradlew test                                  # все тесты (нужен Docker для app-тестов)
./gradlew :src:ai:test                          # юнит-тесты модуля ai (без Docker)
./gradlew :src:ai:test --tests "*GenerateAnswerUsecaseTest*"  # один класс ai (в своём модуле)
./gradlew :src:processor:test --tests "*MessageReceivedUsecaseInputTest*"  # один класс (в своём модуле)
./gradlew :src:receiver:test --tests "*LongPollRunnerTest*"  # цикл Long Poll на fake-портах (без Docker)
./gradlew :src:greetings:test                   # юнит-тесты поздравлений: правила, оркестрация, спан
./gradlew :src:app:test --tests "*AiIntegrationTest*"  # Quarkus-проводка ai (нужен Docker)
./gradlew :src:app:test --tests "*DailyGreetingsIntegrationTest*"  # поздравления на WireMock + PostgreSQL (нужен Docker)
./gradlew :src:testing:arch-tests:test          # архитектурные правила и сторож тестовых данных (26 тестов)
./gradlew coverageReport                        # агрегированный HTML+XML отчёт
./gradlew coverageVerification                  # порог покрытия (входит в ./gradlew build)
```

Фильтр `--tests` применяется ко всем test-задачам: корневой `./gradlew test --tests "*X*"` падает на
модуле, где совпадений нет (`No tests found for given includes` в `:src:app`), поэтому одиночный тест
запускается в своём модуле.

Полный гейт этого дерева — `./gradlew build` (компиляция, тесты, detekt, ktlintCheck,
`coverageVerification`, `capabilitiesCheck`, `:src:app:quarkusBuild`). Дерево покрывает полный профиль VK,
историю изменений и материализованную статистику (ADR 2026-11-10-vk-profile-history-and-statistics,
ADR 2026-11-10-user-statistics-materialization) и доработку фильтра взлома ИИ
(ADR 2026-11-10-jailbreak-rubric-and-threshold). Состав тестов по классам — в таблице «Где лежат тесты»;
число классов и тестов печатает сам прогон (`./gradlew test`), отдельного сводного числа здесь не держим,
чтобы инвентарь не расходился с прогоном.

`./gradlew capabilitiesCheck` запускает [`scripts/check-capabilities.sh`](../../scripts/check-capabilities.sh) через bash; тот же скрипт
выполняется в CI-джобе `docs`.

[`src/ai/src/test/.../adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt) —
известная точка: из-за длинного имени Gradle пишет его отчёт в `build/test-results` с префиксом
`__TES-…`, поэтому число файлов `TEST-*.xml` занижает итог. Считать тесты по `testsuite` из всех XML
(в свежем прогоне 2026-11-13 — 37 классов, 304 теста в `ai`) и сверять со сводкой Gradle.

## Где лежат тесты

| Модуль | Классы и тесты (свежий прогон 2026-11-13) |
|---|---|
| `app` | 25 классов / 130 тестов: `InitTest`, `HealthTest`, `integration/*` — Long Poll (`VkLongPollIntegrationTest`, `LongPollPositionIntegrationTest`), `AiIntegrationTest`, decision-модель, «что нового», досье «профиль», знания (извлечение, хранение, чтение, сводки), статистика, поздравления; инфраструктура — `WireMockTestResource`, `IntegrationTestProfile`, `LongPollPositionTestProfile`, `ProfileStorageTestProfile` |
| `ai` | 37 классов / 304 теста: usecase/command (answer, summary, news, profile, knowledge, vane), домен (`NewsPeriodParserTest` — 43, `ProfileNameMatcherTest`, `UserKnowledgeValidatorTest`, форматтеры), адаптеры answer/news/summarization/greeting/profile/knowledge/Vane, event-процессоры |
| `receiver` | 5 классов / 22 теста: `LongPollRunnerTest` (14), `VkLongPollOutputAdapterTest` (2), `VkLongPollCheckOutputAdapterTest` (3), `ReceiveMessageUsecaseTest` (2), `PublishVkEventCommandImplTest` (1) |
| `persistence` | 16 классов / 93 теста: in-process адаптер, профили/история/статистика, знания (`KnowledgeTemporalPolicyTest`, `FactConfidenceCalculatorTest`, `UserKnowledgeReadModelTest`, `UserKnowledgeMigrationChangesetTest`), backfill, миграции 008/009/010, `BirthDateMatcherTest` (17), `FetchUserProfileCommandImplTest` (8) |
| `processor` | 9 классов / 138 тестов: `MessageReceivedUsecaseInputTest` (39), `DecisionModelOutputAdapterTest` (53 — ответы и ошибки decision-модели, рубрика взлома и порог), `IsSpamCommandTest`, `IsJailbreakCommandTest`, `ResolveMessageIntentCommandTest`, `BuildHelpTextCommandTest`, `DecisionAnalysisTest`, `MessageTextNormalizerTest`, `RequestKnowledgeExtractionCommandImplTest` |
| `vk-facade` | 11 классов / 24 теста: профили VK (`VkGetUserProfilesBatchAdapterTest`, `VkUserProfileMapperTest`, `VkProfileInProcessAdapterTest`), отправка сообщений (`VkSendMessageOutputAdapterTest`, `MessageSendUsecaseTest`, `PlainMessageSendUsecaseTest`, `SendVkMessageCommandImplTest`, `RandomIdTest`), `SummaryReadyEventProcessorTest`, `SendGreetingMessageInProcessAdapterTest` |
| `greetings` | 7 классов / 59 тестов: `HolidaysTest` (7), `BirthdayDatesTest` (4), `NameFormatterTest` (5), `CollectGreetingPlansCommandTest` (18), `GenerateGreetingTextCommandTest` (13), `RunDailyGreetingsUsecaseTest` (8), `DailyGreetingRunnerTest` (4) |
| `share` | 3 класса / 9 тестов: `MessageTextTest`, `ConversationMessageIdTest`, `KnowledgeEnumLiteralsTest` |
| `infrastructure` | 1 класс / 2 теста: `CommandLoggingDecoratorTest` |
| `testing/arch-tests` | 7 классов / 26 тестов: `ModuleDependenciesTest` (9), `LayerDependenciesTest` (5), `GradleDependenciesTest` (5), `TestDataFactoriesTest` (2), `DetektExcludesPathTest` (1), `NamingConventionsTest` (2 — L7: `*Controller`/`*Service`/`*Repository` запрещены), `ExceptionHandlingTest` (2 — бросаются только `VkBotAppException` и наследники; `require` запрещён) |

Тестовые ресурсы: [`src/app/src/test/resources/application.yaml`](../../src/app/src/test/resources/application.yaml) (базовый профиль `@QuarkusTest`),
[`src/app/src/test/resources/application-integration-test.yaml`](../../src/app/src/test/resources/application-integration-test.yaml) (профиль `integration-test`), [`src/app/src/test/resources/application.yml`](../../src/app/src/test/resources/application.yml) (`%test`-настройки),
[`src/testing/test-common/src/main/resources/application.yaml`](../../src/testing/test-common/src/main/resources/application.yaml) (общие значения для модульных тестов).

## Fake Objects

Ни одного mocking-фреймворка в сборке нет. Fake реализует тот же интерфейс, записывает вызовы в
`ConcurrentLinkedQueue` и возвращает реалистичный ответ.

```kotlin
class FakeVkClient(
    val vkResponseDto: VkResponseDto? = null,
    val vkUsersResponseDto: VkUsersResponseDto? = null,
    val vkGroupsResponseDto: VkGroupsResponseDto? = null,
) : VkClient {
    val sendMessageParameterCalls = ConcurrentLinkedQueue<SendMessageParameter>()
    override fun sendMessage(peerId: Long, message: String, rand: Int, forwardMessages: String?): VkResponseDto {
        sendMessageParameterCalls.add(SendMessageParameter(peerId, message, rand))
        return vkResponseDto ?: VkResponseDto(error = null)
    }
}
```

См. [`src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/adapter/output/vk/FakeVkClient.kt:9-41`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/adapter/output/vk/FakeVkClient.kt#L9-L41),
`.../command/vkFacade/FakePublishEventCommand.kt:8-16`,
`.../adapter/output/mq/FakePublishEventOutputPort.kt:8-16`.

### Раскладка fake-объектов и фабрик

```
src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/
├── adapter/
│   ├── input/FakeEventProcessor.kt
│   └── output/
│       ├── mq/FakePublishEventOutputPort.kt
│       └── vk/FakeVkClient.kt
├── command/
│   ├── FakeCommand.kt, FakeCommandRequest.kt, FakeCommandResponse.kt, FakeCommandProvider.kt
│   └── vkFacade/
│       ├── FakePublishEventCommand.kt, FakePublishEventCommandProvider.kt
│       └── FakeSendVkMessageCommand.kt, FakeSendVkMessageCommandProvider.kt
├── domain/
│   ├── FakeEventProvider.kt
│   ├── FakeMessageProvider.kt
│   ├── FakeModelProvider.kt
│   └── FakeVoProvider.kt
├── objectProvider/ObjectMapperProvider.kt
├── port/
│   ├── FakeGreetingPortProvider.kt
│   ├── FakePersistencePortProvider.kt
│   ├── FakeProfilePortProvider.kt
│   └── output/vk/FakeVkSendMessageOutputProvider.kt
└── vkFacade/FakeVkFacadeProvider.kt

src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/  # PersistenceDataRequestProvider,
  # PersistenceDataResponseProvider, ProfileActualizationPortProvider
src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/   # FakeMessageMapper,
  # FakePublishVkEventCommand, FakePublishVkEventCommandProvider, FakeVkUpdateInputPortProvider,
  # FakeLongPollPortProvider, port/{FakeGetLongPollServerPort, FakeCheckLongPollPort,
  # FakeFindLongPollTsPort, FakeSaveLongPollTsPort, FakeReceiveMessageInputPort}
src/processor/src/testFixtures/kotlin/com/simarel/vkbot/processor/testfixtures/  # FakeIsRequireAnswerCommand,
  # FakeMessageNewInputPortProvider, FakeMessageReceivedInputPort
src/ai/src/testFixtures/kotlin/com/simarel/vkbot/ai/testfixtures/   # FakeAnswerProvider, FakeSummaryProvider,
  # FakeVaneProvider, FakeConversationContextPortProvider
src/greetings/src/testFixtures/kotlin/com/simarel/vkbot/greetings/testfixtures/  # FakeGreetingProvider
src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/test/  # Fakes.kt (fake-порты, stateful claim),
  # SystemLoggerCapture.kt (JUL-handler для WARN/ERROR)
```

Модульные `testFixtures` подключаются только в test-конфигурациях — это проверяет
`GradleDependenciesTest`. Общий набор подключается через `testFixtures(project(":src:testing:test-fixtures"))`.

### Провайдеры тестовых данных

Правило: **тестовые данные создаются только фабриками `*Provider`**; конвенция имени — `Fake*Provider`,
у persistence-фабрик — `PersistenceData*Provider`.

1. В `src/*/src/test/**` запрещено прямое создание (`Type(...)`, `Type.of(...)`) типов:
   - доменных моделей, VO и событий `com.simarel.vkbot.share.domain.{model,vo,event}`;
   - DTO под `**/adapter/output/**/dto/**`;
   - request/response под `**/port/**` и `**/command/**`, включая вложенные типы контрактов.
2. Данные берутся из фабрики `*Provider`: общие типы (`share`, `vk-facade`) — в `:src:testing:test-fixtures`,
   типы модуля — в его `<module>/src/testFixtures` (`ai`, `persistence`, `receiver`, `processor`, `greetings`). Фабрика —
   единственное место, где эти типы создаются напрямую.
3. У каждого параметра фабричного метода `create*` есть реалистичный дефолт; тест передаёт только поля,
   от которых зависит проверка.
4. Исключения: тест самого типа (`<Type>Test.kt`; для вложенного контракта — `<Owner>Test.kt`),
   исключения-ошибки и сырые JSON-тела интеграционных тестов.
5. Фейки-зависимости (`Fake*`, `Recording*`) по-прежнему создаются в тесте напрямую; общие фейки
   переиспользуются, а не копируются локально.
6. Нарушение ловит `TestDataFactoriesTest` в `:src:testing:arch-tests` (см. «Сторож правила»).

Фабрики (дерево — в «Раскладка fake-объектов и фабрик»):

- [`domain/FakeVoProvider.kt:18-69`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt#L18-L69) — единая точка создания доменных значений: `createDate`, `createFromId`,
  `createHumanFromId`, `createGroupFromId`, `createGroupChatFromId`, `createGroupId`, `createPeerId`,
  `createConversationMessageId`, `createMessageText`, `createMessage`, `createForwardedMessage`,
  `createMessageWithForwarded`. `FakeMessageProvider` — фабрика `Message` поверх него, `FakeEventProvider` —
  `createSummaryRequestedPayload`.
- [`domain/FakeModelProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeModelProvider.kt) — `createStoredMessage`, `createSummary`, `createVkUserProfile`,
  `createVkGroupProfile`, `createResponseMessage`.
- [`port/FakeProfilePortProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/port/FakeProfilePortProvider.kt) — `createGetUserProfilesBatchRequest`,
  `createGetGroupProfilesBatchRequest`.
- [`port/FakePersistencePortProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/port/FakePersistencePortProvider.kt) — 9 request-фабрик `share`-портов persistence
  (`createFindMessagesBeforeRequest`, `createFindMessagesBetweenRequest`, `createFindLastSummaryRequest`,
  `createHasPendingSummaryRequest`, `createCreatePendingSummaryRequest`, `createSaveCompletedSummaryRequest`,
  `createMarkSummaryFailedRequest`, `createFindUserProfilesByIdsRequest`,
  `createFindGroupProfilesByIdsRequest`).
- [`port/FakeGreetingPortProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/port/FakeGreetingPortProvider.kt) — request/response-фабрики шести `share`-портов поздравлений
  (`createFindUserProfilesByBirthDateRequest`, `createFindUserChatPeerIdsRequest`,
  `createFindGroupChatPeerIdsRequest`, `createClaimDailyGreetingRequest`, `createGenerateGreetingTextRequest`,
  `createSendGreetingMessageRequest` и соответствующие response).
- [`src/greetings/src/testFixtures/.../testfixtures/FakeGreetingProvider.kt`](../../src/greetings/src/testFixtures/kotlin/com/simarel/vkbot/greetings/testfixtures/FakeGreetingProvider.kt) — контракты модуля
  `daily-greetings`: `createGreetingPlan`, `createRunDailyGreetingsRequest/Response`,
  `createCollectGreetingPlansRequest/Response`, `createGenerateGreetingTextCommandRequest/Response`.
- [`vkFacade/FakeVkFacadeProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/vkFacade/FakeVkFacadeProvider.kt) — `createVkError`, `createVkResponseDto`, `createVkProfileRequest`,
  `createVkProfileResponse`, `createVkSendMessageInputRequest`.
- [`command/FakeCommandProvider.kt`](../../src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/FakeCommandProvider.kt) — `createRequest`, `createResponse`.
- `src/ai/src/testFixtures/.../ai/testfixtures/` — `FakeAnswerProvider` (message/answer, контекст и
  request/response команд и портов `ai-answer`), `FakeSummaryProvider` (саммаризация и summary-команды),
  `FakeVaneProvider` (порт и REST-клиент Vane, одноимённые типы разведены именами фабрик),
  `FakeConversationContextPortProvider` (ответы persistence-портов контекста).
- [`PersistenceDataRequestProvider.kt`](../../src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataRequestProvider.kt) и [`PersistenceDataResponseProvider.kt`](../../src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataResponseProvider.kt) в
  [`src/persistence/src/testFixtures`](../../src/persistence/src/testFixtures) — 9 фабрик `PersistenceDataRequest.*` и 7 фабрик
  `PersistenceDataResponse.*`.
- [`FakePublishVkEventCommandProvider.kt`](../../src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/FakePublishVkEventCommandProvider.kt) в [`src/receiver/src/testFixtures`](../../src/receiver/src/testFixtures) — `createRequest`.

Пример «добавили поле в модель»: в `Summary` появилось поле `language`:

1. в тип — новое поле (nullable или с дефолтом);
2. в `FakeModelProvider.createSummary` — параметр `language: String? = "ru"`;
3. тесты не меняются: они не передавали `language`, фабрика подставляет дефолт.

Так добавление поля, которое не влияет на проверки, стоит одной строки в фабрике, а не правки в каждом
тесте.

### Сторож правила

`TestDataFactoriesTest` ([`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/TestDataFactoriesTest.kt`](../../src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/TestDataFactoriesTest.kt))
— два теста:

| Тест | Что проверяет |
|---|---|
| `test sources create test data only through test data providers` | в `src/*/src/test/**/*.kt` нет `Type(...)`/`Type.of(...)` для типов из правила; исключение — тест самого типа (`<Type>Test.kt`, для вложенного контракта — `<Owner>Test.kt`); `src/testFixtures` не сканируются |
| `every factory method parameter has a default` | у каждого параметра каждого `fun create*` в `:src:testing:test-fixtures` и `<module>/src/testFixtures` есть `= <дефолт>` |

Падение называет `path:line` и тип или параметр:
`…:14: Message создаётся напрямую; используй Fake*Provider …`,
`…:5: параметр requiredValue функции createRequest без дефолта; …`. Негативные прогоны: временное
`Message.of(...)` в существующем тесте → `test sources create test data only through test data providers`
FAILED с `path:line`; временный обязательный параметр в фабрике → `every factory method parameter has a
default` FAILED с именем параметра. После отката оба теста PASSED.

Ограничение: скан текстовый — строковые литералы и комментарии не разбираются (имя типа в них даёт ложное
срабатывание), `copy(...)` не ловится, `Type` и `.of(` на разных строках тоже, пакеты вне перечисленных
выше не проверяются. Дефолт с оператором сравнения (`limit > 0`) сбивает угловую глубину разбора
параметров, аннотация после модификатора (`public @Suppress(...) data class`) не попадает в набор типов, а
без системного свойства `project.root` скан пуст (fail-open). Ложное срабатывание лечится фабрикой или
переименованием теста самого типа. Таск `:src:testing:arch-tests:test` не объявляет сканируемые тесты
входами, поэтому локальный инкрементальный прогон после правки чужого теста может быть `UP-TO-DATE`; CI с
чистого дерева выполняет сторож всегда.

## Как писать тесты

- **Именование:** backtick-предложение, описывающее проверку (`LongPollRunnerTest`, `ReceiveMessageUsecaseTest`).
  Стиль Given/When/Then — как в соседних тестах.
- **Unit-тест** — обычный JUnit 5 без Quarkus: собрать объект с fake-зависимостями, вызвать, проверить
  записи fake'а и результат ([`ReceiveMessageUsecaseTest.kt:12-41`](../../src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/usecase/ReceiveMessageUsecaseTest.kt#L12-L41)).
- **Адаптер in-process** — шаблон для новых адаптеров: записать запрос в `port.input` и проверить обратный
  маппинг ([`PersistenceDataInProcessAdapterTest.kt:31-53`](../../src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt#L31-L53), [`VkProfileInProcessAdapterTest.kt:16-34`](../../src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapterTest.kt#L16-L34)).
- **Fake с настраиваемым поведением** — параметр конструктора вместо `when(...)`:
  `FakeVkClient(vkResponseDto = ...)`.
- **Thread safety** — `ConcurrentLinkedQueue` для записанных вызовов, не `mutableListOf`.
- **Реалистичные данные** — возвращать осмысленный объект из провайдера, не `""`/`null`.

## Интеграционные тесты

`VkLongPollIntegrationTest` ([`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/VkLongPollIntegrationTest.kt)):

- `@QuarkusTest` + `@TestProfile(IntegrationTestProfile::class)` (профиль `integration-test`) +
  `@QuarkusTestResource(WireMockTestResource::class)`; опрос Bots Long Poll включён;
- DevServices поднимают PostgreSQL и RabbitMQ, поэтому **нужен Docker**; LangChain4j DevServices
  отключены, LLM отвечает WireMock-заглушкой `/v1/chat/completions`; VK API (`/method/users.get`,
  `/method/groups.getById`, `/method/messages.send`, `/method/groups.getLongPollServer`) тоже заглушается;
- WireMock-сценарий приёма: `groups.getLongPollServer` отдаёт `server` = базовый URL WireMock и `ts`,
  первый `a_check` возвращает одно обновление `message_new`, следующие — пустые; иначе одно и то же
  обновление доставлялось бы бесконечно. Тест сквозной: доставленное обновление доходит до
  `messages.send` с `peer_id` исходного сообщения (Awaitility, до 120 с), а `ts` сохраняется в
  `long_poll_position`;
- позиция переживает пачку: `ts` сохраняется после ответа `a_check`, и следующий запрос идёт с ним;
- `failed: 3`: заглушка видит новый `groups.getLongPollServer`, опрос идёт с `ts` из его ответа;
- неизвестный тип обновления пропускается, опрос продолжается с новым `ts`, `messages.send` не вызывается;
- `POST /vk/callback` → 404: HTTP-входа у `receiver` нет.

`AiIntegrationTest` ([`src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt)) — 8 тестов
реальной проводки `ai` в единственном деплое: инжектятся CDI-бины `GenerateAnswerOutputPort`,
`SummarizationOutputPort`, `ImageRecognitionOutputPort`, `VaneSearchOutputPort`, а OpenAI-совместимый endpoint
LLM и Vane подменены WireMock-заглушками на HTTP-границе. Проверены ответ модели, тело промпта (профили,
история, текущее сообщение), парсинг JSON
саммари, 3 попытки порта (9 HTTP-вызовов), картинки в запросе к модели (`image_url` с url изображения),
`SummaryGenerationException` при отказе саммаризации, маппинг ответа Vane и
`VaneProviderNotConfiguredException` при отсутствии провайдера с chat- и embedding-моделью.

`DailyGreetingsIntegrationTest` ([`src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt)) —
10 тестов реальной проводки `daily-greetings`: PostgreSQL (DevServices) с профилями и сообщениями,
засиженными через `VkUserProfileTable`/`MessageTable`, LLM (`/v1/chat/completions`) и VK API
(`/method/messages.send`) под WireMock, прогон вызывается напрямую через `RunDailyGreetingsInputPort` — ручного
REST-триггера нет. Покрыты: дни рождения в групповом чате и в личной переписке, Новый год только в групповых
чатах, 256-й день для невисокосного и високосного года, отдельное сообщение на каждый праздник и одно на
всех именинников чата, at-most-once `greeting_log` при последовательном и конкурентном повторе, шаблон при
500 от ИИ, тишина в день без событий, 29.02 → 1 марта невисокосного года. Отправка проверяется по телу `messages.send`: `peer_id`, текст,
`random_id` и отсутствие forward.

`WireMockTestResource` подменяет HTTP-границу трёх клиентов: VK API (`quarkus.rest-client.vk.url`), Vane
(`quarkus.rest-client.vane.url`) и LLM (`quarkus.langchain4j.openai.base-url`, [`WireMockTestResource.kt:24-34`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt#L24-L34)).
Этого достаточно и для Long Poll: `groups.getLongPollServer` идёт в `/method`, а `a_check` — на `server` из
ответа заглушки, то есть на тот же базовый URL WireMock.
Заглушек удалённых loopback-эндпоинтов `/profiles/*` и `/persistence/*` в нём нет, поэтому e2e не пройдёт при
возврате HTTP-контура между модулями (ADR 2026-10-28-in-process-ports-instead-of-loopback-rest).

## Архитектурные тесты

[`src/testing/arch-tests`](../../src/testing/arch-tests):

| Тест | Что проверяет |
|---|---|
| `ModuleDependenciesTest` | каждый бизнес-модуль зависит только от `share`; `share` — ни от кого |
| `LayerDependenciesTest` | L1 `domain`, L2 `port`, L3 `usecase`/`command`, L4 входные адаптеры, L5 выходные адаптеры |
| `NamingConventionsTest` | L7: имя типа — ответственность, а не слой; `*Controller`/`*Service`/`*Repository` запрещены, правило видит production-типы всех модулей (ADR 2026-11-13-responsibility-type-names) |
| `GradleDependenciesTest` | найдено 13 build-файлов; обязательные рёбра `:src:app`/`:src:testing:arch-tests` → `:src:greetings` и membership в `coverageModules`; production-рёбра соответствуют allowlist; testing-модули только в test-конфигурациях |
| `TestDataFactoriesTest` | сторож правила тестовых данных: прямое создание в тестах и дефолты параметров фабрик (см. «Сторож правила») |
| `DetektExcludesPathTest` | глобы `excludes` из [`detekt-config.yml`](../../detekt-config.yml) не матчат main-исходники даже при чек-ауте в каталоге с именем `test` |

Негативная проверка (этот проход): класс пакета `receiver` в модуле `persistence`, зависящий от
`persistence` → `ModuleDependenciesTest > receiver module should only depend on share() FAILED`, exit 1;
ссылка use case на адаптер → `LayerDependenciesTest > L3 … FAILED`, exit 1. После откатов — зелёные.

## Покрытие

- Агрегат по 9 модулям (`ai, app, greetings, infrastructure, persistence, processor, receiver, share,
  vk-facade`), `src/testing/*` исключён ([`build.gradle.kts:22-32`](../../build.gradle.kts#L22-L32)).
- Измерено 2026-11-13: **line ratio 0.6951** (4333 покрытых строк из 6234, 69.51% LINE);
  `coverageMin = 0.40`, `measuredLineCoverage = 0.6951` ([`build.gradle.kts:19-20`](../../build.gradle.kts#L19-L20)).
- Порог включён в `check` → `build` ([`build.gradle.kts:131-134`](../../build.gradle.kts#L131-L134)) и в CI-джоб `coverage`.
- Политика: порог не понижается; ратчет 0.40 выполнен (было 0.2796 при пороге 0.25), целевой порог 0.60
  достигнут; повышение порога — отдельное решение (ADR 2026-10-28-quality-gates-and-ci). Покрытие меряется по бизнес-коду, а не по glue.
- Негативная проверка: `coverageMin = 0.99` → `Rule violated for bundle vkbot: lines covered ratio is 0.69,
  but expected minimum is 0.99`, exit 1.

## Ограничения

- `@QuarkusTest` в `app` требует Docker (DevServices); в окружении без Docker эти тесты не пройдут.
- `@QuarkusTest` в `:src:ai` не запускается (нет плагина `io.quarkus`, ADR 2026-10-28-single-deployable-app-module), поэтому проводка `ai`
  проверяется Quarkus-тестом в единственном деплое — `AiIntegrationTest` в `:src:app`; юнит-тесты `ai` —
  plain JUnit 5 без Docker.
- Для устойчивости к отсутствию тестов в модуле сборка ставит `failOnNoDiscoveredTests = false`
  ([`build.gradle.kts:70-73`](../../build.gradle.kts#L70-L73)) — новый модуль без тестов не упадёт, но и не будет проверен.
