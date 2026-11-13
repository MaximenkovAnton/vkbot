# 2026-10-28: Гейты качества и CI

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

До прохода гейты существовали частично и часть была вакуумной: `./gradlew detekt` отдавал `NO-SOURCE`
(плагин применялся только к корню), `ktlintCheck` проверял пустые sourceSets корня, `jacocoTestReport`
отсутствовал как агрегат, `GradleDependenciesTest` не мог упасть, CI гонял только `build` и `test`.
Гейт, который не может упасть, — не гейт, поэтому каждый включённый механизм проверен негативным прогоном.

## Решение

### Включённые гейты

| Гейт | Команда | Как падает | Где включён |
|---|---|---|---|
| Сборка | `./gradlew build` | ошибка компиляции, падение теста, любой гейт ниже | локально; CI job `build` |
| Тесты | `./gradlew test` | падение/ошибка любого теста | `check` → `build`; CI job `test` |
| Линт | `./gradlew detekt` | новое нарушение сверх baseline, `maxIssues: 0` ([`detekt-config.yml:2`](../../detekt-config.yml)) | `check` → `build`; CI job `lint` |
| Формат | `./gradlew ktlintCheck` | расхождение с `.editorconfig` | `check` → `build`; CI job `lint` |
| Покрытие-отчёт | `./gradlew coverageReport` | — (отчёт), агрегирует 9 модулей | `check` (через порог) |
| Покрытие-порог | `./gradlew coverageVerification` | LINE ratio < `coverageMin` | `check` → `build`; CI job `coverage` |
| Архитектура | `./gradlew :src:testing:arch-tests:test` | L1–L6, allowlist build-графа | `check` → `build`; CI job `test` |

Конвенции — в корневом [`build.gradle.kts:34-74`](../../build.gradle.kts): `subprojects {}` применяет Kotlin/JVM (toolchain 21),
detekt, ktlint, jacoco и единственный `enforcedPlatform(quarkus-bom)` (`:45-52`). Detekt: `buildUponDefaultConfig`,
`autoCorrect = false` (линтер не правит код), конфиг — [`detekt-config.yml`](../../detekt-config.yml) (`:54-68`), baseline подключается,
только если `${projectDir}/detekt-baseline.xml` существует. Baseline один раз сгенерирован и закоммичен:
9 файлов `detekt-baseline.xml` — по одному в [`src/ai`](../../src/ai), [`src/app`](../../src/app), [`src/infrastructure`](../../src/infrastructure), [`src/persistence`](../../src/persistence),
[`src/processor`](../../src/processor), [`src/share`](../../src/share), [`src/vk-facade`](../../src/vk-facade), [`src/testing/arch-tests`](../../src/testing/arch-tests) и [`src/testing/test-fixtures`](../../src/testing/test-fixtures).

### Независимость гейта линта от пути чек-аута

Detekt сопоставляет глобы `excludes` с абсолютным путём файла, а не с путём относительно корня проекта.
Широкий глоб `**/test/**` матчил любой файл чек-аута, лежащего в каталоге с именем `test`
(`/Users/asmaksime1/IdeaProjects/test/vk`), поэтому локально 15 правил не применялись к `src/main`,
и `./gradlew detekt` был зелёным при красном CI. Анкоринг глобов на source sets (`**/src/test/**`,
`**/src/androidTest/**` и т.д.) выравнивает локальный прогон с CI: исключаются только тестовые
исходники, где бы ни лежал чек-аут.

Сторож — `DetektExcludesPathTest` ([`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/DetektExcludesPathTest.kt`](../../src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/DetektExcludesPathTest.kt)):
он извлекает глобы из [`detekt-config.yml`](../../detekt-config.yml) и проверяет через `java.nio.file` glob-PathMatcher, что ни
один из них не матчит main-исходник синтетического чек-аута в каталоге `test`
(`/tmp/test/vk/<module>/src/main/kotlin/...`), и называет виновный глоб. Негативный прогон: если
вернуть `'**/test/**'` хотя бы в один список `excludes`, `./gradlew :src:testing:arch-tests:test --tests
"*DetektExcludesPathTest*"` падает с `detekt-config.yml excludes match main sources: [**/test/**]`; после
отката — зелёный. [`detekt-config.yml`](../../detekt-config.yml) объявлен входом `Test`-задач `:src:testing:arch-tests`, поэтому
правка конфига перезапускает сторожа, а не оставляет задачу `UP-TO-DATE`.

### Покрытие

Агрегат собирается из `src/*/build/jacoco/test.exec` девяти модулей ([`build.gradle.kts:22-32`](../../build.gradle.kts)), `src/testing/*`
исключены. Измерено: **`coverageMin = 0.40`, `measuredLineCoverage = 0.5687`**
(1478 покрытых строк из 2599; [`build.gradle.kts:19-20`](../../build.gradle.kts)). Формула первого порога —
`max(0.10, floor(L * 20) / 20)`; при L = 0.5687 она дала бы 0.55, а 0.40 — цель ратчета (см. «Ратчет»).
Значение записано константой, а не выведено в рантайме.
`check` зависит от `coverageVerification` ([`build.gradle.kts:131-134`](../../build.gradle.kts)), поэтому `./gradlew build` включает порог.

