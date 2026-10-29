# tests.md — `summary-unit-tests`

- **Board task:** `summary-unit-tests` (board `test-authoring`)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Ветка / worktree:** `fix/ai-tests` / `/Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests`
- **Стадия:** `write` (implementation), агент `test-writer`
- **Production-код:** не изменялся; `git diff --stat -- 'src/*/src/main'` пуст (см. §5).
- **Одна команда всего набора:** `./gradlew :src:ai:test`
- **Раунд 2 (после ревью round 1):** закрыты F1 (blocking, AC-6) и F2 (nit, AC-8):
  `execute skips when the last summary is still pending` теперь передаёт записывающие message-порты и
  утверждает, что выборка сообщений не выполнялась (§2, §3 M9); тест успеха `GenerateSummaryCommandImplTest`
  получает от LLM значения, отличные от дефолтов фабрик (`"llm short"`/`"llm full"`), поэтому сохранение
  pass-through ответа отличимо от констант (§2, §3 M10). Обе мутации воспроизведены на доставленных байтах.

## 1. Что добавлено

### 1.1 Тестовые классы (25 исполнений, все новые)

| Класс | Тестов | Файл |
|---|---|---|
| `ProcessSummaryCommandImplTest` | 11 (9 `@Test` + parameterized ×2) | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt` |
| `GenerateSummaryCommandImplTest` | 7 (5 `@Test` + parameterized ×2) | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt` |
| `SummarizationAiAdapterTest` | 3 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapterTest.kt` |
| `SummaryRequestedEventProcessorTest` | 2 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessorTest.kt` |
| `ProcessSummaryUsecaseTest` | 1 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/ProcessSummaryUsecaseTest.kt` |
| `GenerateSummaryUsecaseTest` | 1 | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/GenerateSummaryUsecaseTest.kt` |

Все — plain JUnit 5 без Quarkus: конструкторы `*CommandImpl` принимают порты/конфиг напрямую,
поэтому `@ConfigProperty`-аннотации не участвуют. Имена — backtick-предложения, фейки пишут вызовы
в `ConcurrentLinkedQueue`, табличные случаи — `@ParameterizedTest` + `@CsvSource`/`@MethodSource`
(`junit-jupiter-params` уже в тестовом classpath через `quarkus-junit5`).

### 1.2 Фабрики тестовых данных

| Файл | Добавлено | Зачем |
|---|---|---|
| `src/testing/test-fixtures/.../testfixtures/domain/FakeEventProvider.kt` | новый `createSummaryRequestedPayload(peerId = 2_000_000_001, conversationMessageId = 5_001)` | тип `share/domain/event/SummaryRequestedPayload` в `src/test` создавать напрямую запрещено |
| `src/ai/src/testFixtures/.../ai/testfixtures/FakeSummaryProvider.kt` | `createProcessSummaryCommandRequest`, `createHasPendingSummaryResponse`, `createFindLastSummaryResponse`, `createFindMessagesBetweenResponse`, `createCreatePendingSummaryResponse`, `createSaveCompletedSummaryResponse`, `createMarkSummaryFailedResponse`, `createSummarizationServiceResponse` | request/response контрактов `**/port/**` и `**/command/**`; каждый параметр — с реалистичным дефолтом |

Переиспользованы без копирования: `FakePersistencePortProvider` (запросы persistence-портов),
`FakeConversationContextPortProvider` (ответы на `FindStoredMessagesBefore`, `FindStoredUser/GroupProfiles`),
`FakeModelProvider` (`StoredMessage`, `Summary`, профили), `FakeVoProvider` (`FromId`),
`ObjectMapperProvider` (JSON для event processor). Внешних зависимостей и нового фреймворка нет.

## 2. Ledger: AC → тест → файл → команда

