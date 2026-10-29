# review-summary.md — `quarkus-ai-test`

- **Board task:** `quarkus-ai-test` (board `test-authoring`, stage `review`, round 1)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Ветка / worktree:** `fix/ai-tests` / `.worktrees/fix/ai-tests`
- **Reviewer стадии:** `test-reviewer` (единственный reviewer этой board task; план `reviewers` не добавляет ролей)
- **Диф ревью:** `src/app/build.gradle.kts` (+1, `testImplementation(testFixtures(project(":src:ai")))`),
  `WireMockTestResource.kt` (+1, `quarkus.rest-client.vane.url`), новый
  `AiIntegrationTest.kt` (209 строк, 6 `@Test`). `git diff --stat -- 'src/*/src/main'` пуст.
- **Вердикт:** **approve**, 0 blocking, 0 nit. Board task готов к завершению.

## Вердикты

| Reviewer | Решение | Находки | Привязка |
|---|---|---|---|
| `test-reviewer` | **approve** (round 1) | нет | `write:tests.md` sha256 `ad3fee304f10bbfecef4482ca210f901d713e1a2b4d1d56a826809aa5ecb6719` (совпадает с доставленными байтами) |

Раунд 1: своих предыдущих находок нет. Новых блокирующих находок и нитов нет — список не выдумывался.

## Доказательства гейтов (не перезапускались)

Ledger fresh на дереве `8ff1c9d1bb1a8d8fdd3a3dfe19a099910500afaf2144ccd2e50775fb65edf82e`; ни reviewer,
ни lead не запускали Gradle повторно (команды от свежих записей, не дублировались):

| criterion | command | exit | доказательство |
|---|---|---|---|
| tests (класс) | `./gradlew :src:app:test --tests "*AiIntegrationTest*"` | 0 | `BUILD SUCCESSFUL in 1m 4s`; JUnit XML: `tests=6 failures=0 errors=0` |
| tests (все) | `./gradlew test` | 0 | `BUILD SUCCESSFUL in 2m 12s`; 130 tests / 0 failures, включая `VkWebhookIntegrationTest` 6/6 (WireMock-правка не сломала соседний тест) |
| lint | `./gradlew detekt ktlintCheck` | 0 | `:src:app:ktlintTestSourceSetCheck`, `:src:app:detekt`, `:src:app:ktlintCheck` |
| build | `./gradlew build` | 0 | `BUILD SUCCESSFUL in 6s`, 163 задачи, в составе `:src:app:test`, `:src:ai:test`, `:src:testing:arch-tests:test`, `:capabilitiesCheck`, `:coverageVerification`, `:src:app:quarkusBuild` |
| architecture | `./gradlew :src:testing:arch-tests:test` | 0 | fresh на том же дереве (entries соседних board tasks): `TestDataFactoriesTest` 2/0, `GradleDependenciesTest` 3/0, `LayerDependenciesTest` 5/0, `ModuleDependenciesTest` 7/0, `DetektExcludesPathTest` 1/0 |

Независимо от ledger проверен артефакт прогона на текущем дереве:
`src/app/build/test-results/test/TEST-com.simarel.vkbot.integration.AiIntegrationTest.xml` —
`tests="6" skipped="0" failures="0" errors="0"`, timestamp `2026-10-29T16:41:28.779Z`, присутствуют все
шесть имён testcase; mtime всего каталога `test-results` — 19:42, т.е. это прогон записи `85ac56e0`
(`./gradlew test`), а не остаток прошлой недели. Замечание tests.md §4.1 о промежуточной гонке Gradle за
`test-results` относится к другому, более раннему запуску; финальные записи выше — чистые.

## Покрытие AC-16 (проверено по доставленным байтам, а не по tests.md)

