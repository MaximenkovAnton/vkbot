# tests.md — `ai-answer-unit-and-foundation`

- **Board task:** `ai-answer-unit-and-foundation` (board `test-authoring`)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Ветка / worktree:** `fix/ai-tests` / `/Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests`
- **Стадия:** `write` (implementation), агент `test-writer`
- **Production-код:** не изменялся; `git diff --stat -- 'src/*/src/main'` пуст (см. §5).
- **Одна команда всего набора:** `./gradlew :src:ai:test`

## 1. Фундамент тестов `ai`

### 1.1 `src/ai/build.gradle.kts`

Подключён плагин `java-test-fixtures` и test-конфигурации по образцу `src/processor/build.gradle.kts`;
существующие зависимости (CDI, Jackson, REST Client, LangChain4j, `quarkus-junit5`, `kotlin("test")`)
сохранены, внешних библиотек не добавлено:

```kotlin
plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.kordamp.gradle.jandex") version "2.3.0"
}

dependencies {
    implementation(project(":src:share"))

    testFixturesImplementation(project(":src:share"))
    testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures")))

    testImplementation(testFixtures(project))
    testImplementation(testFixtures(project(":src:testing:test-fixtures")))
    ...
}
```

### 1.2 `src/ai/src/testFixtures/kotlin/com/simarel/vkbot/ai/testfixtures/`

Единственное место прямого создания контрактных типов `ai`. У каждого параметра каждой `create*`-фабрики
есть реалистичный дефолт — это проверяет `TestDataFactoriesTest` (см. §4.3).

| Файл | Фабрики | Назначение |
|---|---|---|
| `FakeAnswerProvider.kt` | `createChatMessage`, `createGenerateAnswerOutputPortRequest/Response`, `createConversationContextRequest/Response`, `createGenerateAnswerCommandRequest/Response`, `createMessageRequireAnswerInputPortRequest/Response` | AC-1…AC-5, контракты `ai-answer` |
| `FakeSummaryProvider.kt` | `createSummarizationRequest/Response`, `createGenerateSummaryCommandRequest/Response`, `createProcessSummaryInputPortRequest`, `createGenerateSummaryInputPortRequest/Response` | задел `summary-unit-tests` |
| `FakeVaneProvider.kt` | `createVanePortSearchRequest`, `createVanePortSearchResponse`, `createVanePortSource` — порт; `createVaneClientSearchRequest`, `createVaneClientSearchResponse`, `createVaneClientSource`, `createVaneClientSourceMetadata`, `createVaneModelConfig`, `createVaneProvidersResponse`, `createVaneProvider`, `createVaneModel` — REST-клиент | задел `vane-unit-tests`; одноимённые типы порта и клиента разведены именами фабрик |
| `FakeConversationContextPortProvider.kt` | `createFindMessagesBeforeResponse`, `createFindUserProfilesByIdsResponse`, `createFindGroupProfilesByIdsResponse` | ответы persistence-портов для AC-3 (запросы — из общего `FakePersistencePortProvider`) |

Данные берутся из общих `FakeVoProvider` / `FakeModelProvider` (`:src:testing:test-fixtures`);
общие фейки (`FakePublishEventCommand`) переиспользуются, а не копируются.

### 1.3 Тестовые классы

