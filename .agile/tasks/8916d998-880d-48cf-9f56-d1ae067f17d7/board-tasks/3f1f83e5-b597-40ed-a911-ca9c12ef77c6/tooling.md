# Tooling проекта VK Bot: версии, команды, гейты

**Стадия:** implement (implementation), board task `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`.
**Правило:** версии не скопированы из профиля — они прочитаны из сборки (`settings.gradle.kts`, `gradle.properties`, `build.gradle.kts`, wrapper) и подтверждены прогоном. Команды названы так, как их объявляет сам сборщик (`./gradlew tasks`).

## 1. Разрешённые версии (источник истины)

| Слой | Версия | Где зафиксирована |
|---|---|---|
| JDK | 21.0.5 Temurin (toolchain 21) | `kotlin { jvmToolchain(21) }` каждого модуля; `java -version` = 21.0.5; CI `java-version: '21'` |
| Gradle | 9.3.0 (wrapper, Kotlin DSL) | `gradle/wrapper/gradle-wrapper.properties`; `./gradlew --version` |
| Kotlin | 2.3.0 | `settings.gradle.kts:14` (pluginManagement) |
| Quarkus | 3.34.2 | `gradle.properties:3-7`, одно объявление BOM в `subprojects {}` корня |
| LangChain4j | 1.8.4 | `gradle.properties:8` |
| detekt | 1.23.8 | `gradle.properties:9-10` |
| ktlint (Gradle plugin) | 13.1.0 | корневой `build.gradle.kts` plugins |
| JaCoCo | 0.8.14 (по умолчанию плагина Gradle 9.3.0) | разрешается как `org.jacoco:org.jacoco.ant:0.8.14` |
| ArchUnit | 1.3.0 | `src/testing/arch-tests/build.gradle.kts` |
| jOOQ | 3.19.10 (кодогенерация выключена) | `src/persistence/build.gradle.kts` |
| WireMock / Awaitility | 3.9.1 / 4.2.0 | `src/app/build.gradle.kts` |
| jandex (kordamp) | 2.3.0 | модульные `build.gradle.kts` + объявление `apply false` в корне |
| foojay-resolver | 0.8.0 | `settings.gradle.kts:18` |

Второй точки изменения версии Quarkus нет: `grep -rn enforcedPlatform` находит единственную строку в корневом `subprojects {}`. Плавающих версий нет: `grep -rn ":latest" build.gradle.kts src/*/build.gradle.kts src/testing/*/build.gradle.kts` пуст.

## 2. Команды, которые работают

| Команда | Что делает | exit | Подтверждение |
|---|---|---|---|
| `./gradlew clean build` | полная сборка: компиляция, тесты, detekt, ktlintCheck, coverageVerification, `:src:app:quarkusBuild` | 0 | `BUILD SUCCESSFUL in 2m 1s`, `167 actionable tasks: 167 executed` |
| `./gradlew build` | то же без clean | 0 | `BUILD SUCCESSFUL in 6s`, `154 actionable tasks` (rework-прогон; clean build — §3.1) |
| `./gradlew test` | только тесты (нужен Docker для DevServices) | 0 | 59 тестов, 0 падений; `BUILD SUCCESSFUL in 1m 14s` (rework) |
| `./gradlew :src:app:test` | интеграционные тесты единственного деплоя (Docker DevServices) | 0 | `BUILD SUCCESSFUL in 1m 7s` (rework) |
| `./gradlew detekt` | линтер detekt по всем 11 модулям | 0 | `BUILD SUCCESSFUL` |
| `./gradlew ktlintCheck` | проверка формата ktlint по всем модулям и `*.kts` | 0 | `BUILD SUCCESSFUL` |
| `./gradlew ktlintFormat` | разовое приведение к стилю (автоформат) | 0 | выполнен один раз по существующему коду |
| `./gradlew coverageReport` | агрегированный JaCoCo-отчёт (HTML+XML) по 8 модулям | 0 | `build/reports/jacoco/coverageReport/{html,coverageReport.xml}` |
| `./gradlew coverageVerification` | порог покрытия, включён в `check` | 0 | `BUILD SUCCESSFUL` |
| `./gradlew :src:app:quarkusBuild` | сборка единственного деплоя | 0 | `src/app/build/quarkus-app/quarkus-run.jar` |
| `./gradlew :src:app:quarkusDev` | dev-режим единственного деплоя | — | команда существует (`:quarkusDev` в корне больше не создаётся) |
| `./gradlew :src:testing:arch-tests:test` | архитектурные тесты | 0 | 15 тестов |
| `./scripts/check-capabilities.sh` | чек карты возможностей (пути, тест-пути, ADR id) | 0 | `OK: capability map paths, test paths, statuses and ADR ids resolve` (rework) |
| `ruby -e "require 'yaml'; YAML.load_file('.github/workflows/build-and-test.yml'); puts 'yaml ok'"` | валидация CI-workflow | 0 | `yaml ok` |

