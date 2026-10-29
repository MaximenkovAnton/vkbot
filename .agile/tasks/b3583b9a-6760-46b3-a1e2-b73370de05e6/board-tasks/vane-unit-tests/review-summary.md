# review-summary.md — `vane-unit-tests`

- **Board task:** `vane-unit-tests` (board `test-authoring`, stage `review`, round 1)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Reviewer:** `test-reviewer` (единственный агент стадии; вердикт `5b16050c`, round 1, привязан к
  `tests.md` sha256 `e370eedbe7383d7dd34184d9b4d6540e82a155124cb225df701036da815f548b`)
- **Диф ревью:** 3 тестовых класса (19 тестов: 8 + 8 + 3) и +4 фабрики в `FakeVaneProvider.kt`;
  `src/*/src/main` не изменён. Проверено по доставленным байтам, не по намерению.
- **Итог:** **approve**, 0 blocking, 2 nit. Board task готов к завершению; находки — polish и передача
  следующей задаче DAG, раунд доработки не нужен.

## Доказательства гейтов (не перезапускались)

Ledger fresh на дереве `8ff1c9d1bb1a8d8fdd3a3dfe19a099910500afaf2144ccd2e50775fb65edf82e`; review не
запускал Gradle и не мутировал `src/**`:

| criterion | command | exit | доказательство из ledger |
|---|---|---|---|
| tests | `./gradlew :src:ai:cleanTest :src:ai:test` | 0 | `BUILD SUCCESSFUL in 7s`; JUnit XML `src/ai`: TOTAL 62, failures 0, errors 0; `VaneSearchCommandImplTest 8/0`, `VaneSearchOutputAdapterTest 8/0`, `VaneSearchToolAdapterTest 3/0` |
| tests | `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` | 0 | XML `tests=8 failures=0 errors=0` |
| tests | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` | 0 | XML `tests=8 failures=0 errors=0` |
| tests | `./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"` | 0 | XML `tests=3 failures=0 errors=0` |
| architecture | `./gradlew :src:testing:arch-tests:cleanTest :src:testing:arch-tests:test` | 0 | 18/18, в т.ч. `TestDataFactoriesTest 2/0` (скан `src/ai/src/testFixtures` перечитан принудительно) |
| lint | `./gradlew detekt ktlintCheck` | 0 | `BUILD SUCCESSFUL in 688ms`, 89/89 up-to-date |
| build | `./gradlew :src:ai:build --rerun-tasks` | 0 | `BUILD SUCCESSFUL in 10s`, 28 executed |
| tests/build (task-wide) | `./gradlew test`, `./gradlew build` | 0 | 130 тестов / 0 failures; `BUILD SUCCESSFUL` |

Счётчики сверены независимо с JUnit XML на диске: 14 suite, 62 теста, 0 failures, 0 errors, 0 skipped;
по классам задачи — 8 / 8 / 3. Заявление tests.md §3.2 «62 теста модуля» верно.

## Покрытие AC (по доставленным байтам)