| Класс | Тестов | Файл |
|---|---|---|
| `GenerateAnswerUsecaseTest` | 1 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt` |
| `GenerateAnswerCommandImplTest` | 2 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommandImplTest.kt` |
| `ConversationContextRestAdapterTest` | 7 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapterTest.kt` |
| `GenerateAnswerOutputAdapterTest` | 6 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt` |
| `MessageRequireAnswerEventProcessorTest` | 2 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt` |

Все тесты — plain JUnit 5 без Quarkus, фейки пишут вызовы в `ConcurrentLinkedQueue`, имена — backtick-предложения.

## 2. Ledger: AC → тест → команда → evidence

| AC | Доступное поведение (DoD) | Тест | Файл | Команда |
|---|---|---|---|---|
| AC-1 | публикуется `ANSWER_MESSAGE` с `ResponseMessage(text, responseTo = исходное)`, ответ `ok` | `execute publishes ANSWER_MESSAGE with the generated answer addressed to the original message` | `.../usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt` | `./gradlew :src:ai:test --tests "*GenerateAnswerUsecaseTest*"` |
| AC-2 | контекст запрошен, AI-запрос собран, ответ обёрнут в `MessageText` | `execute loads the conversation context and forwards it to the ai port`; `execute returns the ai answer as message text` | `.../command/answer/GenerateAnswerCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*GenerateAnswerCommandImplTest*"` |
| AC-3 | ≤20 сообщений до текущего; профили по знаку `fromId`; JSONB массив/объект рекурсивно | 7 тестов (см. ниже) | `.../adapter/output/context/ConversationContextRestAdapterTest.kt` | `./gradlew :src:ai:test --tests "*ConversationContextRestAdapterTest*"` |
| AC-4 | профили/история/текущее сообщение/вложенные пересланные блоки, fallback `idN`, пустые профили | 6 тестов (см. ниже) | `.../adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt` | `./gradlew :src:ai:test --tests "*GenerateAnswerOutputAdapterTest*"` |
| AC-5 | JSON `MESSAGE_REQUIRE_ANSWER` разобран в `Message` и передан в input port; `event()` = `MESSAGE_REQUIRE_ANSWER` | `process reads message json and passes the parsed message to the input port`; `event is MESSAGE_REQUIRE_ANSWER` | `.../adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt` | `./gradlew :src:ai:test --tests "*MessageRequireAnswerEventProcessorTest*"` |

### AC-1 — `GenerateAnswerUsecaseTest`

Проверяет наблюдаемый результат обработки `MESSAGE_REQUIRE_ANSWER`: команда получила исходное сообщение,
опубликовано ровно одно событие `ANSWER_MESSAGE`, payload — `ResponseMessage` с текстом от команды и
`responseTo`, равным исходному сообщению; ответ usecase — `ok`.

Evidence (`.:src:ai:test --tests "*GenerateAnswerUsecaseTest*"`, exit 0):
```
> Task :src:ai:test
BUILD SUCCESSFUL in 6s
```
Негативная проверка — M1.

### AC-2 — `GenerateAnswerCommandImplTest`

- `execute loads the conversation context and forwards it to the ai port` — контекст запрошен ровно для
  входного `Message`, а `GenerateAnswerOutputPortRequest` собран из полей полученного контекста
  (`currentMessage`, `chatHistory`, `userProfiles`, `groupProfiles`).
- `execute returns the ai answer as message text` — ответ AI-порта `"answer from model"` возвращён как
  `MessageText` с тем же значением.

Evidence (`.:src:ai:test --tests "*GenerateAnswerCommandImplTest*"`, exit 0):
```
> Task :src:ai:test
BUILD SUCCESSFUL in 6s
```
Негативные проверки — M2, M3.

### AC-3 — `ConversationContextRestAdapterTest`

JSONB строится не хардкодом, а тем же набором Jackson-модулей, что production
(`ObjectMapperProvider.create().registerModule(JavaTimeModule())`): `Message` сериализуется в JSON-строку,
которая кладётся в `StoredMessage.forwardedMessages`.

| Тест | Что доказывает |
|---|---|
| `execute requests the last twenty messages before the current message` | запрос `FindStoredMessagesBeforePort.Request(peerId, before=conversationMessageId, limit=20)` |
| `execute maps stored messages into chat history and keeps the current message` | `StoredMessage` → `ChatMessage` (id/fromId/текст/дата), `currentMessage` не изменён |
| `execute maps a stored message without text to an empty chat message text` | `messageText = null` → `""` |
| `execute asks user profiles for human authors and group profiles for group authors` | `fromId = 5` / `7` → user-порт, `fromId = -8` → group-порт |
| `execute associates returned profiles with their author ids` | ответы портов разложены в `Map<FromId, …>` по id автора |
| `execute parses a forwarded message array recursively and collects nested authors` | JSON-массив, вложенный forwarded; авторы вложенных (`5`, `-8`) попадают в выборки профилей |
| `execute parses a single forwarded message object` | JSON-объект (не массив) разобран в forwarded-сообщение |

Evidence (`.:src:ai:test --tests "*ConversationContextRestAdapterTest*"`, exit 0):
```
> Task :src:ai:test
BUILD SUCCESSFUL in 7s
```
Негативные проверки — M4…M9.

### AC-4 — `GenerateAnswerOutputAdapterTest`

Фейк `UserAnswerAiService` — приватный `Recording`-класс, пишет 4 отформатированные строки
(`userProfiles`, `groupProfiles`, `chatHistory`, `currentMessage`).