Smoke-проверка §13 дизайна (после `clean build`):

```
test -f src/app/build/quarkus-app/quarkus-run.jar   -> artifact: src/app/build/quarkus-app/quarkus-run.jar OK
test ! -e build/quarkus-app                          -> root build/quarkus-app: absent OK
```

Что команды **не** делают (намеренно): `./gradlew jacocoTestReport` в корне отсутствует — отчёт агрегируется задачами `coverageReport`/`coverageVerification`; `quarkusDev`, `quarkusBuild` и сам деплой живут только в `:src:app`, корень — агрегатор.

## 3. Гейты: что валит, порог, где включён, доказательство

Каждый гейт проверен по схеме «проходит → временное нарушение → падает → откат → проходит снова». Ниже цитаты и exit-коды.

| # | Гейт | Команда | На чём падает | Порог / условие | Где включён |
|---|---|---|---|---|---|
| 3.1 | Сборка деплоя | `./gradlew build` | ошибка компиляции, найденный тест, любой гейт ниже | exit 0 + `src/app/build/quarkus-app/quarkus-run.jar` | локально и job `build` |
| 3.2 | Тесты | `./gradlew test` | падение/ошибка любого теста | 59 тестов, 0 падений | `check` → `build`; job `test` |
| 3.3 | Линт | `./gradlew detekt` | новое нарушение сверх baseline | `maxIssues: 0` (`detekt-config.yml:2`), baseline в каждом модуле с находками | `check` → `build`; job `lint` |
| 3.4 | Формат | `./gradlew ktlintCheck` | любое расхождение со стилем `.editorconfig` | 0 нарушений | `check` → `build`; job `lint` |
| 3.5 | Покрытие | `./gradlew coverageVerification` | агрегатный LINE ratio ниже порога | `coverageMin = 0.25` (`measuredLineCoverage = 0.2796`, формула `max(0.10, floor(L*20)/20)`) | `check` → `build`; job `coverage` |
| 3.6 | Архитектура слоёв | `./gradlew :src:testing:arch-tests:test` | L1–L5 нарушены | 0 нарушений ArchUnit | `check` → `build`; job `test` |
| 3.7 | Граф сборки | то же | `GradleDependenciesTest`: allowlist production-рёбер, testing-модули только в тестах, найдено 12 build-файлов | ровно 12 модулей в allowlist | `check` → `build`; job `test` |
| 3.8 | Карта возможностей | `./scripts/check-capabilities.sh` | путь кода или `ADR NNNN` в `docs/capabilities/**` не существует; нет `docs/capabilities/index.md` | 0 мёртвых путей и id | job `docs` |
| 3.9 | CI | `ruby -e ... YAML.load_file` | невалидный YAML | парсится | job'ы гоняют 3.1, 3.2, 3.3, 3.4, 3.5 |

### 3.1 Сборка

```
$ ./gradlew clean build
BUILD SUCCESSFUL in 2m 1s
167 actionable tasks: 167 executed
exit=0
```

### 3.2 Тесты

```
tests=59 failures=0 errors=0 skipped=0        (src/**/build/test-results/test/*.xml)
```

Из них 41 существовавший (включая `VkWebhookIntegrationTest`: вебхук → RabbitMQ → persistence (in-process) → processor → ai → vk-facade → WireMock VK API) и 18 новых: 9 `PersistenceDataInProcessAdapterTest`, 2 `VkProfileInProcessAdapterTest`, 5 `LayerDependenciesTest`, 3 `GradleDependenciesTest` (было 1 вакуумный), `ModuleDependenciesTest` без изменений — 7.

### 3.3 detekt

```
$ ./gradlew detekt
BUILD SUCCESSFUL in 566ms      exit=0
--- временное нарушение: src/share/.../GateProofDetekt.kt, строка длиннее 120 ---
$ ./gradlew detekt
> Task :src:share:detekt FAILED
.../GateProofDetekt.kt:3:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
BUILD FAILED in 572ms          exit=1
--- файл удалён ---
$ ./gradlew detekt
BUILD SUCCESSFUL in 568ms      exit=0
```