| AC | Поведение (DoD) | Тест(ы) | Файл | Команда |
|---|---|---|---|---|
| AC-6 | чат не включён → пропуск, `GenerateSummaryCommand` не зовётся | `execute skips a chat that is not enabled` | `ProcessSummaryCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*ProcessSummaryCommandImplTest*"` |
| AC-6 | уже есть `PENDING` → пропуск | `execute skips when a pending summary already exists` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-6 | последнее саммари `PENDING` → пропуск без выборки сообщений и без `GenerateSummaryCommand` | `execute skips when the last summary is still pending` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-6 | конфиг `" 1, 2 ,3 "` парсится (trim), чат считается включённым | `execute recognises enabled chats listed with surrounding spaces` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-7 | пустое окно → пропуск | `execute skips when the message window is empty` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-7 | `size < threshold` **и** прошлое саммари есть → пропуск | `execute skips a short window when a previous summary exists` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-7 | `size < threshold`, но прошлого саммари нет → прогон идёт | `execute summarises a short window when there is no previous summary` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-7 | без прошлого саммари: `FindStoredMessagesBefore(peerId, current+1, batchSize)`, команда получает first/last id | `execute requests messages before the current one when there is no previous summary` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-7 | с прошлым саммари: `FindStoredMessagesBetween(peerId, lastMessageId ?: 0, current, batchSize)` (2 случая: `10 → 10`, `null → 0`; `size == threshold` идёт) | `execute requests messages between the previous summary and the current one` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-7 | исключение из команды проглатывается, ответ возвращается | `execute swallows a generate summary failure` | `ProcessSummaryCommandImplTest.kt` | то же |
| AC-8 | `PENDING` создан первым, затем выборка; успех → `COMPLETED` c short/full **из ответа LLM** (`"llm short"`/`"llm full"`, отличны от дефолтов фабрик), `summaryId` возвращён; limit=1000 | `execute creates the pending summary first and completes it with the llm result` | `GenerateSummaryCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*GenerateSummaryCommandImplTest*"` |
| AC-8 | пустой диапазон → `COMPLETED` с «Нет сообщений» / «Нет сообщений для суммаризации», LLM не зовётся | `execute completes with the no-messages texts and skips the llm when the range is empty` | `GenerateSummaryCommandImplTest.kt` | то же |
| AC-8 | исключение LLM → `markFailed(summaryId)` + rethrow того же исключения | `execute marks the summary failed and rethrows when the llm fails` | `GenerateSummaryCommandImplTest.kt` | то же |
| AC-9 | `[автор]: текст` через `\n\n`; имя пользователя и имя группы (`id = -fromId`) | `execute formats messages as author and text separated by a blank line` | `GenerateSummaryCommandImplTest.kt` | то же |
| AC-9 | профили пользователей и групп запрошены раздельно (id с нужным знаком) | `execute asks for user and group profiles separately` | `GenerateSummaryCommandImplTest.kt` | то же |
| AC-9 | fallback «Пользователь N» / «Группа N» (2 случая) | `execute falls back to placeholders when a profile is missing` | `GenerateSummaryCommandImplTest.kt` | то же |
| AC-10 | успех с 1-й попытки — 1 вызов LLM, ответ смаплен | `execute returns the summary on the first attempt` | `SummarizationAiAdapterTest.kt` | `./gradlew :src:ai:test --tests "*SummarizationAiAdapterTest*"` |
| AC-10 | успех со 2-й попытки — 2 вызова, ответ со 2-й попытки | `execute retries once after a failure and returns the second response` | `SummarizationAiAdapterTest.kt` | то же |
| AC-10 | три падения — 3 вызова и проброс последнего исключения | `execute retries three times and rethrows the last failure` | `SummarizationAiAdapterTest.kt` | то же |
| AC-11 | JSON `SUMMARY_REQUESTED` → `ProcessSummaryInputPortRequest(peerId, conversationMessageId)` | `process reads the payload json and passes the identifiers to the input port` | `SummaryRequestedEventProcessorTest.kt` | `./gradlew :src:ai:test --tests "*SummaryRequestedEventProcessorTest*"` |
| AC-11 | `event()` = `SUMMARY_REQUESTED` | `event is SUMMARY_REQUESTED` | `SummaryRequestedEventProcessorTest.kt` | то же |
| AC-12 | `ProcessSummaryUsecase` передаёт peerId/conversationMessageId в команду, возвращает ответ | `execute passes the identifiers to the command and returns the response` | `ProcessSummaryUsecaseTest.kt` | `./gradlew :src:ai:test --tests "*SummaryUsecaseTest*"` |
| AC-12 | `GenerateSummaryUsecase` передаёт три id в команду, возвращает `summaryId` | `execute passes the identifiers to the command and returns its summary id` | `GenerateSummaryUsecaseTest.kt` | то же |

