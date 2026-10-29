# Спецификация: тестовые данные только через фабрики (Fake*Provider)

- **id:** 0544056b-a6ef-4bd9-ac77-fba232fffd5f
- **Рабочая директория:** `/Users/asmaksime1/IdeaProjects/test/vk`
- **Тип:** рефакторинг тестов + правило + сторож (не bugfix: причина и изменение известны до кода)
- **Связанные документы:** `AGENTS.md` §1, §6, §8, §10; `docs/development/testing.md`;
  `docs/architecture/patterns.md`; `docs/adr/0003-module-boundaries-and-layer-rules.md` (границы
  тестовых модулей); `docs/adr/0005-quality-gates-and-ci.md` (гейт обязан падать).

## 1. Симптом и текущее состояние

Тесты создают доменные объекты, DTO и request/response напрямую в конструкторе или через `of(...)`.
Примеры (проверено чтением файлов):

| Файл | Строки | Что создаётся напрямую |
|---|---|---|
| `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt` | 28-38, 66-76, 100-155 | `StoredMessage`, `Summary`, `VkUserProfile.of`, `VkGroupProfile.of`, `FromId.of`, все `PersistenceDataRequest.*` / `PersistenceDataResponse.*` |
| `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapterTest.kt` | 14-15, 19-40 | `VkUserProfile.of`, `VkGroupProfile.of`, `FromId.of`, `VkProfileInputPort.*`, `GetUserProfilesBatchPort.Request`, `GetGroupProfilesBatchPort.Request` |
| `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkSendMessageOutputAdapterTest.kt` | 32 | `VkResponseDto(VkError(...))` |
| `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/usecase/message/MessageSendUsecaseTest.kt` | 19 | `VkSendMessageInputRequest(ResponseMessage(...))` |
| `src/infrastructure/src/test/kotlin/com/simarel/vkbot/infrastructure/decorator/logging/CommandLoggingDecoratorTest.kt` | 18-19, 37-38 | `FakeCommandRequest`, `FakeCommandResponse` |
| `src/receiver/src/test/kotlin/com/simarel/vkbot/receiver/command/sendVkEvent/PublishVkEventCommandImplTest.kt` | 16, 30 | `PublishVkEventCommandRequest`; локальная копия `FakePublishEventOutputPort`, хотя общая есть в `:src:testing:test-fixtures` |

Следствие: добавление одного поля в такую модель ломает компиляцию всех мест её создания; тест должен
знать о поле, которое его не касается. Это ровно та боль, из-за которой задача заведена.

Что уже есть и переиспользуется:

- `src/testing/test-fixtures/.../domain/FakeVoProvider.kt:19-86` — `createDate`, `createHumanFromId`,
  `createGroupId`, `createPeerId`, `createConversationMessageId`, `createMessageText`, `createMessage`,
  `createForwardedMessage`, `createMessageWithForwarded`; `FakeMessageProvider.kt:12-30` — `Message`
  поверх него.
- `src/receiver/src/testFixtures/.../fixtures/FakeVkConfirmationInputPortProvider.kt`,
  `FakeVkProvider.kt`; `src/processor/src/testFixtures/.../FakeMessageNewInputPortProvider.kt`.
- `FakeSendVkMessageCommandProvider`, `FakePublishEventCommandProvider`,
  `FakeVkSendMessageOutputProvider` в `:src:testing:test-fixtures`.
- Слабая версия правила уже записана: `docs/development/testing.md:96-102` («в тесте задаются только
  значимые поля, остальное берётся из провайдера») и `AGENTS.md:169-170` («Use them instead of
  constructing VOs by hand in tests»). Она неполная (не названы DTO и request/response, нет гейта) и
  нарушается тестами из таблицы.

Ограничения из документов и сборки:

- `docs/adr/0003-module-boundaries-and-layer-rules.md:25,38-40`: `testing:test-fixtures -> share,
  vk-facade`, `testing:arch-tests -> все модули`; модульные `src/testFixtures` уже есть у `receiver`,
  `processor`, `persistence`, `vk-facade`, `infrastructure`. Значит общие фабрики для `share` и
  `vk-facade` живут в `:src:testing:test-fixtures`, а для `persistence`/`receiver` — в их собственных
  `src/testFixtures`; новых зависимостей и модулей не нужно.