Ратчет 0.40 выполнен в задаче `tests: ai module`; после доставки `daily-greetings` измерено `L = 0.5687`, порог не понижен.

### Негативные прогоны (каждый гейт падает)

| Гейт | Нарушение | Вывод |
|---|---|---|
| detekt | строка > 120 символов в `:src:share` | `> Task :src:share:detekt FAILED` … `[MaxLineLength]`, `BUILD FAILED`, exit 1 |
| ktlintCheck | `val value=1` в `:src:processor` | `> Task :src:processor:ktlintMainSourceSetCheck FAILED` … `Missing spacing around "="`, `BUILD FAILED`, exit 1 |
| ArchUnit L3 | use case `ai` ссылается на адаптер `UserAnswerGenerator` | `LayerDependenciesTest > L3 … FAILED`, `BUILD FAILED`, exit 1 |
| ArchUnit модули | класс пакета `receiver` в модуле `persistence` зависит от `persistence` | `ModuleDependenciesTest > receiver module should only depend on share() FAILED`, exit 1 |
| Покрытие | `coverageMin = 0.99` | `Rule violated for bundle vkbot: lines covered ratio is 0.56, but expected minimum is 0.99`, exit 1 |
| Граф сборки | вернуть `implementation(project(":src:persistence"))` в `:src:ai` | `GradleDependenciesTest > production project dependencies follow the allowlist() FAILED` (проверено стадией implement) |
| Карта возможностей | путь кода или `ADR NNNN`, которого нет | `FAIL: … which does not exist`, exit 1 |

После откатов все гейты снова зелёные; временные файлы удалены.

### CI

[`.github/workflows/build-and-test.yml`](../../.github/workflows/build-and-test.yml) — триггеры `push`/`pull_request` в `main`, JDK 21 temurin,
`gradle/actions/setup-gradle@v4`; пять джобов: `build` (артефакт деплоя + отсутствие второго раннера),
`test`, `lint` (`detekt ktlintCheck`), `coverage` (`test coverageReport coverageVerification` + upload отчёта),
`docs` ([`./scripts/check-capabilities.sh`](../../scripts/check-capabilities.sh)). `--scan` убран.

### Ратчет

Порог 0.40 — цель ратчета, выполненная в задаче `tests: ai module` (текущее измерение `L = 0.5687`). Следующий
целевой порог 0.60 — отдельная задача с новыми тестами, не порог этого прохода.
`coverageMin` поднимается только вместе с измеренным `L`.

**Обновление 2026-11-13:** на дереве `2336ae1` `./gradlew coverageReport` даёт `L = 0.6951` (4333 покрытых
строки из 6234); целевой порог 0.60 достигнут, `coverageMin` остаётся 0.40 — повышение порога отдельное
решение. Живые числа — [`docs/development/testing.md`](../development/testing.md) §«Покрытие».

## Последствия

- `./gradlew build` — единственная локальная команда «всё разом»: тесты, линт, формат и порог покрытия
  входят через `check`; она зелёная (`./gradlew build` → `BUILD SUCCESSFUL in 1m 43s`, 180 задач,
  docs-гейт `capabilitiesCheck` внутри, 243 теста в 53 классах — прогон на момент решения; на 2026-11-13
  прогон даёт 807 тестов в 121 классе).
- Модульный `./gradlew jacocoTestReport` не является гейтом и в `:src:app` падает на Gradle validation
  (`:src:app:jandex` output не объявлен входом задачи); поддерживаемые команды покрытия —
  `coverageReport` и `coverageVerification`.
- `ktlintCheck` после удаления исходника может один раз отдать кэшированный отчёт; лечится
  `./gradlew ktlintCheck --rerun-tasks` (наблюдалось в этом проходе: `BUILD FAILED` сразу после удаления
  временного файла, затем `BUILD SUCCESSFUL`).
- Джоб `docs` проверяет [`docs/capabilities/`](../capabilities); карта возможностей существует, чек зелёный
  ([`./scripts/check-capabilities.sh`](../../scripts/check-capabilities.sh) → `OK: capability map paths, test paths, statuses and ADR ids resolve`,
  exit 0).

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Spotless/Checkstyle вместо detekt/ktlint | В проекте уже объявлены detekt и ktlint; смена — churn |
| Линт без baseline | 9 baseline-файлов фиксируют существующий долг; без них гейт был бы выключен навсегда |
| Линт с `autoCorrect = true` | Линтер, который правит код в гейте, перестаёт быть проверкой |
| Покрытие без порога | Отчёт без порога не валит сборку — не гейт |
| Порог «на глаз» | Порог выведен из измеренного `L` формулой и записан константой |
| `--scan` в CI | Внешний сервер сканов не нужен |