| Фраза AC-16 (spec.md §5) | Тест | Чем доказывается, что тест может упасть |
|---|---|---|
| `GenerateAnswerOutputPort` возвращает ответ модели | #1 `AiIntegrationTest.kt:57-64` | M1: `GenerateAnswerOutputAdapter.kt:27` возвращает `""` → `AssertionFailedError: expected: <Hello! …> but was: <>` |
| запрос к Ollama содержит отформатированные профили/историю/текущее сообщение | #2 `:66-80` | M2: `formatAuthor` → `"unknown"` (`:92-105`) → `VerificationException: No requests exactly matched`; проверяемые строки производятся только `formatUserProfiles:107-112`, `formatGroupProfiles:114-119`, `formatChatHistory:30-42` |
| `SummarizationOutputPort` парсит JSON-ответ | #3 `:82-90` | M3: `SummarizationAiAdapter.kt:27-28` меняет местами short/full → assertion fails |
| повтор при 5xx, 3 попытки порта | #4 `:92-105` | M4: `MAX_RETRIES 3 → 1` (`:10`) → 3 HTTP-запроса вместо 9 → `VerificationException` на `verify(9)` |
| `VaneSearchOutputPort` мапит HTTP-ответ провайдера/поиска | #5 `:107-133` | M5: `VaneSearchOutputAdapter.kt:34` подменяет `content` → assertion fails; `GET /api/providers` + `POST /api/search` — реальные WireMock-границы |
| провайдер без chat/embedding-модели → `IllegalStateException` | #6 `:135-148` | M6: удалён фильтр `VaneSearchOutputAdapter.kt:56-58` → `NoSuchElementException` из `embeddingModels.first()`; текст исключения совпадает с `:18` |

Непокрытых фраз AC-16 нет. `@Tool`-вызовы и вложенные forwarded-блоки — предмет AC-15/AC-4 (другие
board tasks), успех на 2-й попытке порта — AC-10; это названные пробелы tests.md §3, а не пробелы AC-16.

## Проверка на «театральность» — проверено и чисто

- **Не мок тестируемого:** WireMock стоит только на внешней HTTP-границе (Ollama `/api/chat`, Vane
  `/api/providers`, `/api/search`); инжектируются реальные CDI-бины, LangChain4j и MicroProfile REST client
  настоящие, подменены только HTTP-ответы. Утверждения — на выходе адаптеров и на реально отправленном теле
  запроса, не на фейке под тестом.
- **Не может пройти с удалённой фичей:** удаление любой из трёх реализаций портов ломает CDI-инъекцию
  теста; каждая проверка пинует маппинг/формат, который производит production-код.
- **Нет sleep/ordering/сети наружу:** 6 отдельных `@Test` со своими заглушками, `@BeforeEach` →
  `resetAll()`; в `WireMockTestResource.start()` дефолтных заглушек нет, поэтому тесты не зависят от
  порядка и не «протекают» друг в друга.
- **Нет копипасты, которую надо свести в таблицу:** шесть разных наблюдаемых поведений, не вариации
  одного.
- **Данные только из фабрик:** `FakeAnswerProvider`, `FakeSummaryProvider`, `FakeVaneProvider`,
  `FakeModelProvider`, `FakeVoProvider`; сырые JSON-тела — разрешённое исключение для интеграционных
  тестов. Прямого `Type(...)`/`Type.of(...)` в тесте нет.
- **Строка ledger показывает прогон:** fresh-записи с exit code и хвостом + сверенный JUnit XML
  `tests=6 failures=0`; плюс агрегированный `./gradlew test` 130/0 и `./gradlew build` — регресса нет.
- **Test #4 (9 вызовов):** 9 = 3 попытки порта × 3 HTTP-попытки LangChain4j; зелёный прогон это
  подтверждает, а `MAX_RETRIES 3 → 1` даёт 3 вызова и валит `verify`. Отказ от буквального сценария
  «2×500 → 200» обоснован в tests.md §5: он проходит и при `MAX_RETRIES = 1` и потому доказывал бы retry
  библиотеки, а не цикл порта. `assertThrows(RuntimeException)` широк, но не вакуумен: не бросающий порт
  его валит, а точное число запросов доказывает цикл; тип последнего исключения — предмет AC-10.