- `docs/adr/0005-quality-gates-and-ci.md`: гейт обязан уметь падать и проверяется негативным прогоном;
  в `:src:testing:arch-tests` уже есть тест, читающий исходники, — `GradleDependenciesTest.kt:104-130`.
- `src/testing/test-fixtures/detekt-baseline.xml` уже содержит `TooManyFunctions:FakeVoProvider.kt`, поэтому
  добавление функции в `FakeVoProvider` не роняет detekt; новые фабрики раскладываются по файлам меньше
  порога `thresholdInFiles/thresholdInClasses = 11` (`detekt-config.yml:168-173`).
- `scripts/check-capabilities.sh` проверяет только существование путей тестов, диапазоны строк не валидирует;
  наблюдаемое поведение не меняется, поэтому `docs/capabilities/` не получает новых строк.

Базовая линия (прогон этого прохода): `./gradlew test` → exit 0, 59 тестов (44 в бизнес-модулях + 15
arch-tests), `./gradlew build` в этом проходе не перепроверялся (будет доказан в board task).

## 2. Цель и не-цели

**Цель**

1. Записать в документы правило: тестовые данные создаются только фабриками `Fake*Provider` с
   дефолтами на все поля; прямое создание — только в самих фабриках и в тесте самого типа.
2. Перевести существующие тесты на фабрики; добавить недостающие фабрики.
3. Поставить автоматический сторож `TestDataFactoriesTest` в `:src:testing:arch-tests`, который ловит
   прямое создание данных в тестах и обязательные параметры фабрик.
4. Не менять производственный код, контракты, конфигурацию, поведение, карту возможностей.

**Не-цели**

- Не рефакторить JSON-тела интеграционных тестов (`VkWebhookIntegrationTest`) и HOCON-подобные строки:
  это контракт VK, доменные поля на них не влияют.
- Не трогать `ExceptionHandlerTest` (`ValidationException`, `ClassCastException`, `RuntimeException` —
  это входы-исключения, а не тестовые данные) и `MessageTextTest`/`ConversationMessageIdTest` (создают
  тестируемый VO — разрешённое исключение).
- Не переносить локальные `Recording*`-фейки (`RecordingPersistenceDataInputPort`,
  `RecordingVkProfileInputPort`): это записывающие фейки-зависимости, а не объекты-данные; правило их не
  касается. Локальный дубль `FakePublishEventOutputPort` в `PublishVkEventCommandImplTest` заменяется
  общей реализацией — это прямое переиспользование уже существующего фейка.
- Не менять `detekt-config.yml`, `.github/**`, `build.gradle.kts`, `AGENTS.md` §10 (там работает
  параллельная задача `c40b0d2a`), ADR-файлы, `docs/capabilities/*` кроме диапазонов строк, сдвинутых
  этим рефакторингом (см. §9).
- Не поднимать порог покрытия и не добавлять тесты на бизнес-поведение.

## 3. Принятые решения (согласованы с человеком)

| Вопрос | Решение |
|---|---|
| Гарантия правила | доки + рефакторинг + тест-сторож в `:src:testing:arch-tests` |
| Что считаем объектом | доменные модели/VO/события `share`, DTO адаптеров (`adapter/output/**/dto`), request/response портов и команд; исключения — тестируемый тип и исключения-ошибки; JSON-тела интеграционных тестов остаются |
| Где фабрики | существующая конвенция `Fake*Provider`/`create...`: общие типы — `:src:testing:test-fixtures`, модульные — `<module>/src/testFixtures`; новые зависимости не добавляются |
| Документы | `docs/development/testing.md` + `AGENTS.md` §8 + `docs/architecture/patterns.md`; ADR не нужен |

Принятые допущения:

- Дефолты фабрик реалистичны (не `""`/`null` «по умолчанию»), а незначащие значения можно не передавать.
- Сторож — текстовый скан исходников (как `GradleDependenciesTest` читает `build.gradle.kts`); он ловит
  конструктор `Type(` и `Type.of(`, но не `copy(...)` и не обращения к production-классам вне
  перечисленных пакетов. Ложное срабатывание лечится использованием фабрики или переименованием файла
  теста самого типа; это осознанная цена.