### 2.1 Evidence по классам

Все 25 новых исполнений — exit 0, `BUILD SUCCESSFUL`, 0 failures/0 errors в JUnit XML:

```
$ ./gradlew :src:ai:test --tests "*ProcessSummaryCommandImplTest*"     # exit 0, BUILD SUCCESSFUL in 7s
  ProcessSummaryCommandImplTest: 11 tests, 0 failures, 0 errors
$ ./gradlew :src:ai:test --tests "*GenerateSummaryCommandImplTest*"    # exit 0, BUILD SUCCESSFUL in 7s
  GenerateSummaryCommandImplTest: 7 tests, 0 failures, 0 errors
$ ./gradlew :src:ai:test --tests "*SummarizationAiAdapterTest*"        # exit 0, BUILD SUCCESSFUL in 7s
  SummarizationAiAdapterTest: 3 tests, 0 failures, 0 errors
$ ./gradlew :src:ai:test --tests "*SummaryRequestedEventProcessorTest*" # exit 0, BUILD SUCCESSFUL in 8s
  SummaryRequestedEventProcessorTest: 2 tests, 0 failures, 0 errors
$ ./gradlew :src:ai:test --tests "*SummaryUsecaseTest*"                # exit 0, BUILD SUCCESSFUL in 7s
  GenerateSummaryUsecaseTest: 1 tests, 0 failures, 0 errors
  ProcessSummaryUsecaseTest: 1 tests, 0 failures, 0 errors
```

### 2.2 Весь набор `ai`

```
$ ./gradlew :src:ai:test        # exit 0
> Task :src:ai:test
BUILD SUCCESSFUL in 8s
```

JUnit XML: 25 тестов задачи `summary-unit-tests` — 0 failures/0 errors; остальные классы модуля
(`ai-answer` — 18 из board task `ai-answer-unit-and-foundation`, плюс тесты параллельной board task
`vane-unit-tests`, которые пишутся в это же общее worktree) — тоже 0 failures. Собственных падений нет.

## 3. Негативное доказательство: тесты не вакуумные

Каждая мутация — временная правка одного production-файла, прогон класса, затем полный откат.
Минимум одна мутация на тестовый класс (DoD §Правила доказательства п.2); M9 и M10 добавлены в раунде 2
и закрывают находки ревью F1/F2. После всех откатов
`git status --short -- src/ai/src/main` пуст и `git diff --stat -- 'src/*/src/main'` пуст (§5).

| # | Файл | Мутация | Упавшие тесты | Цитата |
|---|---|---|---|---|
| M1 | `ProcessSummaryCommandImpl.kt` | проверка `hasPending` нейтрализована (`).hasPending && false`) | `execute skips when a pending summary already exists` | см. ниже |
| M2 | `ProcessSummaryCommandImpl.kt` | порог `messages.size < summaryThreshold` → `>` | `execute skips a short window when a previous summary exists` | см. ниже |
| M3 | `SummarizationAiAdapter.kt` | `MAX_RETRIES = 3` → `1` | `execute retries once after a failure...`, `execute retries three times and rethrows the last failure` | см. ниже |
| M4 | `GenerateSummaryCommandImpl.kt` | вызов `markSummaryAsFailedPort.execute(...)` → `Unit` | `execute marks the summary failed and rethrows when the llm fails` | см. ниже |
| M5 | `GenerateSummaryCommandImpl.kt` | разделитель `joinToString("\n\n")` → `"\n"` | `execute formats messages as author and text separated by a blank line` | см. ниже |
| M6 | `SummaryRequestedEventProcessor.kt` | `peerId`/`conversationMessageId` переставлены местами | `process reads the payload json and passes the identifiers to the input port` | см. ниже |
| M7 | `GenerateSummaryUsecase.kt` | `firstMessageId = request.lastMessageId` | `execute passes the identifiers to the command and returns its summary id` | см. ниже |
| M8 | `ProcessSummaryUsecase.kt` | второй id заменён на `0` | `execute passes the identifiers to the command and returns the response` | см. ниже |
| M9 | `ProcessSummaryCommandImpl.kt` | удалён блок `if (lastSummary?.status == SummaryStatus.PENDING) { … }` | `execute skips when the last summary is still pending` | см. ниже |
| M10 | `GenerateSummaryCommandImpl.kt` | pass-through LLM заменён литералами `"short summary"`/`"full summary"` в `SaveStoredCompletedSummaryPort.Request` | `execute creates the pending summary first and completes it with the llm result` | см. ниже |

