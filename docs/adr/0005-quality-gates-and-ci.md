# ADR 0005: Гейты качества и CI

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
| Линт | `./gradlew detekt` | новое нарушение сверх baseline, `maxIssues: 0` (`detekt-config.yml:2`) | `check` → `build`; CI job `lint` |
| Формат | `./gradlew ktlintCheck` | расхождение с `.editorconfig` | `check` → `build`; CI job `lint` |
| Покрытие-отчёт | `./gradlew coverageReport` | — (отчёт), агрегирует 8 модулей | `check` (через порог) |
| Покрытие-порог | `./gradlew coverageVerification` | LINE ratio < `coverageMin` | `check` → `build`; CI job `coverage` |
| Архитектура | `./gradlew :src:testing:arch-tests:test` | L1–L6, allowlist build-графа | `check` → `build`; CI job `test` |

Конвенции — в корневом `build.gradle.kts:33-73`: `subprojects {}` применяет Kotlin/JVM (toolchain 21),
detekt, ktlint, jacoco и единственный `enforcedPlatform(quarkus-bom)` (`:44-51`). Detekt: `buildUponDefaultConfig`,
`autoCorrect = false` (линтер не правит код), конфиг — `detekt-config.yml` (`:53-67`), baseline подключается,
только если `${projectDir}/detekt-baseline.xml` существует. Baseline один раз сгенерирован и закоммичен:
8 файлов `detekt-baseline.xml` — по одному в `src/ai`, `src/app`, `src/infrastructure`, `src/persistence`,
`src/processor`, `src/share`, `src/vk-facade` и `src/testing/arch-tests`.

### Покрытие

Агрегат собирается из `src/*/build/jacoco/test.exec` восьми модулей (`build.gradle.kts:22-31`), `src/testing/*`
исключены. Измерено в этом проходе: **`coverageMin = 0.25`, `measuredLineCoverage = 0.2796`**
(586 покрытых строк из 2096; `build.gradle.kts:19-20`). Формула — `max(0.10, floor(L * 20) / 20)`, то есть
порог ниже измеренного на ≤ 5 п.п.; значение записано константой, а не выведено в рантайме.
`check` зависит от `coverageVerification` (`build.gradle.kts:130-133`), поэтому `./gradlew build` включает порог.

### Негативные прогоны (каждый гейт падает)

| Гейт | Нарушение | Вывод |
|---|---|---|
| detekt | строка > 120 символов в `:src:share` | `> Task :src:share:detekt FAILED` … `[MaxLineLength]`, `BUILD FAILED`, exit 1 |
| ktlintCheck | `val value=1` в `:src:processor` | `> Task :src:processor:ktlintMainSourceSetCheck FAILED` … `Missing spacing around "="`, `BUILD FAILED`, exit 1 |
| ArchUnit L3 | use case `ai` ссылается на адаптер `UserAnswerAiService` | `LayerDependenciesTest > L3 … FAILED`, `BUILD FAILED`, exit 1 |
| ArchUnit модули | класс пакета `receiver` в модуле `persistence` зависит от `persistence` | `ModuleDependenciesTest > receiver module should only depend on share() FAILED`, exit 1 |
| Покрытие | `coverageMin = 0.99` | `Rule violated for bundle vkbot: lines covered ratio is 0.27, but expected minimum is 0.99`, exit 1 |
| Граф сборки | вернуть `implementation(project(":src:persistence"))` в `:src:ai` | `GradleDependenciesTest > production project dependencies follow the allowlist() FAILED` (проверено стадией implement) |
| Карта возможностей | путь кода или `ADR NNNN`, которого нет | `FAIL: … which does not exist`, exit 1 |

После откатов все гейты снова зелёные; временные файлы удалены.

### CI

`.github/workflows/build-and-test.yml` — триггеры `push`/`pull_request` в `main`, JDK 21 temurin,
`gradle/actions/setup-gradle@v4`; пять джобов: `build` (артефакт деплоя + отсутствие второго раннера),
`test`, `lint` (`detekt ktlintCheck`), `coverage` (`test coverageReport coverageVerification` + upload отчёта),
`docs` (`./scripts/check-capabilities.sh`). `--scan` убран.

### Ратчет

Целевые пороги 0.40 и 0.60 — отдельные задачи с новыми тестами, не порог этого прохода.
`coverageMin` поднимается только вместе с измеренным `L`.

## Последствия

- `./gradlew build` — единственная локальная команда «всё разом»: тесты, линт, формат и порог покрытия
  входят через `check`; она зелёная (168 задач, `BUILD SUCCESSFUL in 2m 4s`).
- Модульный `./gradlew jacocoTestReport` не является гейтом и в `:src:app` падает на Gradle validation
  (`:src:app:jandex` output не объявлен входом задачи); поддерживаемые команды покрытия —
  `coverageReport` и `coverageVerification`.
- `ktlintCheck` после удаления исходника может один раз отдать кэшированный отчёт; лечится
  `./gradlew ktlintCheck --rerun-tasks` (наблюдалось в этом проходе: `BUILD FAILED` сразу после удаления
  временного файла, затем `BUILD SUCCESSFUL`).
- Джоб `docs` проверяет `docs/capabilities/`; карта возможностей существует, чек зелёный
  (`./scripts/check-capabilities.sh` → `OK: capability map paths, test paths, statuses and ADR ids resolve`,
  exit 0).

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Spotless/Checkstyle вместо detekt/ktlint | В проекте уже объявлены detekt и ktlint; смена — churn |
| Линт без baseline | 8 baseline-файлов фиксируют существующий долг; без них гейт был бы выключен навсегда |
| Линт с `autoCorrect = true` | Линтер, который правит код в гейте, перестаёт быть проверкой |
| Покрытие без порога | Отчёт без порога не валит сборку — не гейт |
| Порог «на глаз» | Порог выведен из измеренного `L` формулой и записан константой |
| `--scan` в CI | Внешний сервер сканов не нужен |