- Фабрика может создавать объект напрямую — она и есть разрешённая точка создания.
- Существующие имена провайдеров (`FakeVoProvider`, `FakeMessageProvider`) сохраняются.

## 4. Правило (нормативный текст для документов)

**Тестовые данные создаются только фабриками.**

1. В `src/*/src/test/**` запрещено прямое создание (`Type(...)`, `Type.of(...)`) типов:
   - доменных моделей, VO и событий `com.simarel.vkbot.share.domain.{model,vo,event}`;
   - DTO под `**/adapter/output/**/dto/**`;
   - request/response под `**/port/**` и `**/command/**` (включая вложенные типы контрактов).
2. Данные берутся из `*Provider`-фабрики: общие типы — в `:src:testing:test-fixtures`, модульные — в
   `<module>/src/testFixtures`. Фабрика — единственное место, где эти типы создаются напрямую.
3. У каждого параметра фабричного метода `create*` есть значение по умолчанию; тест передаёт только
   поля, от которых зависит проверка. Добавление поля в тип = добавление дефолтного параметра в
   фабрику, тесты не меняются.
4. Исключения: тест самого типа (`<Type>Test.kt`, для вложенного контракта — тест внешнего типа),
   исключения-ошибки и сырые JSON-тела интеграционных тестов.
5. Фейки-зависимости (`Fake*`, `Recording*`) по-прежнему создаются в тесте напрямую; общие фейки
   переиспользуются, а не копируются локально.
6. Нарушение правила ловит `TestDataFactoriesTest` в `:src:testing:arch-tests`.

## 5. Фабрики: точная раскладка

Все новые методы — с дефолтом у каждого параметра, возвращают готовый объект; имя — `create<Thing>`.

### 5.1 `:src:testing:test-fixtures` (общие типы `share` и `vk-facade`)

| Файл | Методы |
|---|---|
| `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt` (правка) | `createFromId(value: Long? = null): FromId` |
| `.../domain/FakeModelProvider.kt` (новый) | `createStoredMessage(id: UUID = UUID.randomUUID(), peerId: Long = …, conversationMessageId: Long = …, fromId: Long = …, messageText: String? = "text", date: OffsetDateTime? = FIXED_DATE, forwardedMessages: String? = null)`, `createSummary(id: UUID = UUID.randomUUID(), peerId: Long = …, firstMessageId: Long? = 2, lastMessageId: Long? = 3, shortSummary: String? = "short summary", fullSummary: String? = "full summary", status: SummaryStatus = COMPLETED, createdAt/updatedAt = FIXED_DATE)`, `createVkUserProfile(id: Long? = null, firstName: String? = "Ivan", lastName: String? = "Petrov", screenName: String? = "ivan_petrov", birthDate: String? = "01.01.1990", city: String? = "Moscow")`, `createVkGroupProfile(id: Long? = null, name: String? = "Test Group", screenName: String? = "testgroup")`, `createResponseMessage(messageText: MessageText? = null, responseTo: Message? = null)` |
| `.../port/FakeProfilePortProvider.kt` (новый) | `createGetUserProfilesBatchRequest(fromIds: List<FromId> = emptyList())`, `createGetGroupProfilesBatchRequest(fromIds: List<FromId> = emptyList())` |
| `.../port/FakePersistencePortProvider.kt` (новый) | `createFindMessagesBeforeRequest(peerId: Long = 1, beforeConversationMessageId: Long = 2, limit: Int = 3)`, `createFindMessagesBetweenRequest(peerId: Long = 1, firstMessageId: Long = 2, lastMessageId: Long = 3, limit: Int = 4)`, `createFindLastSummaryRequest(peerId: Long = 1)`, `createHasPendingSummaryRequest(peerId: Long = 1)`, `createCreatePendingSummaryRequest(peerId: Long = 1, firstMessageId: Long = 2, lastMessageId: Long = 3)`, `createSaveCompletedSummaryRequest(summaryId: UUID = UUID.randomUUID(), shortSummary: String = "short summary", fullSummary: String = "full summary")`, `createMarkSummaryFailedRequest(summaryId: UUID = UUID.randomUUID())`, `createFindUserProfilesByIdsRequest(ids: List<FromId> = emptyList())`, `createFindGroupProfilesByIdsRequest(ids: List<FromId> = emptyList())` |
| `.../vkFacade/FakeVkFacadeProvider.kt` (новый) | `createVkError(errorCode: Int = 1, errorMessage: String = "error happened")`, `createVkResponseDto(error: VkError? = null)`, `createVkProfileRequest(userIds: List<Long> = emptyList(), groupIds: List<Long> = emptyList())`, `createVkProfileResponse(users: List<VkUserProfile> = emptyList(), groups: List<VkGroupProfile> = emptyList())`, `createVkSendMessageInputRequest(responseMessage: ResponseMessage? = null, forwardedMessages: ForwardedMessages? = null)` |
| `.../command/FakeCommandProvider.kt` (новый) | `createRequest(value: String = "test")`, `createResponse(result: String = "result")` |