Baseline: `./gradlew detektBaseline` выполнен один раз, 8 файлов `detekt-baseline.xml` созданы и входят в набор изменений. `autoCorrect = false` — линтер не правит код. В baseline не попали ни один из новых файлов этой стадии (проверено `grep ForwardedMessagesSerializationPort src/persistence/detekt-baseline.xml` → 0): единственное замечание в новом коде (длинная строка объявления `ForwardedMessagesSerializationPort`) исправлено переносом.

### 3.4 ktlintCheck

```
$ ./gradlew ktlintCheck
BUILD SUCCESSFUL in 572ms      exit=0
--- временное нарушение: src/processor/.../GateProofKtlint.kt, "val value=1" ---
$ ./gradlew ktlintCheck
> Task :src:processor:ktlintMainSourceSetCheck FAILED
.../GateProofKtlint.kt:4:14 Missing spacing around "="
BUILD FAILED in 1s             exit=1
--- файл удалён ---
$ ./gradlew ktlintCheck
BUILD SUCCESSFUL in 592ms      exit=0
```

Оговорка (см. §5): сразу после удаления файла плагин ktlint один раз отдал вчерашний отчёт (`BUILD FAILED`, `exit=1`) из-за кэша задачи на удалённом файле; `./gradlew ktlintCheck --rerun-tasks` → `BUILD SUCCESSFUL in 4s`, `exit=0`, и все последующие обычные прогоны зелёные. На это же указывает `./gradlew build` с нуля.

### 3.5 Покрытие

```
$ ./gradlew coverageVerification            # coverageMin = 0.25
BUILD SUCCESSFUL                            exit=0
--- временно coverageMin = 0.99 ---
$ ./gradlew coverageVerification --rerun-tasks
> Task :coverageVerification FAILED
[ant:jacocoReport] Rule violated for bundle vkbot: lines covered ratio is 0.27, but expected minimum is 0.99
BUILD FAILED in 1m 47s                      exit=1
--- порог возвращён на 0.25 ---
$ ./gradlew coverageVerification
BUILD SUCCESSFUL in 1m 8s                   exit=0
```

Измерение и формула (записаны константами в корневом `build.gradle.kts`): `L = 0.2796` (586 покрытых строк из 2096), `coverageMin = max(0.10, floor(0.2796*20)/20) = 0.25`. Отчёт строится из прогона тестов (`:src:*:test` → `build/jacoco/test.exec`), а не отдельным запуском. В покрытие входят 8 модулей (`ai, app, infrastructure, persistence, processor, receiver, share, vk-facade`), `src/testing/*` исключены.

### 3.6 Архитектура слоёв (L1–L5)

```
$ ./gradlew :src:testing:arch-tests:test
BUILD SUCCESSFUL in 9s         exit=0
--- временно: в ai/usecase добавлена ссылка на ConversationContextRestAdapter ---
$ ./gradlew :src:testing:arch-tests:test --tests '*LayerDependenciesTest*'
LayerDependenciesTest > L3 use cases and commands depend on no adapter, database or transport type() FAILED
BUILD FAILED in 8s             exit=1
--- откат ---
$ ./gradlew :src:testing:arch-tests:test
BUILD SUCCESSFUL in 9s         exit=0
```

Правила: L1 `domain` без фреймворков и чужих слоёв; L2 `port` без реализации и фреймворков; L3 `usecase`/`command` без адаптеров, БД-клиента и транспорта (DI-аннотации `jakarta.inject`/`jakarta.enterprise`/`jakarta.transaction`/`org.eclipse.microprofile.config.inject` не в запрете — ADR 0003); L4 входной адаптер зовёт только `port`; L5 выходной адаптер не ходит во входной и в usecase (в `command` можно — `VaneSearchToolAdapter`).

Доказательство модульной границы (без правки build-файлов: класс с пакетом `com.simarel.vkbot.receiver` временно положен в модуль `persistence`):

```
ModuleDependenciesTest > receiver module should only depend on share() FAILED
BUILD FAILED in 9s             exit=1
--- класс удалён ---
BUILD SUCCESSFUL in 9s         exit=0
```

### 3.7 Граф сборки

```
--- временно возвращено ребро implementation(project(":src:persistence")) в :src:ai ---
$ ./gradlew :src:testing:arch-tests:test --tests '*GradleDependenciesTest*'
GradleDependenciesTest > production project dependencies follow the allowlist() FAILED
BUILD FAILED in 7s             exit=1
--- ребро удалено ---
BUILD SUCCESSFUL in 6s         exit=0
```

