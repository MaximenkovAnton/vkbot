# tests.md — `quarkus-ai-test`

- **Board task:** `quarkus-ai-test` (board `test-authoring`)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Ветка / worktree:** `fix/ai-tests` / `/Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests`
- **Стадия:** `write` (implementation), агент `test-writer`
- **DoD-строка этой board task:** AC-16 (`spec.md` §5, `dod.md`). Остальные строки DoD (`AC-1…AC-15`,
  `AC-17…AC-19`) принадлежат другим board tasks; на момент сдачи их тесты в дереве есть, и полный
  `./gradlew test` их прогоняет (`130` тестов, §4.1).
- **Production-код:** не изменялся; `git diff --stat -- 'src/*/src/main'` пуст (§6).
- **Одна команда всего набора этой board task:** `./gradlew :src:app:test --tests "*AiIntegrationTest*"`
  (нужен Docker: DevServices PostgreSQL/RabbitMQ; см. §7).

## 1. Что изменено

| Файл | Изменение | Зачем |
|---|---|---|
| `src/app/build.gradle.kts` | `testImplementation(testFixtures(project(":src:ai")))` | тесту нужны `create*`-провайдеры `src/ai/src/testFixtures` (провайдеры задачи `ai-answer-unit-and-foundation`); только test-конфигурация, production-граф не меняется |
| `src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt` | новый файл, `@QuarkusTest` + `@TestProfile(IntegrationTestProfile::class)` + `@QuarkusTestResource(WireMockTestResource::class)`, 6 тестов | AC-16: реальная проводка CDI/LangChain4j/Quarkus REST в единственном деплое `:src:app` |
| `src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt` | в `start()` добавлено `"quarkus.rest-client.vane.url" to server!!.baseUrl()` | `@RegisterRestClient(configKey = "vane")` должен ходить в WireMock, а не в `http://localhost:8075`; остальные переопределения сохранены |

`src/ai/**` (production и провайдеры), `docs/**`, чужие тесты не менялись. Новых библиотек нет:
WireMock 3.9.1, JUnit 5, Jackson и test-fixtures уже были в `:src:app`.

Тест инжектирует реальные CDI-бины `GenerateAnswerOutputPort`, `SummarizationOutputPort`,
`VaneSearchOutputPort`. LLM и Vane подменены только на HTTP-границе WireMock-заглушками; LangChain4j,
Quarkus REST client, Jackson и AI-сервисы — настоящие. Запросы строятся только `create*`-фабриками
(`FakeAnswerProvider`, `FakeSummaryProvider`, `FakeVaneProvider`, `FakeVoProvider`, `FakeModelProvider`),
JSON-объекты WireMock сериализуются общим `ObjectMapperProvider`.

## 2. Тесты (один `@Test` на поведение)

Файл: `src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt`

| # | Тест | Проверяемое наблюдаемое поведение |
|---|---|---|
| 1 | `generate answer port returns the answer produced by the model` | `POST /api/chat` возвращает assistant-content → `GenerateAnswerOutputPort.execute(...)` отдаёт этот текст в `answer` |
| 2 | `generate answer port sends formatted profiles, chat history and current message to the model` | тело реального запроса к Ollama содержит форматированные профили (`- id30: Ivan Petrov (@ivan_petrov)`, `- id-40: Test Group (@testgroup)`), блок истории (`ID: 1 | Отправитель: Ivan Petrov (id30)`), текст истории и текст текущего сообщения |
| 3 | `summarization port parses short and full summaries from the model json` | assistant-content с JSON `{"shortSummary":…,"fullSummary":…}` → `SummarizationOutputPort.execute(...)` отдаёт `shortSummary`/`fullSummary` |
| 4 | `summarization port retries model failures and throws after three attempts` | при постоянных 5xx порт делает 3 попытки (каждая — 3 HTTP-вызова LangChain4j, всего 9 `POST /api/chat`) и пробрасывает исключение |
| 5 | `vane port returns the message and sources mapped from the provider response` | `GET /api/providers` (провайдер с chat+embedding) и `POST /api/search` → `VaneSearchOutputPort` отдаёт `message` и `sources` (content/title/url); тело поиска содержит выбранные ключи провайдера, `query`, `sources`, `optimizationMode` |
| 6 | `vane port fails when no provider has both chat and embedding models` | провайдер без embedding-модели → `IllegalStateException("No providers configured in Vane")` |

## 3. Ledger AC → тест → команда → evidence