`FIXED_DATE` — `OffsetDateTime.parse("2026-01-01T00:00:00Z")`; профили создаются с фиксированным
`lastUpdated`, чтобы два независимо созданных профиля были равны.

### 5.2 Модульные `src/testFixtures`

| Файл | Методы |
|---|---|
| `src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataRequestProvider.kt` (новый) | 9 методов для `PersistenceDataRequest.*`: `createFindMessagesBeforeRequest`, `createFindMessagesBetweenRequest`, `createFindLastSummaryRequest`, `createHasPendingSummaryRequest`, `createCreatePendingSummaryRequest`, `createSaveCompletedSummaryRequest`, `createMarkSummaryFailedRequest`, `createFindUserProfilesByIdsRequest`, `createFindGroupProfilesByIdsRequest` — все параметры с дефолтами |
| `src/persistence/src/testFixtures/.../PersistenceDataResponseProvider.kt` (новый) | `createMessagesResponse(messages: List<StoredMessage> = emptyList())`, `createOptionalSummaryResponse(summary: Summary? = null)`, `createPendingStatusResponse(hasPending: Boolean = false)`, `createCreatedSummaryIdResponse(id: UUID = UUID.randomUUID())`, `createEmptyResponse()`, `createUserProfilesResponse(profiles: List<VkUserProfile> = emptyList())`, `createGroupProfilesResponse(profiles: List<VkGroupProfile> = emptyList())` |
| `src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/FakePublishVkEventCommandProvider.kt` (новый) | `createRequest(message: Message? = null)` |

`PersistenceDataRequestProvider` и `PersistenceDataResponseProvider` разделены, чтобы не превысить
`TooManyFunctions.thresholdInFiles = 11`.

### 5.3 Рефакторинг существующих тестов

| Тест | Было | Станет |
|---|---|---|
| `PersistenceDataInProcessAdapterTest.kt` (9 тестов) | прямые `StoredMessage`, `Summary`, `VkUserProfile.of`, `VkGroupProfile.of`, `FromId.of`, `PersistenceDataRequest/Response` | `FakeModelProvider`, `FakeVoProvider.createFromId`, `FakePersistencePortProvider`, `PersistenceDataRequestProvider`, `PersistenceDataResponseProvider`; значимые id/значения передаются явно |
| `VkProfileInProcessAdapterTest.kt` (2) | профили, `FromId.of`, `VkProfileInputPort.*`, `Get*ProfilesBatchPort.Request` | `FakeModelProvider`, `FakeVoProvider.createFromId`, `FakeVkFacadeProvider`, `FakeProfilePortProvider` |
| `VkSendMessageOutputAdapterTest.kt` (2) | `VkResponseDto(VkError(...))` | `FakeVkFacadeProvider.createVkResponseDto(FakeVkFacadeProvider.createVkError())` |
| `MessageSendUsecaseTest.kt` (1) | `VkSendMessageInputRequest(ResponseMessage(...))` | `FakeVkFacadeProvider.createVkSendMessageInputRequest(...)` + `FakeModelProvider.createResponseMessage(...)` |
| `CommandLoggingDecoratorTest.kt` (2) | `FakeCommandRequest/Response(...)` | `FakeCommandProvider.createRequest/createResponse(...)` |
| `PublishVkEventCommandImplTest.kt` (1) | `PublishVkEventCommandRequest(message)`, локальный дубль `FakePublishEventOutputPort` | `FakePublishVkEventCommandProvider.createRequest(...)`, общий `FakePublishEventOutputPort` из `:src:testing:test-fixtures`; локальный класс удаляется |
| Без изменений | `MessageTextTest`, `ConversationMessageIdTest`, `ExceptionHandlerTest`, `VkWebhookIntegrationTest`, `ReceiveMessageUsecaseTest`, `VkMediatorRouterIntegrationTest`, `MessageReceivedUsecaseInputTest`, arch-tests | — |