Первое ребро дизайна (`share -> :src:ai`) как негативный тест не годится: оно циклическое (ai уже зависит от share), и Gradle падает на конфигурации, не доходя до теста. Поэтому взято ровно то ребро, которое дизайн удаляет (`ai -> persistence`), — граф ломается на нём так же.

Тест `build files are discovered` валит сборку, если найдено не 12 build-файлов; обход теперь не отбрасывает всё по подстроке «build» (старый фильтр `!it.path.contains("build")` и `File(".")` в рабочей директории модуля делали тест неспособным упасть).

### 3.8 Карта возможностей

```
$ ./scripts/check-capabilities.sh         # временный валидный fixture docs/capabilities + docs/adr
OK: capability map paths and ADR ids resolve                 exit=0
--- в файле возможности путь заменён на несуществующий ---
FAIL: docs/capabilities/gate-proof.md references src/share/src/main/kotlin/com/simarel/vkbot/share/domain/Gone.kt, which does not exist   exit=1
--- путь восстановлен, ADR id заменён на ADR 9999 ---
FAIL: docs/capabilities/gate-proof.md references ADR 9999, but docs/adr/9999-*.md does not exist                                          exit=1
--- fixture удалён (состояние до стадии capabilities) ---
FAIL: docs/capabilities/index.md does not exist                                                                    exit=1
```

Скрипт: требует `docs/capabilities/index.md`; каждая ссылка из индекса должна существовать; внутри файла возможности каждый путь в обратных кавычках, начинающийся с `src/` или `docs/`, должен существовать; каждый токен `ADR NNNN` должен иметь `docs/adr/NNNN-*.md`. Job `docs` в CI гоняет этот скрипт; после стадии `capabilities` он зелёный без правки скрипта (rework-прогон §6.6: `OK`, exit 0).

### 3.9 CI

```
$ ruby -e "require 'yaml'; YAML.load_file('.github/workflows/build-and-test.yml')"
yaml ok                        exit=0
```

(PyYAML в окружении отсутствует, поэтому валидация выполнена Ruby-парсером YAML — тот же смысл проверки.)

## 4. CI: job'ы и версии

Файл `.github/workflows/build-and-test.yml`, триггеры `push`/`pull_request` в `main`, JDK 21 temurin, `gradle/actions/setup-gradle@v4`; версии — только те, что закреплены сборкой (JDK 21, Gradle wrapper 9.3.0).

| job | шаг | что валит job |
|---|---|---|
| `build` | `./gradlew :src:app:quarkusBuild` + `test -f src/app/build/quarkus-app/quarkus-run.jar` + `test ! -e build/quarkus-app` | сборка деплоя, второй раннер |
| `test` | `./gradlew test` | тесты |
| `lint` | `./gradlew detekt ktlintCheck` | линт и формат |
| `coverage` | `./gradlew test coverageReport coverageVerification` + upload `build/reports/jacoco/coverageReport/html` | порог покрытия |
| `docs` | `./scripts/check-capabilities.sh` | мёртвые пути/ADR id в карте возможностей |

`--scan` из старого workflow убран: внешний сервер сканов не нужен. `./gradlew build` остаётся локальной командой «всё разом» и включает те же гейты через `check`.

## 5. Известные ограничения и условия

- `shortcut: ktlintCheck после удаления исходника один раз отдаёт кэшированный отчёт, запускать с --rerun-tasks; апгрейд — при переходе на другой ktlint-плагин.`
- `shortcut: jandex.idx генерируется в build/resources; при ручной правке состава классов без clean возможен устаревший индекс (ошибка Unsatisfied dependency на этапе ArC); лечится ./gradlew clean build.`
- `shortcut: покрытие меряется стандартным JaCoCo-плагином; если @QuarkusTest перестанет попадать в инструментацию, порог упадёт — тогда вернуть quarkus-jacoco и строить отчёт из его данных, формула порога не меняется.`
- `shortcut: AC-ветка scripts/check-capabilities.sh (строка 102) принимает любой путь src/, в том числе production-код; ужесточение до src/**/src/(test|testFixtures)/ — N5, заявленное в docs-стадию.`
- Порог покрытия 0.25 — детерминированная формула от измеренного `L`. Цель-ратчет 0.40, затем 0.60 — отдельные задачи с тестами (§7.2 дизайна), не порог этого прохода.
- `VK_BOT_ID` обязателен в runtime (без дефолта в коде); сборка и тесты его не требуют (тестовые конфиги задают значение).
- Нативный образ и Docker-сборки документируются, но в CI не собираются (§12 дизайна).

## 6. Rework-проход (review раунд 1): гейты перепрогнаны