| Тест | Что доказывает |
|---|---|
| `execute formats user and group profiles` | `- id1: Ivan Petrov (@ivan_petrov)` и `- id-2: Group (@group)` |
| `execute formats chat history as message blocks` | блок `[MESSAGE_START]…[MESSAGE_END]` с `ID: 10 | Отправитель: Ivan Petrov (id1)` |
| `execute formats the current message and nested forwarded blocks with levels and indentation` | `[FORWARDED_BLOCK]`, `Уровень: 1/2`, отступы `    `, `[END_LEVEL_2]`/`[END_LEVEL_1]` |
| `execute falls back to id when a profile is missing` | нет профиля → `Отправитель: id999` / `id-555` |
| `execute passes empty profile blocks when no profiles are known` | пустые профили → `""`, пустая история → `""` |
| `execute returns the answer produced by the ai service` | ответ AI-сервиса доходит до `GenerateAnswerOutputPortResponse.answer` |

Evidence (`.:src:ai:test --tests "*GenerateAnswerOutputAdapterTest*"`, exit 0):
```
> Task :src:ai:test
BUILD SUCCESSFUL in 6s
```
Негативные проверки — M10…M14.

### AC-5 — `MessageRequireAnswerEventProcessorTest`

JSON строится из `FakeVoProvider.createMessage(...)` тем же mapper'ом с `JavaTimeModule`, который
разбирает production. Проверено: вложенные forwarded-сообщения и все поля `Message` доходят до input port
без потерь; `event()` называется `MESSAGE_REQUIRE_ANSWER`.

Evidence (`.:src:ai:test --tests "*MessageRequireAnswerEventProcessorTest*"`, exit 0):
```
> Task :src:ai:test
BUILD SUCCESSFUL in 7s
```
Негативные проверки — M15, M16.

## 3. Негативное доказательство: тесты не вакуумные

Каждая мутация — временная правка production-файла, затем запуск класса и полный откат. После всех
мутаций `git diff --stat -- 'src/*/src/main'` пуст, `git status --short` не содержит следов мутаций.
Одна мутация на поведение (16 мутаций покрывают все 18 тестов).

| # | Файл | Мутация | Упавшие тесты |
|---|---|---|---|
| M1 | `usecase/.../GenerateAnswerUsecase.kt` | `Event.ANSWER_MESSAGE` → `Event.MESSAGE_RECEIVED` | AC-1 test |
| M2 | `command/answer/GenerateAnswerCommand.kt` | `chatHistory = context.chatHistory` → `emptyList()` | AC-2 test 1 |
| M3 | `command/answer/GenerateAnswerCommand.kt` | ответ AI → `MessageText.of("")` | AC-2 test 2 |
| M4 | `adapter/output/context/ConversationContextRestAdapter.kt` | `limit = 20` → `19` | AC-3 test 1 |
| M5 | `adapter/output/context/ConversationContextRestAdapter.kt` | forwarded в `ChatMessage` → `emptyList()` | AC-3 tests 6, 7 |
| M6 | `adapter/output/context/ConversationContextRestAdapter.kt` | `partition { it.value < 0 }` → `> 0` | AC-3 tests 4, 6 |
| M7 | `adapter/output/context/ConversationContextRestAdapter.kt` | id + 1 при маппинге истории | AC-3 test 2 |
| M8 | `adapter/output/context/ConversationContextRestAdapter.kt` | `messageText ?: ""` → `?: "mutated"` | AC-3 test 3 |
| M9 | `adapter/output/context/ConversationContextRestAdapter.kt` | профили разложены по `id + 1` | AC-3 test 5 |
| M10 | `adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt` | формат профиля без `@` | AC-4 test 1 |
| M11 | `adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt` | автор без `(idN)` | AC-4 tests 2, 3 |
| M12 | `adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt` | fallback `idN` → `unknown` | AC-4 test 4 |
| M13 | `adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt` | пустые профили → `"mutated"` | AC-4 test 5 |
| M14 | `adapter/output/ai/answer/GenerateAnswerOutputAdapter.kt` | ответ адаптера → `""` | AC-4 test 6 |
| M15 | `adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessor.kt` | `readValue` → `Message.of(null, …)` | AC-5 test 1 |
| M16 | `adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessor.kt` | `event()` → `MESSAGE_RECEIVED` | AC-5 test 2 |