## 6. Сторож `TestDataFactoriesTest` (точное поведение)

Новый файл: `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/TestDataFactoriesTest.kt`.
`project.root` уже передаётся (`src/testing/arch-tests/build.gradle.kts:44-47`); корень — как в
`GradleDependenciesTest.kt:8-10`. Два теста, backtick-имена в стиле соседей.

### 6.1 `test sources create test data only through test data providers`

1. Собрать множество запрещённых типов из `src/**/src/main/kotlin`:
   - файлы `com/simarel/vkbot/share/domain/{model,vo,event}/**/*.kt` — все `class`/`data class`/`value class`;
   - файлы под `**/port/**/*.kt` и `**/command/**/*.kt` — top-level классы с именем на `Request`/`Response`
     и все классы, вложенные в такие типы (например `PersistenceDataRequest.FindMessagesBefore`);
   - файлы под `**/adapter/output/**/dto/**/*.kt` — все классы.
   `enum class`, `object`, `interface`, `sealed interface` в множество не входят.
2. Пройти `src/*/src/test/**/*.kt` и для каждого типа искать `\b<Type>\s*\(` и `\b<Type>\s*\.of\s*\(`
   (граница слова не даёт сработать на `createMessage(`/`createMessageText(`).
3. Разрешено: файл теста самого типа — `<Type>Test.kt`; для вложенного контракта — тест внешнего типа
   (`<Owner>Test.kt`, например `VkProfileInputPortTest.kt`); `testFixtures` вообще не сканируются.
4. При нарушении — упасть с сообщением `path:line: <Type> создаётся напрямую; используй <Provider> из
   src/.../testFixtures`.

### 6.2 `every factory method parameter has a default`

1. Файлы `src/testing/test-fixtures/src/main/kotlin/**/*.kt` и `src/*/src/testFixtures/**/*.kt`.
2. Каждая функция `fun create<Name>(...)` (в т.ч. многострочная) — у каждого параметра верхнего уровня
   обязательна часть `= <default>`; функции без параметров проходят.
3. При нарушении — `path:line: параметр <name> функции <fn> без дефолта; тест будет обязан знать о поле`.

### 6.3 Обязательные негативные прогоны (в test-report)

- Временная строка `val message = Message.of(...)` в любом существующем тесте → тест 1 FAILED с
  `path:line`; откат → PASSED. Вывод цитируется.
- Временный обязательный параметр в любой `create*`-фабрике → тест 2 FAILED с именем параметра; откат →
  PASSED. Вывод цитируется.

Ограничение (зафиксировать в `docs/development/testing.md`): скан текстовый, строковые литералы не
разбираются; ложное срабатывание лечится фабрикой или переименованием теста самого типа.

## 7. Acceptance criteria (EARS)

- **AC-1** — КОГДА выполняется `./gradlew test`, СИСТЕМА ДОЛЖНА пройти сторож
  `TestDataFactoriesTest > test sources create test data only through test data providers`, то есть в
  `src/*/src/test/**` не остаётся прямого создания типов из §4, кроме теста самого типа.
  Test: `./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*"`; полный прогон —
  `./gradlew test`.
- **AC-2** — КОГДА у фабричного метода `create*` есть параметры, СИСТЕМА ДОЛЖНА иметь дефолт у каждого
  параметра, чтобы тест мог не передавать незначащие поля. Test тот же:
  `TestDataFactoriesTest > every factory method parameter has a default`.
- **AC-3** — КОГДА в тест добавляют прямое создание типа тестовых данных или обязательный параметр
  фабрики, сторож ДОЛЖЕН упасть с `path:line` и подсказкой. Test: негативные прогоны §6.3, вывод в
  `test-report.md`.