**M1**
```
> Task :src:ai:test FAILED
ProcessSummaryCommandImplTest > execute skips when a pending summary already exists() FAILED
    org.opentest4j.AssertionFailedError at ProcessSummaryCommandImplTest.kt:62
BUILD FAILED in 7s
```

**M2**
```
> Task :src:ai:test FAILED
ProcessSummaryCommandImplTest > execute skips a short window when a previous summary exists() FAILED
    org.opentest4j.AssertionFailedError at ProcessSummaryCommandImplTest.kt:111
BUILD FAILED in 7s
```

**M3**
```
> Task :src:ai:test FAILED
SummarizationAiAdapterTest > execute retries three times and rethrows the last failure() FAILED
    org.opentest4j.AssertionFailedError at SummarizationAiAdapterTest.kt:56
SummarizationAiAdapterTest > execute retries once after a failure and returns the second response() FAILED
BUILD FAILED in 7s
```

**M4**
```
> Task :src:ai:test FAILED
GenerateSummaryCommandImplTest > execute marks the summary failed and rethrows when the llm fails() FAILED
    java.util.NoSuchElementException at GenerateSummaryCommandImplTest.kt:129
7 tests completed, 1 failed
BUILD FAILED in 7s
```

**M5**
```
> Task :src:ai:test FAILED
GenerateSummaryCommandImplTest > execute formats messages as author and text separated by a blank line() FAILED
    org.opentest4j.AssertionFailedError at GenerateSummaryCommandImplTest.kt:155
7 tests completed, 1 failed
BUILD FAILED in 7s
```

**M6**
```
> Task :src:ai:test FAILED
SummaryRequestedEventProcessorTest > process reads the payload json and passes the identifiers to the input port() FAILED
    org.opentest4j.AssertionFailedError at SummaryRequestedEventProcessorTest.kt:33
2 tests completed, 1 failed
```
```
org.opentest4j.AssertionFailedError: expected: <ProcessSummaryInputPortRequest(peerId=2000000001, conversationMessageId=7777)>
 but was: <ProcessSummaryInputPortRequest(peerId=7777, conversationMessageId=2000000001)>
```

**M7**
```
> Task :src:ai:test FAILED
GenerateSummaryUsecaseTest > execute passes the identifiers to the command and returns its summary id() FAILED
    org.opentest4j.AssertionFailedError at GenerateSummaryUsecaseTest.kt:32
1 test completed, 1 failed
```
```
org.opentest4j.AssertionFailedError: expected: <GenerateSummaryRequest(peerId=2000000001, firstMessageId=10, lastMessageId=20)>
 but was: <GenerateSummaryRequest(peerId=2000000001, firstMessageId=20, lastMessageId=20)>
```

**M8**
```
> Task :src:ai:test FAILED
ProcessSummaryUsecaseTest > execute passes the identifiers to the command and returns the response() FAILED
    org.opentest4j.AssertionFailedError at ProcessSummaryUsecaseTest.kt:27
1 test completed, 1 failed
```
```
org.opentest4j.AssertionFailedError: expected: <ProcessSummaryRequest(peerId=2000000001, conversationMessageId=7777)>
 but was: <ProcessSummaryRequest(peerId=2000000001, conversationMessageId=0)>
```