| AC | Тест | Что доказано и почему может упасть |
|---|---|---|
| AC-13 | `VaneSearchCommandImplTest.kt:17-32` (3 строки) | Web/Academic/Discussion → `sources`, `optimizationMode`, `query` в `VaneSearchRequest` порта; ровно один вызов (`requests.single()`). Смена любой константы (`:35-50` в production) валит строку — негативно подтверждено мутацией §4.1 на `:30`. |
| AC-13 | `VaneSearchCommandImplTest.kt:34-63` (3 строки) | точные байты ответа: `message`, пустая строка, `--- <label> ---`, `[n] title: url` по порядку; подписи `Sources`/`Academic Sources`/`Discussion Sources`. |
| AC-13 | `VaneSearchCommandImplTest.kt:65-76` | пустые `sources` → только `"Vane answer\n"`, блока нет. |
| AC-13 | `VaneSearchCommandImplTest.kt:78-87` | исключение порта → `"Search failed: vane is down"`, без проброса (мутация `catch (e: Error)` дала FAILED, §4.2). |
| AC-14 | `VaneSearchOutputAdapterTest.kt:16-33` | выбран первый провайдер с обоими списками: в данных `chat-only` (embedding=null), `embedding-only` (chat=[]), `complete`, `later`; проверены оба `providerId` = `complete` и единственный поиск. |
| AC-14 | `VaneSearchOutputAdapterTest.kt:35-72` | в клиентский запрос уходят ключи **первых** моделей (`chat-first` / `embedding-first`), `optimizationMode`/`sources`/`query` переносятся; сравнение полного `VaneSearchRequest`. |
| AC-14 | `VaneSearchOutputAdapterTest.kt:74-118` | `message` и каждый `source` (`content` + `metadata.title` + `metadata.url`) смаплены в порт; полное равенство ответа (мутация §4.4 на `:100`). |
| AC-14 | `VaneSearchOutputAdapterTest.kt:120-132` | `sources = null` → пустой список. |
| AC-14 | `VaneSearchOutputAdapterTest.kt:134-147` | нет подходящего провайдера → `IllegalStateException("No providers configured in Vane")` и **ни одного** поиска (`requests.isEmpty()`); строки: пустой список, `chatModels=null`, `embeddingModels=null`, `embeddingModels=[]`. Мутация «убрать фильтр» валит строки [2]–[4] (NPE-путь даёт `emptyList()` → `NoSuchElementException`), [1] остаётся зелёной — ровно как предсказывает код. |
| AC-15 | `VaneSearchToolAdapterTest.kt:16-29,42-63` (3 строки) | каждый `@Tool`-метод делегирует с точным sealed-запросом `WebSearch`/`AcademicSearch`/`DiscussionSearch(query)` и возвращает `result`; строки различаются типом запроса и результатом (мутация §4.5 на `:27`). |

Непокрытых фраз AC-13…AC-15 нет. Каждое утверждение — на записанный вызов/результат production-класса,
а не на мок тестируемого; ассертов, которые не могут упасть, нет.

## Негативное доказательство

Мутации не перезапускались: это временно меняет общий worktree и обнулило бы fresh-гейты, а по
протоколу fresh-доказательство повторно не запускается. Вместо этого цитаты §4 сверены статически с
доставленными байтами:

- сайт мутации §4.1 — `VaneSearchCommand.kt:41` (`listOf("academic")`), падение на `:30` — это как раз
  `assertEquals(expectedSources, actual.sources)`;
- §4.2 — `:80` это место создания `IllegalStateException("vane is down")`; при `catch (e: Error)` он
  выходит наружу, тест падает до `:86`;
- §4.3 — `:28`/`:142` — вызовы `execute`, из которых NSE выходит при убранном фильтре; строки [2]–[4]
  падают, [1] проходит (пустой список → тот же ISE) — совпадает с цитатой;
- §4.4 — `:100` — начало `assertEquals` смапленного ответа;
- §4.5 — `:27` — `assertEquals(expectedRequest, command.requests.single())`.

Строки, типы исключений и паттерн падений согласуются с кодом; следов мутаций в дереве нет
(`git diff --stat -- 'src/*/src/main'` пуст, `git status --porcelain -- 'src/*/src/main'` пуст).

## Находки

### N1 (nit) — таблица `tools()` несёт 4-е значение, которое тест с 3 параметрами игнорирует

- **Требование:** каждая строка параметризованной таблицы должна значить ровно то, что проверяет
  объявивший её тест; общий positional-индекс между двумя тестами — хрупкая связь.
- **Evidence:** `VaneSearchCommandImplTest.kt:17-32` объявляет 3 параметра, а `tools()`
  (`VaneSearchCommandImplTest.kt:106-125`) даёт по 4 значения на строку. JUnit молча отбрасывает 4-е:
  display name в JUnit XML для строк источников — `[1] WebSearch(query=kotlin coroutines), [web],
  "balanced"`. Значение-подпись используется только через `toolLabels()` (`:128`) по позиционному
  индексу `it.get()[3]`. Перестановка/вставка колонки не «порвёт тихо» — тест подписи упадёт, — но
  таблица имеет двух потребителей разной формы и неявную колонку.
