# impl.md — factories-and-gate

- Board task: `factories-and-gate` (этап `implementation`), task `0544056b-a6ef-4bd9-ac77-fba232fffd5f`.
- Спецификация: `.agile/tasks/0544056b-a6ef-4bd9-ac77-fba232fffd5f/spec.md` (§1, §4, §5, §6, §7, §10, §12).
- Рабочая директория: `/Users/asmaksime1/IdeaProjects/test/vk`.
- Дата прогона: 2026-10-29.

## Контракт (AC спецификации, до кода)

| AC | Формулировка | Чем закрыт |
|---|---|---|
| AC-1 | сторож не находит прямого создания тестовых данных в `src/*/src/test/**` | `TestDataFactoriesTest > test sources create test data only through test data providers`, полный `./gradlew test` |
| AC-2 | у каждого параметра фабричного `create*` есть дефолт | `TestDataFactoriesTest > every factory method parameter has a default` |
| AC-3 | негативные прогоны падают с `path:line` / именем параметра | прогоны §3.1-§3.4, с цитатами и откатом |
| AC-4 | общие фабрики в `:src:testing:test-fixtures`, модульные в `persistence`/`receiver` `src/testFixtures`, `build.gradle.kts` не меняются | `:src:testing:arch-tests:test` (`GradleDependenciesTest` + 18/18), `git diff --stat -- '**/build.gradle.kts'` (оговорка в §4) |
| AC-6 | `./gradlew build` зелёный | `./gradlew build` exit 0 |
| AC-5 | документы (docs/**, AGENTS.md §8, patterns.md) | зона board task `docs-rule`; в этой задаче docs/** и AGENTS.md не трогались |

## 0. Раунд 2 — доработка по ревью (2026-10-29)

Ревью раунда 1 (`review-summary.md` §2) вернуло задачу в `implementation` с двумя блокирующими находками. В этом раунде изменён только сторож `TestDataFactoriesTest.kt`; фабрики, 6 тестов и переиспользование общего фейка не менялись (консолидированная инструкция `review-summary.md` §7).

- **B1 — аннотированное объявление не попадало в набор запрещённых типов.** `declarationPattern` (строки 22-24) теперь допускает аннотации в начале строки: `^\s*(?:@[\w.]+(?:\s*\(.*?\))?\s+)*((?:\w+\s+)*)(class|interface|object)\s+...`. До правки `@Suppress("unused") data class X(...)` не распознавался, и прямое создание `X(...)` в тесте проходило. Негативный прогон — §3.3.
- **B2 — обязательный параметр после дефолта-функции не виделся.** В `parameters()` (строки 145-167) круглая/квадратная/фигурная глубина считается отдельно от угловой, а `>` оператора `->` угловую глубину не закрывает; поэтому `callback: () -> Unit = {}` больше не склеивается со следующим параметром. Там же `createFunctionPattern` (строка 26) принимает необязательный список type-параметров `fun <T> createX(...)` — та же категория молчаливого пропуска (test-reviewer nit 3). Негативные прогоны — §3.4.

## 1. Файлы и зачем

Новые фабрики `:src:testing:test-fixtures` (`src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/`):

- `domain/FakeVoProvider.kt` (правка) — добавлен `createFromId(value: Long? = null)`.
- `domain/FakeModelProvider.kt` (новый) — `createStoredMessage`, `createSummary`, `createVkUserProfile`, `createVkGroupProfile`, `createResponseMessage`.
- `port/FakeProfilePortProvider.kt` (новый) — `createGetUserProfilesBatchRequest`, `createGetGroupProfilesBatchRequest`.
- `port/FakePersistencePortProvider.kt` (новый) — 9 request-фабрик портов `FindStoredMessagesBefore/Between`, `FindLastStoredSummary`, `HasStoredPendingSummary`, `CreateStoredPendingSummary`, `SaveStoredCompletedSummary`, `MarkStoredSummaryFailed`, `FindStoredUser/GroupProfilesByIds`.
- `vkFacade/FakeVkFacadeProvider.kt` (новый) — `createVkError`, `createVkResponseDto`, `createVkProfileRequest`, `createVkProfileResponse`, `createVkSendMessageInputRequest`.
- `command/FakeCommandProvider.kt` (новый) — `createRequest(value)`, `createResponse(result)`.

Модульные фабрики:

- `src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataRequestProvider.kt` (новый) — 9 фабрик `PersistenceDataRequest.*`.
- `src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataResponseProvider.kt` (новый) — 7 фабрик `PersistenceDataResponse.*`.
- `src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/FakePublishVkEventCommandProvider.kt` (новый) — `createRequest(message: Message? = null)`.

Сторож:

- `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/TestDataFactoriesTest.kt` (новый) — два `@Test`: запрет прямого создания в `src/*/src/test/**` и обязательный дефолт у каждого параметра `create*`.

Рефакторинг 6 тестов (§5.3):

- `src/persistence/.../adapter/input/inprocess/PersistenceDataInProcessAdapterTest.kt` — `FakeModelProvider`, `FakeVoProvider.createFromId`, `FakePersistencePortProvider`, `PersistenceDataRequestProvider`, `PersistenceDataResponseProvider`.
- `src/vk-facade/.../adapter/input/inprocess/VkProfileInProcessAdapterTest.kt` — `FakeModelProvider`, `FakeVoProvider.createFromId`, `FakeVkFacadeProvider`, `FakeProfilePortProvider`.
- `src/vk-facade/.../adapter/output/vk/VkSendMessageOutputAdapterTest.kt` — `FakeVkFacadeProvider.createVkResponseDto(createVkError())`.
- `src/vk-facade/.../usecase/message/MessageSendUsecaseTest.kt` — `FakeVkFacadeProvider.createVkSendMessageInputRequest` + `FakeModelProvider.createResponseMessage`.
- `src/infrastructure/.../decorator/logging/CommandLoggingDecoratorTest.kt` — `FakeCommandProvider.createRequest/createResponse`.
- `src/receiver/.../command/sendVkEvent/PublishVkEventCommandImplTest.kt` — `FakePublishVkEventCommandProvider.createRequest`; удалён локальный `FakePublishEventOutputPort`, используется общий из `:src:testing:test-fixtures`.

Не менялись: production-код (`src/*/src/main/**`), `build.gradle.kts`, `detekt-config.yml`, `.github/**`, `docs/**`, `docs/capabilities/**`, `AGENTS.md`, `.agile/**` (только этот артефакт). Не тронуты тесты из списка «Без изменений» §5.3: `MessageTextTest`, `ConversationMessageIdTest`, `ExceptionHandlerTest`, `VkWebhookIntegrationTest`, `ReceiveMessageUsecaseTest`, `VkMediatorRouterIntegrationTest`, `MessageReceivedUsecaseInputTest`, локальные `Recording*`-фейки.

`git diff --stat` по отслеживаемым файлам задачи:

```
 .../logging/CommandLoggingDecoratorTest.kt         |  11 +-
 .../PersistenceDataInProcessAdapterTest.kt         | 133 ++++++++++++---------
 .../sendVkEvent/PublishVkEventCommandImplTest.kt   |  18 +--
 .../vkbot/testfixtures/domain/FakeVoProvider.kt    |   2 +
 .../inprocess/VkProfileInProcessAdapterTest.kt     |  27 +++--
 .../output/vk/VkSendMessageOutputAdapterTest.kt    |   7 +-
 .../usecase/message/MessageSendUsecaseTest.kt      |  11 +-
 7 files changed, 115 insertions(+), 94 deletions(-)
```

## 2. Команды и вывод

### 2.1 Сторож (AC-1, AC-3)

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --console=plain
BUILD SUCCESSFUL in 7s
32 actionable tasks: 1 executed, 31 up-to-date
exit 0
```

### 2.2 Весь :src:testing:arch-tests (AC-4)

```
$ ./gradlew :src:testing:arch-tests:test --console=plain
BUILD SUCCESSFUL in 17s
32 actionable tasks: 1 executed, 31 up-to-date
exit 0
```

Результаты из `build/test-results/test/TEST-*.xml` этого прогона:

```
com.simarel.vkbot.arch.LayerDependenciesTest: 5 tests, 0 failures
com.simarel.vkbot.arch.GradleDependenciesTest: 3 tests, 0 failures
com.simarel.vkbot.arch.DetektExcludesPathTest: 1 tests, 0 failures
com.simarel.vkbot.arch.ModuleDependenciesTest: 7 tests, 0 failures
com.simarel.vkbot.arch.TestDataFactoriesTest: 2 tests, 0 failures
arch-tests total: tests=18 failures=0
```

### 2.3 Весь `test` (AC-1, AC-2, AC-6; Docker есть)

```
$ ./gradlew test --rerun-tasks --console=plain
BUILD SUCCESSFUL in 2m 9s
65 actionable tasks: 65 executed
exit 0
```

Тестовые XML этого прогона в сумме: `tests=62 failures=0`. Состав: 59 прежних + 2 `TestDataFactoriesTest` (эта задача) + 1 `DetektExcludesPathTest` (файл параллельной board task `c40b0d2a`, уже лежал в общем рабочем каталоге). На чистом дереве без `c40b0d2a` было бы 61.

Повторный `./gradlew test` на том же дереве: `BUILD SUCCESSFUL in 1s`, `65 actionable tasks: 65 up-to-date`, exit 0.

### 2.4 Detekt + ktlint

```
$ ./gradlew detekt ktlintCheck --console=plain
BUILD SUCCESSFUL in 2s
85 actionable tasks: 4 executed, 81 up-to-date
exit 0
```

(`:src:testing:arch-tests:detekt` и `:src:testing:test-fixtures:detekt` принудительно с `--rerun-tasks` — `BUILD SUCCESSFUL`, exit 0.)

### 2.5 `./gradlew build` (AC-6)

```
$ ./gradlew build --console=plain
BUILD SUCCESSFUL in 1m 12s
157 actionable tasks: 6 executed, 151 up-to-date
exit 0
```

Полный принудительный прогон дерева после всех правок кода (далее менялись только временные строки негативных прогонов, откаченные байт-в-байт):

```
$ ./gradlew build --rerun-tasks --console=plain
BUILD SUCCESSFUL in 2m 6s
157 actionable tasks: 157 executed
exit 0
```

### 2.6 Карта возможностей (AC-6)

```
$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
exit 0
```

### 2.7 Build-файлы (AC-4)

```
$ git diff --stat -- '**/build.gradle.kts'
 src/testing/arch-tests/build.gradle.kts | 1 +
 1 file changed, 1 insertion(+)
```

Это уже лежавшая в общем рабочем каталоге правка параллельной board task `c40b0d2a` (`+    inputs.file(rootDir.resolve("detekt-config.yml"))`, к фабрикам отношения не имеет). Список разрешённых файлов этой задачи build-файлы не содержит; эта задача не добавила ни одной строки в `*.gradle.kts`. На дереве без незакоммиченных правок `c40b0d2a` команда пустая.

### 2.8 Раунд 2: повторные обязательные проверки

Все команды — на итоговом дереве после отката негативных проб; `git status --short` не содержит `probe`/`GateProbe`.

```
$ ./gradlew :src:testing:arch-tests:test --rerun-tasks --console=plain
BUILD SUCCESSFUL in 16s
32 actionable tasks: 32 executed
exit 0
```

XML: `TestDataFactoriesTest: tests=2 failures=0`; всего arch-tests `tests=18 failures=0` (Layer 5, Gradle 3, DetektExcludesPath 1, Module 7).

```
$ ./gradlew test --rerun-tasks --console=plain
BUILD SUCCESSFUL in 2m 58s
65 actionable tasks: 65 executed
exit 0
```

Сумма XML по всем модулям: `tests=62 failures=0` (44 бизнес/общих + 18 arch-tests; 61 на дереве без `DetektExcludesPathTest` параллельной задачи).

```
$ ./gradlew test --console=plain
BUILD SUCCESSFUL in 1m 21s
65 actionable tasks: 4 executed, 61 up-to-date
exit 0
```

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --console=plain
BUILD SUCCESSFUL in 7s
32 actionable tasks: 1 executed, 31 up-to-date
exit 0
```

```
$ ./gradlew build --console=plain
BUILD SUCCESSFUL in 13s
157 actionable tasks: 4 executed, 153 up-to-date
exit 0
```

```
$ ./gradlew build --rerun-tasks --console=plain
BUILD SUCCESSFUL in 2m 21s
157 actionable tasks: 157 executed
exit 0
```

```
$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
exit 0
```

```
$ git diff --stat -- '**/build.gradle.kts'
 src/testing/arch-tests/build.gradle.kts | 1 +
 1 file changed, 1 insertion(+)
```

По-прежнему только чужая строка board task `c40b0d2a`; эта задача build-файлов не меняла (см. §4.2).

Промежуточная авария: первый `./gradlew build` этого раунда упал на `:src:share:runKtlintCheckOverMainSourceSet`:

```
> Task :src:share:runKtlintCheckOverMainSourceSet FAILED
* What went wrong:
Execution failed for task ':src:share:runKtlintCheckOverMainSourceSet'.
> /Users/asmaksime1/IdeaProjects/test/vk/src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/GateProbeAnnotated.kt (No such file or directory)
```

Это ktlint-воркер долгоживущего Gradle-демона держал в снимке уже удалённый пробный файл негативного прогона §3.3. Код не менялся: после перезапуска демона (`./gradlew --stop`, свежий процесс) `build` — exit 0 (инкрементальный и полный `--rerun-tasks`). Пробных файлов в дереве нет — это артефакт среды, не рабочего diff.

## 3. Негативные прогоны (AC-3)

### 3.1 Прямое создание доменного объекта

Временная строка в `src/share/src/test/kotlin/com/simarel/vkbot/share/domain/vo/MessageTextTest.kt`:

```kotlin
val direct = com.simarel.vkbot.share.domain.model.Message.of(null, null, null, null, null, null)
```

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --console=plain
TestDataFactoriesTest > test sources create test data only through test data providers() FAILED
    java.lang.AssertionError at TestDataFactoriesTest.kt:50
> Task :src:testing:arch-tests:test FAILED
BUILD FAILED in 7s
exit 1
```

Сообщение проверки:

```
java.lang.AssertionError: Test data created without a test data provider: 1
  src/share/src/test/kotlin/com/simarel/vkbot/share/domain/vo/MessageTextTest.kt:14: Message создаётся напрямую; используй Fake*Provider из src/testing/test-fixtures или модульных src/testFixtures
```

Строка удалена (`git diff -- MessageTextTest.kt` пуст), повторный прогон:

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --console=plain
BUILD SUCCESSFUL in 7s
exit 0
```

### 3.2 Обязательный параметр фабрики

Временный параметр в `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/FakeCommandProvider.kt`:

```kotlin
fun createRequest(value: String = "test", requiredValue: String) = FakeCommandRequest(value = value)
```

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --console=plain
TestDataFactoriesTest > every factory method parameter has a default() FAILED
    java.lang.AssertionError at TestDataFactoriesTest.kt:50
> Task :src:testing:arch-tests:test FAILED
BUILD FAILED in 7s
exit 1
```

Сообщение проверки:

```
java.lang.AssertionError: Factory methods with parameters without a default: 1
  src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/FakeCommandProvider.kt:5: параметр requiredValue функции createRequest без дефолта; тест будет обязан знать о поле
```

Параметр удалён, повторный прогон:

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --console=plain
BUILD SUCCESSFUL in 7s
exit 0
```

Временных правок в рабочем diff не осталось: `grep -rn requiredValue src` и `grep -n 'Message\.of' MessageTextTest.kt` — пусто.

### 3.3 Раунд 2 — B1: аннотированное объявление

Временный `src/share/src/main/kotlin/com/simarel/vkbot/share/domain/model/GateProbeAnnotated.kt`:

```kotlin
@Suppress("unused") data class GateProbeAnnotated(val value: Int)
```

Временный `src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/probe/GateProbeDirectCreationTest.kt` (имя не совпадает ни с типом, ни с владельцем) создаёт `GateProbeAnnotated(1)`.

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain
> Task :src:testing:arch-tests:test FAILED
TestDataFactoriesTest > test sources create test data only through test data providers() FAILED
    java.lang.AssertionError at TestDataFactoriesTest.kt:50
2 tests completed, 1 failed
BUILD FAILED in 11s
exit 1
```

Сообщение проверки (из `TEST-com.simarel.vkbot.arch.TestDataFactoriesTest.xml`):

```
java.lang.AssertionError: Test data created without a test data provider: 1
  src/persistence/src/test/kotlin/com/simarel/vkbot/persistence/probe/GateProbeDirectCreationTest.kt:10: GateProbeAnnotated создаётся напрямую; используй Fake*Provider из src/testing/test-fixtures или модульных src/testFixtures
```

Оба временных файла удалены, повтор:

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain
BUILD SUCCESSFUL in 13s
32 actionable tasks: 32 executed
exit 0
```

### 3.4 Раунд 2 — B2: обязательный параметр после дефолта-функции

Временный `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/GateProbeFactoryProvider.kt`:

```kotlin
fun createProbeFactory(a: Int = 1, callback: () -> Unit = {}, required: String) = required
```

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain
TestDataFactoriesTest > every factory method parameter has a default() FAILED
    java.lang.AssertionError at TestDataFactoriesTest.kt:50
2 tests completed, 1 failed
BUILD FAILED in 11s
exit 1
```

```
java.lang.AssertionError: Factory methods with parameters without a default: 1
  src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/GateProbeFactoryProvider.kt:3: параметр required функции createProbeFactory без дефолта; тест будет обязан знать о поле
```

Тем же временным файлом добавлен `fun <T> createProbeGeneric(value: T) = value` — проверка соседнего молчаливого пропуска (type-параметры, test-reviewer nit 3):

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain
TestDataFactoriesTest > every factory method parameter has a default() FAILED
2 tests completed, 1 failed
BUILD FAILED in 13s
exit 1
```

```
java.lang.AssertionError: Factory methods with parameters without a default: 2
  src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/GateProbeFactoryProvider.kt:3: параметр required функции createProbeFactory без дефолта; тест будет обязан знать о поле
  src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/GateProbeFactoryProvider.kt:5: параметр value функции createProbeGeneric без дефолта; тест будет обязан знать о поле
```

Файл удалён, повтор:

```
$ ./gradlew :src:testing:arch-tests:test --tests "*TestDataFactoriesTest*" --rerun-tasks --console=plain
BUILD SUCCESSFUL in 14s
32 actionable tasks: 32 executed
exit 0
```

## 4. Отклонения и принятые решения

1. **`@Suppress("LongParameterList")` на `FakeModelProvider.createSummary`.** Спецификация §5.1 задаёт 9 параметров, detekt `LongParameterList.functionThreshold = 8`; `detekt-config.yml` и baseline-файлы этой задачей менять запрещено. Локальный `@Suppress` — минимальный способ сохранить сигнатуру из спецификации и оставить гейт зелёным. Альтернатива (сократить/сгруппировать параметры) нарушила бы §5.1.
2. **`git diff --stat -- '**/build.gradle.kts'` не пуст** из-за незакоммиченной правки параллельной board task `c40b0d2a` в общем рабочем каталоге. Наша задача не меняла build-файлы; правку чужой задачи не откатывал (запрещено трогать артефакты других board task). См. §2.7.
3. **62 теста в выводе вместо 61:** +1 даёт `DetektExcludesPathTest` параллельной задачи, лежащий в общем дереве; наша задача добавляет ровно 2 (stорож).
4. **`FIXED_DATE`** (`2026-01-01T00:00:00Z`) — приватная константа `FakeModelProvider`; профили создаются с фиксированным `lastUpdated`, чтобы два независимо созданных профиля были равны (требование §5.1). Наружу дата не экспортируется: тестам она не нужна.
5. **`createFromId(value: Long? = null)`** по умолчанию берёт случайный человеческий id (как `createHumanFromId`), а не `FromId.of(null)` (тот бросает `ValidationFromIdNotNullException`). Так дефолт остаётся реалистичным и не падает.
6. **Подсказка сторожа** печатает `используй Fake*Provider из src/testing/test-fixtures или модульных src/testFixtures` (в шаблоне §6.1 `<Provider>` — конкретизировал до `Fake*Provider`, чтобы подсказка была применима к любой фабрике).
7. **Сторож — текстовый скан** (как `GradleDependenciesTest`): строковые литералы не разбираются, `copy(...)` не ловится. Это зафиксировано в §6.3 спецификации и уйдёт в `docs/development/testing.md` задачей `docs-rule`.
8. **Сторож не объявлен как Gradle-input.** `:src:testing:arch-tests:test` сканирует чужие `src/*/src/test/**` текстом, но таск не зависит от них как от inputs (так же ведёт себя `GradleDependenciesTest`). Локально после правки теста вне arch-tests таск может быть `UP-TO-DATE`; поэтому негативные прогоны и финальные проверки запускались с очисткой `build/test-results` / `--rerun-tasks`. На CI `./gradlew build` с чистого дерева выполняет тесты всегда; менять build-файлы этой задаче запрещено.
9. **Раунд 2 меняет только сторож** (по консолидированной инструкции `review-summary.md` §7): `TestDataFactoriesTest.kt` — `declarationPattern`, `createFunctionPattern`, `parameters()`. Фабрики, 6 тестов и переиспользование общего `FakePublishEventOutputPort` не менялись.
10. **Неблокирующие находки раунда 1 оставлены как есть** (не входят в инструкцию §7): слияние владельцев вложенных контрактов по простому имени (code-reviewer nit 1), подсказка `Fake*Provider` вместо `PersistenceData*Provider` (nit 2), дублирование `createFromId`/`createHumanFromId` (nit 3), fail-open без `project.root` и пустой скан (security nit 1 / test nit 1), многострочные `Type`+перевод строки (test nit 2), ложные срабатывания текстового скана на комментариях/строках (test nit 4 / qa nit 4), input-wiring arch-tests (qa nit 3, требует build-файлов). Все они — nit: гейт на текущем дереве зелёный; ограничения текстового скана уходят в `docs/development/testing.md` (docs-rule).
11. **Интерфейс сторожа не менялся**: те же два `@Test` и то же сообщение; B1/B2 — исправление того, как он читает легальный Kotlin (§6.1-6.2 спецификации).

## 5. Что не сделано и что блокирует

- **AC-5 (документы) не делался** — это board task `docs-rule` по декомпозиции §12; `docs/**`, `AGENTS.md`, `docs/capabilities/**` в этой задаче не трогались намеренно. Блокеров нет.
- Прочих блокеров нет: все обязательные проверки §8 спецификации выполнены, рабочее дерево зелёное.

## 6. Решения по ходу (журнал)

1. Прочитал спецификацию и код-шаблоны (провайдеры, фейки, port/command-контракты, DTO); определил точный набор типов сторожа по §4/§6.
2. Сначала создал фабрики (§5.1-5.2) с дефолтами у каждого параметра: общие — в `test-fixtures`, модульные — в `persistence`/`receiver` `src/testFixtures`; правки `build.gradle.kts` не понадобились (`java-test-fixtures` уже подключён).
3. Создал `TestDataFactoriesTest` и прогнал его до рефакторинга: он выдал ровно 6 файлов из §5.3 и ни одного лишнего — набор типов подтверждён. Попутно исправил Kotlin-ограничение: ссылки на member-extension функции (`Declaration::isPlainClass`) запрещены, заменил на лямбды.
4. Перевёл 6 тестов на фабрики; в `PublishVkEventCommandImplTest` удалил локальный `FakePublishEventOutputPort` и подключил общий; сторож снова зелёный.
5. Прогнал `detekt`/`ktlintCheck`, устранил их замечания по минимальным правкам: `@Suppress("LongParameterList")` на 9-параметровом `createSummary`; обёртки expression-body в новых файлах; вынес список `shareDomainMarkers`, чтобы булевы хелперы сторожа уложились в 120 символов и стиль ktlint.
6. Сделал два негативных прогона §6.3; обнаружил, что Gradle считает `arch-tests:test` up-to-date при правке чужих тестов, и для доказательства очищал `build/test-results` перед прогонами; после каждого отката — снова PASSED.
7. На старте одного из полных прогонов поймал `NoSuchFileException ... in-progress-results...bin` — это была гонка с параллельным Gradle из соседней board task в общем `build`-каталоге; дождался завершения чужого wrapper и перезапустил. Позже та же соседняя задача один раз оставила `DetektExcludesPathTest` в промежуточном состоянии (падение `No group 2`); после стабилизации файла `:src:testing:arch-tests:test` снова зелёный (18/18), чужой файл не редактировал.
8. Финальные прогоны на итоговом дереве: guard PASSED, `:src:testing:arch-tests:test` 18/18 PASSED, `./gradlew test` 62/0 PASSED, `./gradlew build` exit 0, `check-capabilities.sh` OK, `git diff` по build-файлам содержит только чужую правку.
9. Прочитал `review-summary.md` (round 1): две блокирующие находки `qa-expert` (B1 аннотированное объявление, B2 `->` в параметрах), остальное — nit; консолидированная инструкция §7 — править только сторож, остальное оставить.
10. Воспроизвёл B1 текущим сторожем: временный `@Suppress("unused") data class GateProbeAnnotated(...)` + тест с `GateProbeAnnotated(1)` → до правки не ловился (как в ревью); после правки `declarationPattern` — FAILED с `path:line`. Файлы удалены, повтор PASSED.
11. Воспроизвёл B2: временный `createProbeFactory(a: Int = 1, callback: () -> Unit = {}, required: String)`; после разделения скобочных и угловых глубин и игнора `>` из `->` — FAILED с `параметр required`. Добавил временный `fun <T> createProbeGeneric(...)` (соседний пропуск test-reviewer nit 3) — FAILED с `параметр value`. Файл удалён, повтор PASSED.
12. Проверил, что аннотация в одну строку не встречается в текущих `src/main` (`grep` пуст), поэтому правка B1 не меняет зелёный набор типов; сторож на текущем дереве остался зелёным.
13. Прогнал detekt/ktlint по arch-tests (зелёно), затем обязательные проверки §2.8: guard, arch-tests 18/18, `test` 62/0, `build` exit 0 (инкрементальный и `--rerun-tasks`), `check-capabilities.sh` OK, build-файлы — только чужая строка.
14. Первый `build` раунда упал на ktlint из-за устаревшего снимка удалённого пробного файла в Gradle-демоне; перезапустил демон (`--stop`) и полный `build --rerun-tasks` — exit 0, пробных файлов нет.