- **AC-4** — КОГДА существующие тесты переведены на фабрики, СИСТЕМА ДОЛЖНА держать общие фабрики в
  `:src:testing:test-fixtures`, модульные — в `src/persistence/src/testFixtures` и
  `src/receiver/src/testFixtures`, без новых зависимостей и правок `build.gradle.kts`.
  Test: `./gradlew :src:testing:arch-tests:test` (`GradleDependenciesTest`,
  `build files are discovered`, `production project dependencies follow the allowlist`) + пустой
  `git diff --stat -- '**/build.gradle.kts'`.
- **AC-5** — КОГДА разработчик открывает `docs/development/testing.md`, `AGENTS.md` §8 и раздел Factory
  в `docs/architecture/patterns.md`, ДОКУМЕНТЫ ДОЛЖНЫ содержать правило §4, исключения, размещение
  фабрик, имя сторожа и порядок «добавили поле — добавили дефолт в фабрику».
  Test: `rg -n "TestDataFactoriesTest|FakeModelProvider|FakeVkFacadeProvider|PersistenceDataRequestProvider" docs/development/testing.md AGENTS.md docs/architecture/patterns.md`
  (все три файла в выводе) + `ls` каждого названного пути кода.
- **AC-6** — КОГДА выполняется `./gradlew build`, СБОРКА ДОЛЖНА проходить: тесты, detekt, ktlintCheck,
  coverageVerification, capabilitiesCheck, arch-tests. Test: `./gradlew build` (exit 0),
  `./gradlew test` (количество тестов процитировано), `./scripts/check-capabilities.sh` → OK.

## 8. Проверки, которые обязана содержать каждая board task

```bash
./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*"   # AC-1, AC-2
./gradlew :src:testing:arch-tests:test                                     # AC-4
./gradlew test                                                             # AC-1, AC-2, AC-6 (нужен Docker)
./gradlew build                                                            # AC-6
./scripts/check-capabilities.sh                                            # AC-6
git diff --stat -- '**/build.gradle.kts'                                   # AC-4: пусто
```

Плюс негативные прогоны §6.3 отдельными командами с цитатами вывода.

## 9. Документы (что именно меняется)

- `docs/development/testing.md`:
  - раздел «Провайдеры тестовых данных» → полное правило §4, исключения, размещение, пример
    «добавили поле в модель» (добавить дефолтный параметр в фабрику; тесты не меняются);
  - актуальное дерево фабрик (новые файлы §5.1-5.2, `createFromId`, `FakeModelProvider`,
    `FakeVkFacadeProvider`, `FakeProfilePortProvider`, `FakePersistencePortProvider`, `FakeCommandProvider`,
    `PersistenceDataRequestProvider`, `PersistenceDataResponseProvider`, `FakePublishVkEventCommandProvider`);
  - подраздел «Сторож правила»: `TestDataFactoriesTest`, что проверяет, негативные прогоны, ограничение
    текстового скана;
  - обновить число тестов и раскладку по модулям (arch-tests: +2, всего 61 на момент прогона; точное
    число цитируется из вывода).
- `AGENTS.md` §8: вместо одной строки — норма: какие типы нельзя создавать напрямую, где фабрики, что у
  параметров есть дефолты, какое исключение, имя сторожа. §6: обновить строку про `./gradlew test` и
  `:src:testing:arch-tests:test` (число тестов, новый сторож). §10 не трогать.
- `docs/architecture/patterns.md`, раздел **Factory (companion `of`)**: добавить, что в тестах `of(...)`
  и конструкторы запрещены, кроме теста самого типа; данные — из `Fake*Provider`, дефолты на все поля;
  сторож — `TestDataFactoriesTest`.
- `docs/capabilities/message-storage.md:84,87`, `vk-profiles.md:67,70`, `vk-message-send.md:67,70,74`:
  обновить только диапазоны строк тестов, которые сдвинул рефакторинг (файлы те же). Новых capability-строк
  нет; `scripts/check-capabilities.sh` должен остаться OK.

## 10. Риски, которые меняют план

