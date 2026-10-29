# impl.md — resolve-hidden-findings

## Контракт (AC из board-task spec, зафиксированы до правок кода)

- **AC-1.** КОГДА `./gradlew detekt ktlintCheck` выполняется в чек-ауте без сегмента `test` в пути
  (CI-эквивалент `/tmp/vk-lint-check`), СИСТЕМА ДОЛЖНА завершиться exit 0 без находок.
- **AC-4.** КОГДА запускается полный набор тестов (`./gradlew build`), СИСТЕМА ДОЛЖНА сохранить все
  существующие тесты зелёными: exit 0.
- **AC-5.** КОГДА изменения влиты, СИСТЕМА ДОЛЖНА сохранять карту возможностей и ADR в соответствии
  с кодом: `./scripts/check-capabilities.sh` — exit 0.
- **AC-6.** КОГДА изменения влиты, СИСТЕМА ДОЛЖНА сохранять имена `search_web`, `search_academic`,
  `search_discussions`: `grep -n "fun search_" .../VaneSearchToolAdapter.kt` — три строки.

AC-2 (анкоринг `excludes` в `detekt-config.yml`) и AC-3 (сторож `DetektExcludesPathTest`) относятся к
следующей задаче `make-lint-path-independent` и в этой задаче не реализуются: `detekt-config.yml` и
`src/testing/arch-tests/**` не тронуты.

## 1. Изменённые файлы

| Файл | Почему |
|---|---|
| `src/ai/.../command/summary/GenerateSummaryCommandImpl.kt` | `@Suppress("TooGenericExceptionCaught")` на `override fun execute`; `catch (e: Exception)` оставлен (пункт 1 спеки) |
| `src/ai/.../command/summary/ProcessSummaryCommandImpl.kt` | то же на `override fun execute` (пункт 2) |
| `src/ai/.../command/vane/VaneSearchCommand.kt` | то же на `private fun executeSearch`; деградация в `"Search failed: …"` сохранена (пункт 3) |
| `src/ai/.../adapter/output/ai/summarization/SummarizationAiAdapter.kt` | то же на `override fun execute`; 3 попытки и проброс сохранены (пункт 4) |
| `src/ai/.../adapter/output/ai/tool/VaneSearchToolAdapter.kt` | `@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")`; имена `search_*` не менялись (пункт 5) |
| `src/ai/.../adapter/output/ai/TransientChatMemoryProvider.kt` | число `10` вынесено в файловую `private const val MAX_MESSAGES = 10`; значение то же (пункт 6) |
| `src/app/.../app/Application.kt` | `@Suppress("SpreadOperator")` на `fun main`; `Quarkus.run(*args)` оставлен (пункт 7) |
| `src/infrastructure/.../mq/adapter/input/EventDispatcher.kt` | `@Suppress("TooGenericExceptionCaught")` на `fun dispatch`; любая ошибка по-прежнему уходит в retry-лестницу (пункт 8) |
| `src/persistence/.../command/fetchuserprofile/FetchUserProfileCommandImpl.kt` | `@Suppress("TooGenericExceptionCaught")` на `fetchAndSaveUserProfiles` и `fetchAndSaveGroupProfiles` (пункт 9) |
| `src/testing/test-fixtures/.../domain/FakeVoProvider.kt` | три границы `Random.nextLong` вынесены в `private const val … : Long`; значения не менялись (пункт 10) |
| `src/testing/test-fixtures/detekt-baseline.xml` | новый baseline: ровно одна запись `TooManyFunctions` (пункт 11) |
| `docs/adr/0005-quality-gates-and-ci.md` | «8 baseline-файлов» → «9», в перечень добавлен `src/testing/test-fixtures` (пункт 12) |
| `docs/capabilities/ai-answer.md` | `TransientChatMemoryProvider.kt:9-16` → `:11-18` (пункт 13) |
| `docs/capabilities/conversation-summary.md` | диапазоны `ProcessSummaryCommandImpl.kt:37-126`, `GenerateSummaryCommandImpl.kt:27-90`, `SummarizationAiAdapter.kt:17-42` (пункт 13) |
| `docs/capabilities/ai-tools-vane.md` | `VaneSearchCommand.kt:31-76` → `:31-77` (пункт 13) |
| `src/ai/detekt-baseline.xml` | **вынужденная синхронизация одной подписи `LongMethod`**: detekt включает аннотацию в подпись (отклонение, см. §3) |

Не тронуты: `detekt-config.yml`, `.github/**`, `src/testing/arch-tests/**`, существующие baseline
остальных модулей, логика/сигнатуры/конфигурация.

## 2. Команды и вывод

### 2.1 CI-эквивалентная копия

```console
$ rsync -a --exclude build --exclude .git --exclude .gradle --exclude .idea \
    --exclude .ast-index --exclude .kotlin --exclude .agile --exclude .DS_Store ./ /tmp/vk-lint-check/
$ realpath /tmp/vk-lint-check
/private/tmp/vk-lint-check
$ case "$(realpath /tmp/vk-lint-check)" in *"/test/"*) echo YES;; *) echo NO;; esac
NO
```