Цитаты вывода (каждый запуск — `./gradlew :src:ai:test --tests "*<Class>*"`; ниже показан хвост лога):

**M1**
```
> Task :src:ai:test FAILED
GenerateAnswerUsecaseTest > execute publishes ANSWER_MESSAGE with the generated answer addressed to the original message() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerUsecaseTest.kt:36
```

**M2**
```
> Task :src:ai:test FAILED
GenerateAnswerCommandImplTest > execute loads the conversation context and forwards it to the ai port() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerCommandImplTest.kt:37
```

**M3**
```
> Task :src:ai:test FAILED
GenerateAnswerCommandImplTest > execute returns the ai answer as message text() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerCommandImplTest.kt:57
```

**M4**
```
> Task :src:ai:test FAILED
ConversationContextRestAdapterTest > execute requests the last twenty messages before the current message() FAILED
    org.opentest4j.AssertionFailedError at ConversationContextRestAdapterTest.kt:39
```

**M5**
```
> Task :src:ai:test FAILED
ConversationContextRestAdapterTest > execute parses a forwarded message array recursively and collects nested authors() FAILED
    java.util.NoSuchElementException at ConversationContextRestAdapterTest.kt:147
ConversationContextRestAdapterTest > execute parses a single forwarded message object() FAILED
    java.util.NoSuchElementException at ConversationContextRestAdapterTest.kt:168
```

**M6**
```
> Task :src:ai:test FAILED
ConversationContextRestAdapterTest > execute asks user profiles for human authors and group profiles for group authors() FAILED
    org.opentest4j.AssertionFailedError at ConversationContextRestAdapterTest.kt:102
ConversationContextRestAdapterTest > execute parses a forwarded message array recursively and collects nested authors() FAILED
    org.opentest4j.AssertionFailedError at ConversationContextRestAdapterTest.kt:150
```

**M7**
```
> Task :src:ai:test FAILED
ConversationContextRestAdapterTest > execute maps stored messages into chat history and keeps the current message() FAILED
    org.opentest4j.AssertionFailedError at ConversationContextRestAdapterTest.kt:63
```

**M8**
```
> Task :src:ai:test FAILED
ConversationContextRestAdapterTest > execute maps a stored message without text to an empty chat message text() FAILED
    org.opentest4j.AssertionFailedError at ConversationContextRestAdapterTest.kt:83
```

**M9**
```
> Task :src:ai:test FAILED
ConversationContextRestAdapterTest > execute associates returned profiles with their author ids() FAILED
    org.opentest4j.AssertionFailedError at ConversationContextRestAdapterTest.kt:117
```

**M10**
```
> Task :src:ai:test FAILED
GenerateAnswerOutputAdapterTest > execute formats user and group profiles() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerOutputAdapterTest.kt:38
```

**M11**
```
> Task :src:ai:test FAILED
GenerateAnswerOutputAdapterTest > execute formats the current message and nested forwarded blocks with levels and indentation() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerOutputAdapterTest.kt:84
GenerateAnswerOutputAdapterTest > execute formats chat history as message blocks() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerOutputAdapterTest.kt:53
```

**M12**
```
> Task :src:ai:test FAILED
GenerateAnswerOutputAdapterTest > execute falls back to id when a profile is missing() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerOutputAdapterTest.kt:124
```

**M13**
```
> Task :src:ai:test FAILED
GenerateAnswerOutputAdapterTest > execute passes empty profile blocks when no profiles are known() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerOutputAdapterTest.kt:147
```

**M14**
```
> Task :src:ai:test FAILED
GenerateAnswerOutputAdapterTest > execute returns the answer produced by the ai service() FAILED
    org.opentest4j.AssertionFailedError at GenerateAnswerOutputAdapterTest.kt:159
```

**M15**
```
> Task :src:ai:test FAILED
MessageRequireAnswerEventProcessorTest > process reads message json and passes the parsed message to the input port() FAILED
    com.simarel.vkbot.share.domain.vo.ValidationDateNotNullException at MessageRequireAnswerEventProcessorTest.kt:38
```

**M16**
```
> Task :src:ai:test FAILED
MessageRequireAnswerEventProcessorTest > event is MESSAGE_REQUIRE_ANSWER() FAILED
    org.opentest4j.AssertionFailedError at MessageRequireAnswerEventProcessorTest.kt:47
```