- **Required change (при следующем касании):** разделить источник на 3-значный для теста
  sources/mode и отдельный `(request, label)` для теста подписи, либо принять полную 4-значную строку
  обоими тестами. На доказательность AC не влияет, блокировки нет.

### N2 (nit) — строка карты возможностей `ai-tools-vane` стала ложной по тестам

- **Требование:** продуктовый рекорд не должен утверждать неверное о доставленном дереве.
- **Evidence:** `docs/capabilities/ai-tools-vane.md:55-65` всё ещё говорит «Нет AC с тестом … в модуле
  `ai` тестов нет» и перечисляет `VaneSearchCommand`/`VaneSearchOutputAdapter` как непокрытые, тогда
  как этот диф их покрывает (8/8/3). По DAG файл принадлежит задаче `docs-and-coverage-record`
  (AC-17), которая стартует после всех тестовых задач, поэтому это не дефект диффа этой board task.
- **Required change:** ничего в этой задаче; задача `docs-and-coverage-record` добавляет
  AC-13/14/15 с путями трёх тестов и убирает два пункта «Пробелы приёмки».

## Проверено и чисто

Классы/файлы: `VaneSearchCommandImplTest`, `VaneSearchOutputAdapterTest`, `VaneSearchToolAdapterTest`,
`FakeVaneProvider` (все 15 фабрик с дефолтами, 4 новые командные фабрики — единственное место создания
`VaneSearchCommandRequest.*`/`VaneSearchCommandResponse` в `src/*/src/test`, дублей фабрик нет);
production под тестом — `VaneSearchCommand.kt:31-77`, `VaneSearchOutputAdapter.kt:15-58`,
`VaneSearchToolAdapter.kt:10-32`, `VaneSearchClient.kt`, `VaneSearchOutputPort.kt`; гейт
`TestDataFactoriesTest` (fresh, 2/2); JUnit XML на диске (счётчики); точка входа карты возможностей
(все пути `ai-tools-vane` резолвятся). Театральности не найдено: нет mock-фреймворка, sleep, сети,
зависимости от порядка, нечитаемых снапшотов; фейки — приватные `Recording*` с `ConcurrentLinkedQueue`;
данные — только `create*`-фабрики; комментариев в доставленных файлах нет; внешних зависимостей нет.

## Открытые риски (не блокируют)

1. **Совпадение тестовой query с дефолтом фабрики.** AC-13 `query` проверяется значением
   `"kotlin coroutines"` — тем же, что дефолт `FakeVaneProvider`; гипотетический хардкод этой строки в
   production остался бы зелёным. Неплаузибельная правка, находкой не считаю; отдельный литерал в одной
   строке снял бы совпадение.
2. **Позиционное значение подписи** — следствие N1; отдельного риска, кроме читаемости, нет.
3. **Устаревание карты** до `docs-and-coverage-record` — N2; задача уже в DAG и заблокирована на этой.
4. **Обычный `./gradlew :src:ai:test` слишком слаб как доказательство**: его цитата §3.1 — UP-TO-DATE
   прогон. Честность не нарушена: в ledger fresh лежат принудительный `:src:ai:cleanTest :src:ai:test`
   и агрегатный `./gradlew test` (переисполнил все тестовые задачи). На будущее — цитировать только
   исполнявшийся прогон.

## Capability record

Изменение не добавляет и не меняет наблюдаемого поведения (только тесты и test-fixtures), новые
capability-строки не требуются. Точки входа `ai-tools-vane` не изменились и резолвятся; устаревший
раздел AC/«Пробелы приёмки» этого файла — N2, передано `docs-and-coverage-record` (AC-17).

**Решение:** approve. 0 blocking, 2 nit; доработка не требуется.