### 2.2 Генерация baseline `test-fixtures` (только в копии, не локально)

```console
$ cd /tmp/vk-lint-check && ./gradlew :src:testing:test-fixtures:detektBaseline --console=plain
> Task :src:testing:test-fixtures:detektBaseline
BUILD SUCCESSFUL in 850ms
1 actionable task: 1 executed
EXIT:0
```

```xml
<?xml version="1.0" ?>
<SmellBaseline>
  <ManuallySuppressedIssues/>
  <CurrentIssues>
    <ID>TooManyFunctions:FakeVoProvider.kt$FakeVoProvider</ID>
  </CurrentIssues>
</SmellBaseline>
```

Ровно `TooManyFunctions`; `MagicNumber` исчезли после выноса констант.

### 2.3 Первый прогон AC-1 вскрыл несовместимость подписи baseline

```console
$ cd /tmp/vk-lint-check && ./gradlew detekt ktlintCheck --console=plain
> Task :src:ai:detekt FAILED
/private/tmp/vk-lint-check/src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt:37:18:
The function execute is too long (62). The maximum length is 60. [LongMethod]
> Analysis failed with 1 weighted issues.
BUILD FAILED in 875ms
EXIT:1
```

Причина: подпись `LongMethod` в baseline включает аннотации, поэтому добавленный `@Suppress`
перестал совпадать со старой записью. Диф сгенерированного в нейтральном пути baseline:

```console
$ diff -u /tmp/ai-baseline-old.xml /tmp/vk-lint-check/src/ai/detekt-baseline.xml
-    <ID>LongMethod:ProcessSummaryCommandImpl.kt$ProcessSummaryCommandImpl$override fun execute(request: ProcessSummaryCommand.ProcessSummaryRequest): ProcessSummaryCommand.ProcessSummaryResponse</ID>
+    <ID>LongMethod:ProcessSummaryCommandImpl.kt$ProcessSummaryCommandImpl$@Suppress("TooGenericExceptionCaught") override fun execute(request: ProcessSummaryCommand.ProcessSummaryRequest): ProcessSummaryCommand.ProcessSummaryResponse</ID>
```

Различие ровно в одной строке — синхронизирована, остальные записи baseline не менялись.

### 2.4 AC-1 — финальный прогон (принудительный, чтобы задачи реально выполнились)

```console
$ cd /tmp/vk-lint-check && ./gradlew detekt ktlintCheck --rerun-tasks --console=plain
> Task :src:ai:detekt
> Task :src:app:detekt
> Task :src:infrastructure:detekt
> Task :src:persistence:detekt
> Task :src:vk-facade:detekt
> Task :src:testing:test-fixtures:detekt
…
BUILD SUCCESSFUL in 4s
83 actionable tasks: 83 executed
EXIT:0
```

Находок в выводе нет; в отчётах `## Findings (0)`, например
`/tmp/vk-lint-check/src/ai/build/reports/detekt/detekt.md` и
`/tmp/vk-lint-check/src/testing/test-fixtures/build/reports/detekt/detekt.md` — `## Findings (0)`.

### 2.5 AC-4 — полная сборка в копии

Первая попытка упала на компиляции (константы вывели тип `Int`, а `Random.nextLong` требует `Long`):

```console
> Task :src:testing:test-fixtures:compileKotlin FAILED
e: file:///private/tmp/vk-lint-check/src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt:21:60: Argument type mismatch: actual type is 'Int', but 'Long' was expected.
e: file:///private/tmp/vk-lint-check/src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt:22:61: Argument type mismatch: actual type is 'Int', but 'Long' was expected.
e: file:///private/tmp/vk-lint-check/src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt:23:61: Argument type mismatch: actual type is 'Int', but 'Long' was expected.
BUILD FAILED in 10s
EXIT:1
```

Исправлено объявленным типом `private const val … : Long` (значения не менялись). Финальный прогон:

```console
$ cd /tmp/vk-lint-check && ./gradlew build --console=plain
> Task :capabilitiesCheck
> Task :src:app:test
> Task :src:infrastructure:test
> Task :src:persistence:test
> Task :src:vk-facade:test
> Task :src:processor:test
> Task :src:receiver:test
> Task :coverageVerification
> Task :src:testing:arch-tests:test
…
BUILD SUCCESSFUL in 1m 58s
154 actionable tasks: 37 executed, 117 up-to-date
EXIT:0
```

Тесты в копии (сумма по `src/*/build/test-results/test/*.xml`):
`tests=59 failures=0 errors=0 skipped=0`; строк `FAILED`/`FAILURE` в логе — `0`.

### 2.6 AC-5 и AC-6

```console
$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
EXIT:0
```

```console
$ grep -n "fun search_" src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapter.kt
16:    fun search_web(query: String): String {
22:    fun search_academic(query: String): String {
28:    fun search_discussions(query: String): String {
GREP_EXIT:0
```

### 2.7 Локальный прогон (для протокола: зелёный, но слепой)