| AC | Поведение (DoD) | Тест | Файл | Команда |
|---|---|---|---|---|
| AC-16 | `GenerateAnswerOutputPort` возвращает ответ модели | #1 | `.../integration/AiIntegrationTest.kt` | `./gradlew :src:app:test --tests "*AiIntegrationTest*"` |
| AC-16 | запрос к Ollama содержит профили/историю/текущее сообщение | #2 | там же | там же |
| AC-16 | `SummarizationOutputPort` парсит JSON-ответ | #3 | там же | там же |
| AC-16 | повтор при 5xx: 3 попытки порта | #4 | там же | там же |
| AC-16 | `VaneSearchOutputPort` мапит ответ провайдера/поиска | #5 | там же | там же |
| AC-16 | нет провайдера с chat+embedding → `IllegalStateException` | #6 | там же | там же |

Критерий без теста — не pass. В рамках AC-16 не покрыто и названо пробелом:
- **вызов `@Tool`-инструментов моделью** (`VaneSearchToolAdapter`) — заглушка Ollama инструменты не зовёт;
  это предмет AC-15/юнит-теста `VaneSearchToolAdapterTest` (другая board task), не AC-16;
- **вложенные forwarded-блоки промпта ответа** — предмет AC-4 (юнит-тест `GenerateAnswerOutputAdapterTest`);
- **успех на второй попытке порта без третьей** — предмет AC-10 (юнит-тест `SummarizationAiAdapterTest`).

## 4. Evidence

### 4.1 Гейты (свежие записи `pipeline_gate`, дерево `8ff1c9d1bb1a…`)

| Гейт | Команда | Exit | Цитата |
|---|---|---|---|
| tests (класс) | `./gradlew :src:app:test --tests "*AiIntegrationTest*"` | 0 | `BUILD SUCCESSFUL in 1m 4s`; JUnit XML: `tests=6 failures=0 errors=0` |
| tests (весь набор) | `./gradlew test` | 0 | `BUILD SUCCESSFUL in 2m 12s`, `67 actionable tasks: 8 executed, 59 up-to-date`; JUnit XML по всем 9 модулям (все test-задачи перезапущены, mtime 19:41:58–19:42:54): **TOTAL 130 tests, 0 failures, 0 errors, 0 skipped** — `src/ai` 62, `src/testing` 18, `src/app` 13 (в т.ч. `AiIntegrationTest` 6, `VkWebhookIntegrationTest` 6, `InitTest` 1), `src/persistence` 9, `src/receiver` 9, `src/share` 8, `src/vk-facade` 7, `src/processor` 2, `src/infrastructure` 2 |
| lint | `./gradlew detekt ktlintCheck` | 0 | `BUILD SUCCESSFUL in 1s` (`:src:app:detekt`, `:src:app:ktlintTestSourceSetCheck`, `:src:app:ktlintCheck`) |
| build | `./gradlew build` | 0 | `BUILD SUCCESSFUL in 6s`, `163 actionable tasks: 7 executed, 156 up-to-date`; в составе: `:src:app:test`, `:src:ai:test`, `:src:testing:arch-tests:test`, `:capabilitiesCheck`, `:coverageVerification`, `:src:app:quarkusBuild` |

Классовый прогон (полный вывод хвоста):

```
> Task :src:app:test
BUILD SUCCESSFUL in 1m 4s
JUnit XML src/app/build/test-results/test/TEST-com.simarel.vkbot.integration.AiIntegrationTest.xml:
  tests=6 failures=0 errors=0
```

Замечание о воспроизводимости: worktree общий для трёх параллельных test-задач. Один промежуточный
`./gradlew test` упал не на тестах, а на гонке Gradle за каталог результатов
(`java.nio.file.NoSuchFileException: …/build/test-results/test/binary/in-progress-results-*.bin`),
потому что в тот же момент другой запуск в этом worktree пересоздавал `test-results`. Чистый повтор
(каталоги `test-results` удалены, запуск без конкурентов) — зелёный, см. таблицу выше.

### 4.2 Негативное доказательство: каждый тест падает на временной мутации production-кода

Метод: мутация → прогон одного теста → цитата падения → `git checkout -- <файл>` → мутация откатана.
После всех мутаций `git diff --stat -- 'src/*/src/main'` пуст (§6). `exit code` каждого прогона — 1.