| Риск | Влияние | Смягчение |
|---|---|---|
| Текстовый сторож даёт ложные срабатывания (строковый литерал, нестандартное форматирование) | CI красный на невиновном коде | граница слова, исключение теста самого типа, понятное сообщение; ограничение описано в testing.md |
| Сторож ловит будущий незавершённый код (новая модель без фабрики) | красный build у следующей задачи | это и есть цель: фабрика добавляется в той же задаче; в сообщении подсказан путь |
| Рост `FakeVoProvider` упирается в `TooManyFunctions` | detekt падает | baseline для `FakeVoProvider` уже есть; остальные фабрики разложены по файлам ≤ 9 функций |
| Параллельная задача `c40b0d2a` правит `AGENTS.md` §6/§10, detekt, ADR 0005, capability-файлы | конфликт при записи одного файла | наша задача не трогает detekt/ADR/`AGENTS.md` §10; `AGENTS.md` §6 и §8 — только наша; capability-файлы не пересекаются (у нас message-storage/vk-profiles/vk-message-send, у неё ai-*) |
| Локальный detekt слеп к `**/test/**` (известный дефект пути) | локально «зелено», CI — нет | новые тесты в `:src:testing:arch-tests` и `testFixtures`; прогон `./gradlew build` в задаче A до коммита; задача `c40b0d2a` чинит гейт |
| Полный `./gradlew test` требует Docker (DevServices) | проверка не воспроизводится на машине без Docker | Docker на машине есть (проверено `docker info`); в test-report указать, что app-тесты идут через DevServices |
| Покрытие | `src/testing/*` исключён из JaCoCo, тестовые правки его не двигают | порог 0.25 не меняется; `./gradlew build` доказывает |

Необратимых шагов, потери данных, изменения контрактов, безопасности и стоимости деплоя нет: правки
только в тестовом коде и документах, продакшн-поведение не меняется. Откат — revert коммита.

## 11. Границы и измерения

- **Кто и когда:** разработчик, который пишет или правит тест; гейт — в каждом `./gradlew test`/CI.
- **Объёмы:** 59 существующих тестов, +2 теста сторожа; 6 тестовых файлов рефакторятся, 6 фабричных
  файлов добавляются, 2 правятся; строки — десятки, не сотни.
- **Права и роли:** нет; тестовая инфраструктура.
- **Пустой и ошибочный путь:** фабрика без аргументов даёт реалистичный полный объект; нарушение правила
  даёт падение с `path:line` и именем фабрики/параметра.
- **Совместимость:** продакшн-код, API, БД, события и конфиг не меняются; миграций нет; старые тестовые
  данные не «мигрируют» никуда.
- **Наблюдаемость:** результат виден в `./gradlew test` и CI-джобе `test`; отдельной телеметрии не нужно.
- **Раскатка:** обычный merge, без деплоя; флага нет.
- **Стоимость и задержка:** +2 теста в arch-tests (секунды), покрытие не падает (тестовые модули
  исключены из JaCoCo).

## 12. Декомпозиция на board tasks

```text
factories-and-gate (quick-fix, kotlin-dev)  ──► docs-rule (quick-fix, specialist)
```

- Почему `quick-fix`, а не `bugfix`: причина и изменение известны (инвентарь выше), воспроизведение не
  нужно. Не `spec-driven-delivery`/`feature-delivery`: спецификация, AC, DoD и вопросы уже согласованы в
  интейке; отдельные стадии questions/spec/spec-review дублировали бы этот документ.
- `factories-and-gate` — код и сторож; `docs-rule` — документы, которые описывают уже доставленное
  состояние (зависимость реальная: в документы входят имена и пути фабрик и число тестов).
- Ревьюеры: у первой задачи дополнительно `qa-expert` (проверяет сам сторож и качество тестов), у
  второй — `spec-critic` (проверяет, что правило непротиворечиво и однозначно). Обязательные
  `test-reviewer` и `code-reviewer` остаются.

## 13. Out of scope

- Любые правки production-кода и конфигурации.
- JSON-тела и WireMock-стабы интеграционных тестов.
- Локальные `Recording*`-фейки и вообще перенос фейков-зависимостей.
- Изменение `detekt-config.yml`, `.github/**`, `build.gradle.kts`, ADR.
- Новые тесты на бизнес-поведение, повышение покрытия, ратчет.
- `AGENTS.md` §10 (зона параллельной задачи `c40b0d2a`).