```console
$ ./gradlew detekt ktlintCheck --console=plain
BUILD SUCCESSFUL in 4s
83 actionable tasks: 35 executed, 48 up-to-date
EXIT:0
```

`src/ai/build/reports/detekt/detekt.md` и `src/testing/test-fixtures/build/reports/detekt/detekt.md`
содержат `## Findings (0)`: локальный detekt по-прежнему не применяет правила с
`excludes: ['**/test/**', …]` к main-исходникам, потому что путь `…/IdeaProjects/test/vk` матчится
глобом. Локальный зелёный прогон до следующей задачи (`make-lint-path-independent`) ничего не
доказывает; оракул — копия `/tmp/vk-lint-check`.

## 3. Отклонения от спеки и принятые решения

1. **`src/ai/detekt-baseline.xml` — одна запись `LongMethod` синхронизирована (файл вне перечня
   пунктов 1–13).** Без этого AC-1 недостижим при точном исполнении пункта 2: detekt включает
   аннотации в подпись `LongMethod`, поэтому `@Suppress` на `ProcessSummaryCommandImpl.execute`
   перестаёт совпадать с закоммиченной подписью, и detekt репортит `LongMethod` в любом пути.
   Это не пересборка baseline: файл в нейтральной копии сгенерирован detekt'ом и отличается от
   закоммиченного ровно одной строкой (диф в §2.3), остальные записи не тронуты. Альтернативу
   «дописать `LongMethod` в `@Suppress`» отверг, чтобы исходник остался ровно как в спеке и чтобы
   не расширять подавление за пределы запрошенного правила.
2. **`FakeVoProvider`: константам явно указан тип `Long`.** Спека требует «значения не меняются»;
   тип `Long` — следствие сигнатуры `Random.nextLong(Long, Long)`, значения те же. Без типа компиляция
   падала (см. §2.5).
3. **`docs/capabilities/index.md:31` не менялся**, хотя содержит ту же устаревшую пару
   `ProcessSummaryCommandImpl.kt:36-125`. Спека перечисляет ровно три capability-файла (пункт 13) и
   запрещает добавлять/менять capability-строки; `check-capabilities.sh` отбрасывает диапазоны строк,
   поэтому AC-5 не затронут. Если нужно синхронизировать и индекс — это отдельная правка вне спеки.
4. Других отклонений нет: логика, сигнатуры, конфигурация, пороги detekt и чужие baseline не менялись.

## 4. Что не сделано и что блокирует

- **AC-2, AC-3** — не эта задача: правка `detekt-config.yml` (анкоринг `**/src/test/**`) и тест-сторож
  `DetektExcludesPathTest` входят в `make-lint-path-independent` (зависит от этой задачи).
- Локальный detekt остаётся зелёным, но слепым (§2.7) — это ожидаемо и снимается следующей задачей.
- Ничего не заблокировано.

## 5. Журнал решений (в порядке выполнения)

1. Прочитал `spec.md` (разделы 2, 4, 5, 7) и все 10 затрагиваемых исходников; зафиксировал AC-1/4/5/6
   в impl.md до правок. `git status` чист, чужие зоны (`detekt-config.yml`, `.github`, `arch-tests`)
   не затронуты.
2. Внёс ровно пункты 1–10 спеки: 7 `@Suppress("TooGenericExceptionCaught")`, `@Suppress("SpreadOperator")`,
   расширенный `@file:Suppress` с `FunctionNaming`, 4 именованные константы. Комментарии не добавлял.
3. Обновил ADR 0005 (8→9 baseline-файлов, добавлен `src/testing/test-fixtures`) и диапазоны строк в трёх
   capability-файлах; формат не менял.
4. `rsync` в `/tmp/vk-lint-check`; проверил, что в пути нет сегмента `test` (иначе oracle невалиден).
5. Сгенерировал baseline `test-fixtures` **только в копии** через `detektBaseline`; получилась ровно одна
   запись `TooManyFunctions`, перенёс файл в репозиторий.
6. Первый AC-1 в копии упал на `LongMethod` (62 > 60) для `ProcessSummaryCommandImpl.execute`: baseline-
   подпись включает `@Suppress`. Сравнил сгенерированный baseline с закоммиченным — отличие ровно в одной
   строке; синхронизировал её (см. §3.1), а не расширял подавление и не трогал конфиг.
7. AC-1 в копии: `BUILD SUCCESSFUL`, 83 задачи выполнены, exit 0, находок нет.
8. Первый `build` в копии упал на компиляции: константы без типа стали `Int`. Добавил явный `Long`
   (значения не менялись) — второй `build` зелёный: 59 тестов, 0 падений, `capabilitiesCheck` и
   `coverageVerification` прошли, exit 0.
9. `check-capabilities.sh` — OK, exit 0; `grep "fun search_"` — три строки с прежними именами, exit 0.
10. Локальный `./gradlew detekt ktlintCheck` — зелёный (exit 0), но отчёты `## Findings (0)` показывают,
    что локально правила по-прежнему слепы; oracle — только нейтральная копия.