После откатов:
```
$ git diff --stat -- 'src/*/src/main'
$ git status --short
 M src/ai/build.gradle.kts
?? .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/
?? src/ai/src/test/
?? src/ai/src/testFixtures/
```
Временных файлов и мутаций production-кода нет; `M src/ai/build.gradle.kts` и новые тестовые каталоги —
ожидаемый результат этой board task.

## 4. Проверки (exit code и вывод)

### 4.1 Весь набор `ai`

```
$ ./gradlew :src:ai:test        # exit 0
> Task :src:ai:test
BUILD SUCCESSFUL in 11s
```
JUnit XML (`src/ai/build/test-results/test/`): `TOTAL 18 failures 0 errors 0 skipped 0`
(`GenerateAnswerUsecaseTest` 1, `GenerateAnswerCommandImplTest` 2,
`ConversationContextRestAdapterTest` 7, `GenerateAnswerOutputAdapterTest` 6,
`MessageRequireAnswerEventProcessorTest` 2).

### 4.2 По классам

```
$ ./gradlew :src:ai:test --tests "*GenerateAnswerUsecaseTest*"              # exit 0, BUILD SUCCESSFUL in 6s
$ ./gradlew :src:ai:test --tests "*GenerateAnswerCommandImplTest*"          # exit 0, BUILD SUCCESSFUL in 6s
$ ./gradlew :src:ai:test --tests "*ConversationContextRestAdapterTest*"     # exit 0, BUILD SUCCESSFUL in 7s
$ ./gradlew :src:ai:test --tests "*GenerateAnswerOutputAdapterTest*"        # exit 0, BUILD SUCCESSFUL in 6s
$ ./gradlew :src:ai:test --tests "*MessageRequireAnswerEventProcessorTest*" # exit 0, BUILD SUCCESSFUL in 7s
```

### 4.3 Архитектурные тесты

```
$ ./gradlew :src:testing:arch-tests:test    # exit 0
> Task :src:testing:arch-tests:test
BUILD SUCCESSFUL in 14s
```
Результаты: `TestDataFactoriesTest` 2/0 failures (фабрики и их дефолты),
`GradleDependenciesTest` 3/0 (test-конфигурации не попали в production-граф),
`LayerDependenciesTest` 5/0, `ModuleDependenciesTest` 7/0, `DetektExcludesPathTest` 1/0.

### 4.4 Стиль

```
$ ./gradlew detekt ktlintCheck    # exit 0
> Task :src:ai:ktlintMainSourceSetCheck
> Task :src:ai:ktlintTestFixturesSourceSetCheck
> Task :src:ai:ktlintTestSourceSetCheck
> Task :src:ai:ktlintCheck
BUILD SUCCESSFUL in 3s
```

### 4.5 Сборка модуля

```
$ ./gradlew :src:ai:build    # exit 0
> Task :src:ai:test
> Task :src:ai:build
BUILD SUCCESSFUL in 7s
```

## 5. Границы и названные пробелы

- AC-6…AC-12 (`conversation-summary`), AC-13…AC-15 (`ai-tools-vane`), AC-16 (`:src:app` Quarkus-уровень),
  AC-17…AC-19 (документы/покрытие) — отдельные board tasks (`summary-unit-tests`, `vane-unit-tests`,
  `quarkus-ai-test`, `docs-and-coverage-record`), в этой задаче не покрываются. Задел для них — фабрики
  `FakeSummaryProvider`, `FakeVaneProvider`, `FakeConversationContextPortProvider`.
- OCR-путь (`RecognizeImagesCommandImpl`, `ImageRecognitionOutputAdapter`) и
  `TransientChatMemoryProvider` — по spec.md §2 вне объёма: недостижимый путь / внутреннее поведение
  LangChain4j без наблюдаемого выхода. Названы как пробелы, а не как pass.
- `Message.forwardedContextString()` — не вызывается из production; отдельная кодовая задача.
- В рамках AC-1…AC-5 непокрытых критериев нет.

## 6. Воспроизводимость

На чистом checkout достаточно одной команды — она компилирует фикстуры, тесты и гоняет весь набор `ai`:

```
./gradlew :src:ai:test
```

Остальные гейты: `./gradlew :src:testing:arch-tests:test`, `./gradlew detekt ktlintCheck`,
`./gradlew :src:ai:build`.