**M9** (без PENDING-пропуска прогон доходит до `FindStoredMessagesBetween` — записывающий фейк его ловит)
```
> Task :src:ai:test FAILED
ProcessSummaryCommandImplTest > execute skips when the last summary is still pending() FAILED
    org.opentest4j.AssertionFailedError at ProcessSummaryCommandImplTest.kt:88
11 tests completed, 1 failed
BUILD FAILED in 6s
```

**M10** (сохранение литералов вместо ответа LLM; в фейке LLM `"llm short"`/`"llm full"`)
```
> Task :src:ai:test FAILED
GenerateSummaryCommandImplTest > execute creates the pending summary first and completes it with the llm result() FAILED
    org.opentest4j.AssertionFailedError at GenerateSummaryCommandImplTest.kt:83
7 tests completed, 1 failed
BUILD FAILED in 6s
```

После откатов:

```
$ git status --short -- src/ai/src/main      # пусто
$ git diff --stat -- 'src/*/src/main'        # пусто
```

## 4. Проверки (exit code и вывод)

### 4.1 Тесты

```
$ ./gradlew :src:ai:test        # exit 0
> Task :src:ai:test
BUILD SUCCESSFUL in 8s
```
JUnit XML на прогоне: 25 тестов этой задачи (0 failures/0 errors) и 62 теста модуля всего
(0 failures/0 errors) — остальные классы добавили board tasks `ai-answer-unit-and-foundation`
и параллельная `vane-unit-tests`.

### 4.2 По классам

См. §2.1: пять команд `./gradlew :src:ai:test --tests "*<ClassName>*"` — все exit 0,
`BUILD SUCCESSFUL`.

### 4.3 Архитектурные тесты

```
$ ./gradlew :src:testing:arch-tests:test    # exit 0
> Task :src:testing:arch-tests:test
BUILD SUCCESSFUL in 12s
```
`TestDataFactoriesTest` 2/0 failures (новые фабрики с дефолтами, прямого создания контрактных
типов в `src/test/**` нет), `GradleDependenciesTest` 3/0, `LayerDependenciesTest` 5/0,
`ModuleDependenciesTest` 7/0, `DetektExcludesPathTest` 1/0.

### 4.4 Стиль

```
$ ./gradlew detekt ktlintCheck    # exit 0
BUILD SUCCESSFUL in 4s
```

### 4.5 Сборка модуля

```
$ ./gradlew :src:ai:build    # exit 0
> Task :src:ai:test
> Task :src:ai:build
BUILD SUCCESSFUL
```

## 5. Границы и названные пробелы

- В рамках AC-6…AC-12 непокрытых критериев нет.
- Не входит в эту board task и названо как пробел: `SummaryReadyEventProcessor` (vk-facade,
  forward-блок на диапазон), `handleSaveCompletedSummary`/публикация `SUMMARY_READY` (persistence),
  AC-16 (`:src:app` Quarkus-проводка), AC-17…AC-19 (документы/покрытие) — это board tasks
  `quarkus-ai-test` и `docs-and-coverage-record`.
- Пустой `summary.enabled-chats` не тестируется: production-конфиг требует непустой
  `SUMMARY_ENABLED_CHATS` (Quarkus не стартует с пустым значением, ADR 0006), поэтому «выключено по
  умолчанию» недостижимо; парсинг списка с пробелами проверен.
- OCR-путь и `TransientChatMemoryProvider` — вне объёма по spec.md §2.
- `ProcessSummaryCommandImpl` ловит исключение `GenerateSummaryCommand`; это поведение покрыто
  тестом на проглатывание, а не проверкой логов (лог — ненаблюдаемое для пользователя).

## 6. Воспроизводимость

На чистом checkout одной команды достаточно, чтобы прогнать весь набор `ai` (фикстуры компилируются
как часть `test`):

```
./gradlew :src:ai:test
```

Остальные гейты: `./gradlew :src:testing:arch-tests:test`, `./gradlew detekt ktlintCheck`,
`./gradlew :src:ai:build`.