- **Proof of non-vacuity:** мутации M1–M6 в tests.md §4.2 сверены построчно с production-кодом; каждая
  цитата FAILED следует из описанной мутации. Откат подтверждён пустым `git diff --stat -- 'src/*/src/main'`.

## Проверенные и признанные чистыми классы

`AiIntegrationTest` (все 6 тестов, хелперы `answerRequest`/`stubChatReply`/`jsonResponse`,
изоляция `@BeforeEach`); `WireMockTestResource` (только добавлено переопределение Vane-URL, VK/Ollama
сохранены); `IntegrationTestProfile` и `src/app/src/test/resources/application.yaml` (тест ничего не
отключает и не подменяет бины); production под тестом: `GenerateAnswerOutputAdapter`,
`UserAnswerAiService`, `SummarizationAiAdapter`, `SummarizationAiService`, `VaneSearchOutputAdapter`,
`VaneSearchClient`, контракты трёх портов; фикстуры `FakeAnswerProvider`, `FakeSummaryProvider`,
`FakeVaneProvider`, `FakeModelProvider`, `FakeVoProvider`, `ObjectMapperProvider`; build-граф
`src/app/build.gradle.kts` (зависимость test-only); arch-правила `TestDataFactoriesTest`,
`GradleDependenciesTest`, `ModuleDependenciesTest`, `LayerDependenciesTest` (fresh, exit 0).

## Карта возможностей

Доставленный диф не меняет наблюдаемого поведения (`git diff --stat -- 'src/*/src/main'` пуст) — новой
capability-строки в этом диффе быть не должно. `docs/capabilities/{ai-answer,conversation-summary,ai-tools-vane}.md`
и `index.md` существуют, их entry points указывают на существующий production-код; добавить AC с путями
новых тестов — предмет AC-17 (`docs-and-coverage-record`, заблокирована этой board task). `capabilitiesCheck`
в составе fresh `./gradlew build` зелёный. Блокировки по продуктовому рекорду нет.

## Открытые риски (не блокируют приёмку)

1. **Test #4 пинует 9 запросов** — это сумма двух слоёв retry: 3 попытки порта × 3 HTTP-попытки
   LangChain4j по умолчанию. Апгрейд/настройка `max-retries` изменит число и сломает тест при корректном
   порте; tests.md §5 это фиксирует осознанно.
2. **#4 доказывает поведение «после постоянных 5xx проброшено исключение»**, а не «успех на второй попытке
   без третьей» — второй сценарий покрыт юнит-тестом AC-10 (`SummarizationAiAdapterTest`).
3. **#2 проверяет профили и заголовок блока истории**, а текст текущего сообщения — только на присутствие,
   без заголовка блока; полная форма блока — предмет AC-4 (`GenerateAnswerOutputAdapterTest`).
4. **Статический singleton WireMock** общий для двух `@QuarkusTest`-классов; порядконезависимость держится
   на `@BeforeEach resetAll()` в каждом классе (сегодня оба это делают). Заглушка в `@BeforeAll` будущего
   класса была бы стёрта.
5. **Docker/DevServices обязателен** для `:src:app`-тестов (spec.md §9.1); свежие записи доказывают, что
   здесь он был. В окружении без Docker тест не пройдёт — это честный блокер, а не замена доказательства.
6. **Негативное доказательство M1–M6 записано цитатами в tests.md §4.2**, но не отдельными записями
   ledger (протокол предпочитает пару «упавший/прошедший» gate). Задание этой board task называло местом
   негативного доказательства именно tests.md; цитаты сверены с доставленными строками production-кода и
   непротиворечивы, поэтому блокировки нет. При следующем касании tests.md стоит продублировать мутации в
   `pipeline_gate` с exit code 1 и откатом.

## Итог

AC-16 покрыт шестью невакуумными тестами реальной проводки CDI/LangChain4j/Quarkus REST в единственном
деплое `:src:app`; доказательства свежие, production-код не изменён, регресса нет. **approve.**