| # | Мутация (production) | Упавший тест | Цитата падения |
|---|---|---|---|
| M1 | `GenerateAnswerOutputAdapter`: `GenerateAnswerOutputPortResponse(answer)` → `GenerateAnswerOutputPortResponse("")` | #1 | `AssertionFailedError: expected: <Hello! This is a response from AI.> but was: <>` |
| M2 | `GenerateAnswerOutputAdapter.formatAuthor` → `"unknown"` (убрано форматирование автора) | #2 | `VerificationException: No requests exactly matched`; в теле запроса `ID: 1 \| Отправитель: unknown`, ожидалось `ID: 1 \| Отправитель: Ivan Petrov (id30)` |
| M3 | `SummarizationAiAdapter`: `shortSummary = response.shortSummary` → `= response.fullSummary` | #3 | `AssertionFailedError: expected: <short from model> but was: <full from model>` |
| M4 | `SummarizationAiAdapter`: `MAX_RETRIES = 3` → `1` | #4 | `VerificationException: Expected exactly 9 requests matching the following pattern but received 3` (`urlPath=/api/chat`, `method=POST`) |
| M5 | `VaneSearchOutputAdapter`: `content = it.content` → `content = "mutated"` | #5 | `AssertionFailedError: expected: <source content> but was: <mutated>` |
| M6 | `VaneSearchOutputAdapter`: удалён фильтр `chatModels.isNotEmpty() && embeddingModels.isNotEmpty()` | #6 | `AssertionFailedError: Unexpected exception type thrown, expected: <java.lang.IllegalStateException> but was: <java.util.NoSuchElementException>` |

Полные хвосты прогонов (формат `./gradlew :src:app:test --tests "*AiIntegrationTest.<метод>*"`):

```
M1> AiIntegrationTest > generate answer port returns the answer produced by the model() FAILED
    org.opentest4j.AssertionFailedError: expected: <Hello! This is a response from AI.> but was: <>
M2> AiIntegrationTest > generate answer port sends formatted profiles, chat history and current message to the model() FAILED
    com.github.tomakehurst.wiremock.client.VerificationException: No requests exactly matched.
M3> AiIntegrationTest > summarization port parses short and full summaries from the model json() FAILED
    org.opentest4j.AssertionFailedError: expected: <short from model> but was: <full from model>
M4> AiIntegrationTest > summarization port retries model failures and throws after three attempts() FAILED
    com.github.tomakehurst.wiremock.client.VerificationException: Expected exactly 9 requests ... but received 3
M5> AiIntegrationTest > vane port returns the message and sources mapped from the provider response() FAILED
    org.opentest4j.AssertionFailedError: expected: <source content> but was: <mutated>
M6> AiIntegrationTest > vane port fails when no provider has both chat and embedding models() FAILED
    org.opentest4j.AssertionFailedError: Unexpected exception type thrown, expected: <java.lang.IllegalStateException> but was: <java.util.NoSuchElementException>
```

## 5. Находка: два слоя retry, и почему тест #4 считает 9 запросов

Задание формулировало retry-сценарий как «WireMock 2 раза 500, затем 200 → результат получен, ровно
3 запроса (`MAX_RETRIES=3`)». Проверка показала, что этот сценарий **не доказывает** retry-цикл
`SummarizationAiAdapter`: LangChain4j сам ретраит HTTP-вызов модели (`dev.langchain4j.internal.RetryUtils`,
в логе `A retriable exception occurred. Remaining retries: 2 of 2`), то есть один вызов
`summarizationAiService.generateSummary` делает до 3 HTTP-запросов. Сценарий «2×500 → 200» проходит
и при `MAX_RETRIES = 1` (проверено: тест оставался зелёным) — он доказывает поведение библиотеки, а не
цикл порта.

Поэтому тест #4 написан от наблюдаемого поведения порта: при постоянных 5xx порт делает ровно
`MAX_RETRIES = 3` попытки, каждая попытка — до 3 HTTP-вызовов модели, итого 9 `POST /api/chat`, после
чего пробрасывает последнее исключение. Мутация `MAX_RETRIES 3 → 1` даёт 3 запроса, и тест падает
(M4). Успех на второй попытке без третьей — предмет AC-10 (юнит-тест `SummarizationAiAdapterTest`,
другая board task).

## 6. Чистота дерева

```
$ git diff --stat -- 'src/*/src/main'
(пусто)

$ git status --short
 M src/app/build.gradle.kts
 M src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt
?? src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt
```

(`src/ai/build.gradle.kts`, `src/ai/src/test/**`, `src/ai/src/testFixtures/**`,
`src/testing/test-fixtures/.../FakeEventProvider.kt` в общем worktree — артефакты соседних board tasks,
эта стадия их не трогала.)

## 7. Воспроизведение на чистом checkout

1. `git checkout fix/ai-tests` (worktree `.worktrees/fix/ai-tests`), Docker запущен.
2. `./gradlew :src:app:test --tests "*AiIntegrationTest*"` → exit 0, 6 тестов.
3. `./gradlew test` → exit 0, 130 тестов.
4. `./gradlew detekt ktlintCheck` → exit 0.
5. `./gradlew build` → exit 0.

`@QuarkusTest` в `:src:ai` не запускается (`Task 'integrationTestClasses' not found`, ADR 0002), поэтому
Quarkus-уровень живёт только в `:src:app`; Docker нужен для DevServices PostgreSQL/RabbitMQ — это
ожидаемо (spec.md §9.1), без Docker тест не пройдёт и это честный блокер, а не подмена доказательства.