Review §5 вернул задачу с блокирующей B1 (мёртвые REST-заглушки в `WireMockTestResource` маскировали откат ADR 0004) и nit N1 (уровни логирования). Правки описаны в `scaffold.md` §7. Здесь — заново снятые доказательства: каждый гейт снова падает на временном нарушении и проходит после отката; порог и конфигурация восстановлены (sha256 сверен).

### 6.1 Тесты и сборка

```
$ ./gradlew :src:app:test
BUILD SUCCESSFUL in 1m 7s             exit=0    # 39 actionable tasks: 8 executed
$ ./gradlew test
BUILD SUCCESSFUL in 1m 14s            exit=0    # 64 actionable tasks: 4 executed
$ ./gradlew build
BUILD SUCCESSFUL in 6s                exit=0    # 154 actionable tasks: 13 executed
$ ./gradlew build                     # повторно, после откатов негативов
BUILD SUCCESSFUL in 1s                exit=0
tests=19 files, tests=59, failures=0, errors=0, skipped=0        (src/**/build/test-results/test/*.xml)
```

### 6.2 detekt

```
--- временно src/share/.../GateProofDetekt.kt: строка 150 символов ---
$ ./gradlew detekt
> Task :src:share:detekt FAILED
.../GateProofDetekt.kt:3:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
BUILD FAILED in 554ms                exit=1
--- файл удалён ---
$ ./gradlew detekt
BUILD SUCCESSFUL in 516ms             exit=0
```

### 6.3 ktlintCheck

```
--- временно src/processor/.../GateProofKtlint.kt: "val gateProofValue =1" ---
$ ./gradlew ktlintCheck
> Task :src:processor:ktlintMainSourceSetCheck FAILED
.../GateProofKtlint.kt:3:21 Missing spacing after "="
BUILD FAILED in 1s                    exit=1
--- файл удалён; кэш задачи на удалённом файле — см. §5 ---
$ ./gradlew ktlintCheck
BUILD FAILED in 518ms                 exit=1
$ ./gradlew ktlintCheck --rerun-tasks
BUILD SUCCESSFUL in 3s                exit=0
```

### 6.4 Покрытие (порог валит build; отчёт строится из тестового прогона)

```
--- временно coverageMin = 0.25 -> 0.99 в build.gradle.kts ---
$ ./gradlew coverageVerification --rerun-tasks
> Task :coverageVerification FAILED
[ant:jacocoReport] Rule violated for bundle vkbot: lines covered ratio is 0.27, but expected minimum is 0.99
BUILD FAILED in 1m 46s                exit=1
--- порог возвращён, sha256 файла сошёлся, тесты перезапущены в --rerun-tasks прогоне ---
$ ./gradlew coverageVerification
BUILD SUCCESSFUL in 1m 7s             exit=0
```

### 6.5 Архитектура (граница слоёв)

```
--- временно src/ai/.../usecase/GateProofL3.kt: import ...ai.adapter.output.context.ConversationContextRestAdapter ---
$ ./gradlew :src:testing:arch-tests:test --tests '*LayerDependenciesTest*'
LayerDependenciesTest > L3 use cases and commands depend on no adapter, database or transport type() FAILED
BUILD FAILED in 9s                    exit=1
--- класс удалён ---
$ ./gradlew :src:testing:arch-tests:test
BUILD SUCCESSFUL in 9s                exit=0
```

### 6.6 Карта возможностей (мёртвый ADR id и мёртвый путь)

```
--- ADR 0004 -> ADR 0099 в docs/capabilities/conversation-summary.md ---
FAIL: docs/capabilities/conversation-summary.md references ADR 0099, but docs/adr/0099-*.md does not exist      exit=1
--- восстановлено, sha256 сошёлся ---
OK: capability map paths, test paths, statuses and ADR ids resolve                                               exit=0
--- добавлена строка с несуществующим .../share/Gone.kt в docs/capabilities/vk-webhook.md ---
FAIL: docs/capabilities/vk-webhook.md references src/share/src/main/kotlin/com/simarel/vkbot/share/Gone.kt, which does not exist   exit=1
--- восстановлено ---
OK: capability map paths, test paths, statuses and ADR ids resolve                                               exit=0
```

### 6.7 CI и целостность дерева

```
$ ruby -e "require 'yaml'; YAML.load_file('.github/workflows/build-and-test.yml')"
yaml ok                               exit=0
$ git status --short | wc -l
147                                   # как до rework (изменённые файлы, не новые)
$ find src -name 'GateProof*'         # пусто: временные файлы удалены
```
