# impl.md — docs-rule

- Board task: `docs-rule` (стадия `implementation`), task `0544056b-a6ef-4bd9-ac77-fba232fffd5f`.
- Спецификация: `.agile/tasks/0544056b-a6ef-4bd9-ac77-fba232fffd5f/spec.md` (§4, §7 AC-5, §9).
- Рабочая директория: `/Users/asmaksime1/IdeaProjects/test/vk`.
- Дата прогона: 2026-10-29. Зависимость `factories-and-gate` доставлена: фабрики и
  `TestDataFactoriesTest` уже лежат в рабочем дереве, документы до этой задачи их не описывали.
- Раунд 2: доработка по ревью раунда 1 (`review-summary.md` §2, §6). Изменены только документы;
  нового кода, вариантов правил и новых capability-строк нет.

## Контракт (AC спецификации, до правок)

| AC | Формулировка | Чем закрыт |
|---|---|---|
| AC-5 | в `docs/development/testing.md`, `AGENTS.md` §8 и разделе Factory `docs/architecture/patterns.md` записано правило §4, исключения, размещение фабрик, имя сторожа и порядок «добавили поле — добавили дефолт в фабрику»; все названные имена/пути существуют | §2.4-§2.6: grep всех трёх файлов, `ls` 10 фабричных путей, проверка 50 извлечённых путей, `sed` диапазонов |
| AC-6 (часть `capabilitiesCheck`) | `./scripts/check-capabilities.sh` → `OK` | §2.3 |
| AC-6 (часть `test`) | `./gradlew test` зелёный, число тестов процитировано | §2.1 |

§10 `AGENTS.md`, ADR, `detekt-config.yml`, build-файлы, `.github/**` и код (`src/**`) не трогались.

## 0. Раунд 2 — исправления по ревью

Ревью раунда 1 (`review-summary.md`) вернуло задачу с двумя блокирующими находками `spec-critic`
и пятью nit. Исправлены все; консолидированная инструкция §6 выполнена целиком.

### B1 — правило называло `Fake*Provider`, а две доставленные фабрики не `Fake*`

- **`docs/development/testing.md:104-105`:** «Правило: **тестовые данные создаются только фабриками
  `*Provider`**; конвенция имени — `Fake*Provider`, у persistence-фабрик — `PersistenceData*Provider`.»
  Пункт 2 больше не противоречит пункту 1: он говорит «из фабрики `*Provider`».
- **`AGENTS.md:169-170`:** «Test data comes only from `*Provider` factories (naming convention
  `Fake*Provider`; persistence factories are `PersistenceData*Provider`)».
- **`docs/architecture/patterns.md:71-72`:** «фабрики `*Provider` (конвенция имени — `Fake*Provider`,
  у persistence — `PersistenceData*Provider`)»; `PersistenceDataRequestProvider.kt` больше не назван
  `Fake*Provider`.
- Цитата реального сообщения сторожа (`testing.md:165`, «используй Fake*Provider …») не менялась:
  она воспроизводит текст `TestDataFactoriesTest.kt:108-109`, который правит кодовая задача.
- Имя `TestDataFactoriesTest` осталось в каждом документе, `PersistenceDataRequestProvider` —
  в перечне фабрик, как и раньше.

### B2 — устаревшие диапазоны строк рефакторенных тестов

- `docs/development/testing.md:186` → `PersistenceDataInProcessAdapterTest.kt:24-41`,
  `VkProfileInProcessAdapterTest.kt:16-34`.
- `AGENTS.md:186` → `PersistenceDataInProcessAdapterTest.kt:24-41`.
- 24 — `@Test`, 41 — закрывающая `}` первого теста; 16 — `@Test`, 34 — `}` первого теста
  (проверено `sed`, §2.6). Это те же диапазоны, что в capability-файлах (`24-64` покрывает оба
  теста `Before`/`Between`, `16-34` — первый), противоречия между документами больше нет.

### Nit из `review-summary.md` §3 — все пять взяты, ни одного не отложено

1. **Границы сторожа (`testing.md:169-176`).** В «Ограничение» добавлены три честных промаха
   текстового скана: дефолт с оператором сравнения (`limit > 0`) сбивает угловую глубину и прячет
   обязательный параметр после него; аннотация после модификатора (`public @Suppress(...) data class`)
   не собирается; без `project.root` скан пуст (fail-open). Отдельно сказано, что имя типа в
   комментарии/строке даёт ложное срабатывание, — раньше следствие было неявным.
2. **Область `patterns.md` (nit spec-critic 1).** «В тестах» теперь перечисляет типы: доменные
   модели/VO/события `share`, DTO `**/adapter/output/**/dto/**`, request/response `**/port/**` и
   `**/command/**`; фейки-зависимости и исключения-ошибки под запрет не попадают.
3. **Тест вложенного контракта (nit spec-critic 2).** Формулировка `<Type>Test.kt`; для вложенного
   контракта — `<Owner>Test.kt` теперь есть в `testing.md`, `AGENTS.md` и `patterns.md`, как в
   `TestDataFactoriesTest.kt:88-92`.
4. **«Общие/модульные типы» (nit spec-critic 4).** `testing.md:111-113` и `patterns.md:72-74` явно
   делят: `share` и `vk-facade` → `:src:testing:test-fixtures`; `persistence`/`receiver`/`processor` →
   `<module>/src/testFixtures`. В `AGENTS.md:171-173` тот же классификатор.
5. **Дубль размещения в `patterns.md` (nit code-reviewer 4).** Список фабрик и их расположение
   остались в одном пункте «Где»; «В тестах» описывает только запрет, дефолты и сторожа.

## 1. Файлы и зачем

Изменены только 6 разрешённых файлов:

- `docs/development/testing.md` — раздел «Провайдеры тестовых данных» переписан в правило §4 (что
  запрещено, где фабрики, дефолты, исключения, фейки-зависимости, сторож); добавлены перечень всех
  фабрик и пример «добавили поле в модель»; новый подраздел «Сторож правила» (`TestDataFactoriesTest`,
  два теста, негативные прогоны, ограничение текстового скана и его границы); дерево fake-объектов и
  фабрик обновлено новыми файлами; число тестов и раскладка по модулям заменены на фактический прогон;
  в таблицу архитектурных тестов добавлены `TestDataFactoriesTest` и `DetektExcludesPathTest`.
- `AGENTS.md` — только §6 (строки `./gradlew test` → 62 теста; `:src:testing:arch-tests:test` → 18
  тестов и `TestDataFactoriesTest`) и §8 (норма правила вместо одной строки про `FakeVoProvider`;
  классификатор «общие/модульные типы», исключение теста вложенного контракта).
- `docs/architecture/patterns.md` — раздел Factory (companion `of`): в тестах `of(...)`/конструкторы
  запрещены для типов правила, кроме теста самого типа; данные из `*Provider` с дефолтами; сторож
  `TestDataFactoriesTest`.
- `docs/capabilities/message-storage.md` — строки 84 и 87: только новые диапазоны строк
  `PersistenceDataInProcessAdapterTest` (`38-62`→`24-64`, `111-123`→`119-133`).
- `docs/capabilities/vk-profiles.md` — строки 67 и 70: только диапазоны
  `VkProfileInProcessAdapterTest` (`17-33`→`16-34`, `35-51`→`36-52`).
- `docs/capabilities/vk-message-send.md` — строки 67, 70, 74: только диапазоны
  `MessageSendUsecaseTest` (`12-29`→`12-34`) и `VkSendMessageOutputAdapterTest`
  (`15-28`→`14-27`, `30-44`→`29-45`); строка 71 (`SendVkMessageCommandImplTest`) не менялась.

Новых capability-строк, новых путей и новых зависимостей нет.

`git diff --stat` по этим файлам (итоговое дерево раунда 2):

```
 AGENTS.md                            |  27 ++++++---
 docs/architecture/patterns.md        |  11 +++-
 docs/capabilities/message-storage.md |   4 +-
 docs/capabilities/vk-message-send.md |   6 +-
 docs/capabilities/vk-profiles.md     |   4 +-
 docs/development/testing.md          | 110 ++++++++++++++++++++++++++++++-----
 6 files changed, 131 insertions(+), 31 deletions(-)
```

(В общем `git diff` рабочего дерева есть ещё незакоммиченные правки зависимой задачи
`factories-and-gate` и параллельной `c40b0d2a` — `docs/adr/0005`, `detekt-config.yml`, `src/**`,
`AGENTS.md` §10; эта задача их не трогала. См. §4.1.)

## 2. Команды и вывод

### 2.1 Полный прогон тестов (AC-6, часть `test`)

```
$ ./gradlew test --rerun-tasks --console=plain
BUILD SUCCESSFUL in 2m 3s
65 actionable tasks: 65 executed
exit 0
```

Сумма `build/test-results/test/TEST-*.xml` этого прогона (21 файл, включая
`src/testing/arch-tests`):

```
app 7 (InitTest 1 + VkWebhookIntegrationTest 6)
infrastructure 2
persistence 9
processor 2
receiver 9
share 8
testing/arch-tests 18 (Module 7 + Layer 5 + Gradle 3 + TestDataFactories 2 + DetektExcludesPath 1)
vk-facade 7
TOTAL tests=62 failures=0 errors=0 skipped=0
```

62/18 — фактическое число на общем дереве: 59 прежних + 2 `TestDataFactoriesTest` (наша зависимость)
+ 1 `DetektExcludesPathTest` параллельной board task `c40b0d2a`, файл которой уже лежит в рабочем
каталоге. На дереве без `c40b0d2a` — 61/17; документы описывают дерево как построено («the code
wins»), поэтому в них стоят 62/18.

Повторный инкрементальный прогон на итоговом дереве (до последней правки формулировок):

```
$ ./gradlew test --console=plain
BUILD SUCCESSFUL in 1s
65 actionable tasks: 65 up-to-date
exit 0
```

### 2.2 `./gradlew capabilitiesCheck` (AC-6)

```
$ ./gradlew capabilitiesCheck --console=plain
> Task :capabilitiesCheck
OK: capability map paths, test paths, statuses and ADR ids resolve
BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
exit 0
```

### 2.3 Карта возможностей (AC-6)

```
$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
exit 0
```

### 2.4 Имена правила во всех трёх документах (AC-5)

`rg` на машине не установлен (`bash: rg: command not found`, exit 127), поэтому тот же запрос выполнен
`grep -nE` с теми же шаблонами:

```
$ grep -nE "TestDataFactoriesTest|FakeModelProvider|FakeVkFacadeProvider|PersistenceDataRequestProvider" \
    docs/development/testing.md AGENTS.md docs/architecture/patterns.md
docs/development/testing.md:36:  ... TestDataFactoriesTest (2) ...
docs/development/testing.md:81:  │   ├── FakeModelProvider.kt
docs/development/testing.md:88:  └── vkFacade/FakeVkFacadeProvider.kt
docs/development/testing.md:90:  ... # PersistenceDataRequestProvider,
docs/development/testing.md:120: 6. Нарушение ловит `TestDataFactoriesTest` ...
docs/development/testing.md:128: - `domain/FakeModelProvider.kt` — ...
docs/development/testing.md:137: - `vkFacade/FakeVkFacadeProvider.kt` — ...
docs/development/testing.md:140: - `PersistenceDataRequestProvider.kt` и `PersistenceDataResponseProvider.kt` ...
docs/development/testing.md:148: 2. в `FakeModelProvider.createSummary` — параметр ...
docs/development/testing.md:156: `TestDataFactoriesTest` (`.../TestDataFactoriesTest.kt`) ...
docs/development/testing.md:222: | `TestDataFactoriesTest` | сторож правила ... |
AGENTS.md:129: ... plus the `TestDataFactoriesTest` guard (18 tests) ...
AGENTS.md:171: ... `FakeModelProvider.kt`,
AGENTS.md:172: `FakeVkFacadeProvider.kt` ... `<module>/src/testFixtures`
AGENTS.md:173: (`PersistenceDataRequestProvider.kt`, `PersistenceDataResponseProvider.kt`, ...
AGENTS.md:181: ... The guard is `TestDataFactoriesTest` ...
docs/architecture/patterns.md:73: ... `FakeModelProvider.kt`,
docs/architecture/patterns.md:74: `FakeVkFacadeProvider.kt` ... (`PersistenceDataRequestProvider.kt`, ...
docs/architecture/patterns.md:80: ... Нарушение ловит `TestDataFactoriesTest` ...
exit 0
```

Все три файла в выводе, каждое из четырёх имён названо в каждом.

### 2.5 Старые числа тестов отсутствуют

```
$ grep -nE "59 тест|15 тест" AGENTS.md docs/development/testing.md
(пусто)
exit 1
```

Дополнительно `grep -nE "59 tests|15 tests|61 тест|61 tests|17 тест"` по тем же файлам — пусто, exit 1.
В документах остались `62 теста`/arch-tests `18` (`testing.md:21-23`) и `18 tests`/`18 тестов`
(`AGENTS.md:129`, `testing.md:12`).

### 2.6 Каждый названный в документах путь кода существует (AC-5)

Все `src/**/*.{kt,kts,yml,yaml}`, извлечённые из шести изменённых документов:

```
$ for p in $(grep -rhoE '(src/[A-Za-z0-9_./-]+\.(kt|kts|yml|yaml))' \
    docs/development/testing.md AGENTS.md docs/architecture/patterns.md \
    docs/capabilities/message-storage.md docs/capabilities/vk-profiles.md \
    docs/capabilities/vk-message-send.md | sort -u); do
    test -e "$p" && echo "OK $p" || echo "MISSING $p"; done | ...
OK count: 50 MISSING count: 0
```

Отдельный `ls` по именам, которые цитируют документы (все `OK`, exit 0):

```
OK src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeModelProvider.kt
OK src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/port/FakeProfilePortProvider.kt
OK src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/port/FakePersistencePortProvider.kt
OK src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/vkFacade/FakeVkFacadeProvider.kt
OK src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/command/FakeCommandProvider.kt
OK src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt
OK src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataRequestProvider.kt
OK src/persistence/src/testFixtures/kotlin/com/simarel/vkbot/persistence/testfixtures/PersistenceDataResponseProvider.kt
OK src/receiver/src/testFixtures/kotlin/com/simarel/vkbot/receiver/fixtures/FakePublishVkEventCommandProvider.kt
OK src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/TestDataFactoriesTest.kt
```

### 2.7 Диапазоны строк указывают на `@Test` и закрывающую `}`

```
$ sed -n '24p;41p' PersistenceDataInProcessAdapterTest.kt      # testing.md/AGENTS.md: 24-41
    @Test
    }
$ sed -n '16p;34p' VkProfileInProcessAdapterTest.kt            # testing.md: 16-34
    @Test
    }
$ sed -n '24p;64p;119p;133p' PersistenceDataInProcessAdapterTest.kt   # capability: 24-64, 119-133
    @Test
    }
    @Test
    }
$ sed -n '16p;34p;36p;52p' VkProfileInProcessAdapterTest.kt    # capability: 16-34, 36-52
    @Test
    }
    @Test
    }
$ sed -n '12p;34p' MessageSendUsecaseTest.kt                   # capability: 12-34
    @Test
    }
$ sed -n '14p;27p;29p;45p' VkSendMessageOutputAdapterTest.kt    # capability: 14-27, 29-45
    @Test
    }
    @Test
    }
```

### 2.8 Устаревшие диапазоны отсутствуют

```
$ grep -nE "38-49|17-33|14-62" docs/development/testing.md AGENTS.md docs/architecture/patterns.md
(пусто)
exit 1
```

`docs/architecture/modules.md:164` по-прежнему цитирует `FakeVoProvider.kt:14-62` (файл теперь 18-69),
но `modules.md` — вне разрешённых файлов этой задачи (см. §5).

## 3. Что именно изменилось по смыслу

- **Правило.** Тестовые данные создаются только фабриками-провайдерами `*Provider`; конвенция имени —
  `Fake*Provider`, у persistence — `PersistenceData*Provider`. Запрещено прямое `Type(...)`/`Type.of(...)`
  для доменных моделей/VO/событий `share`, DTO `**/adapter/output/**/dto/**`, request/response
  `**/port/**` и `**/command/**` (включая вложенные контракты). Общие типы (`share`, `vk-facade`) —
  `:src:testing:test-fixtures`, типы `persistence`/`receiver`/`processor` — `<module>/src/testFixtures`.
  У каждого параметра `create*` есть реалистичный дефолт. Исключения — тест самого типа
  (`<Type>Test.kt`; для вложенного контракта `<Owner>Test.kt`), исключения-ошибки, сырые JSON-тела
  интеграционных тестов; фейки-зависимости (`Fake*`, `Recording*`) создаются напрямую. Сторож —
  `TestDataFactoriesTest`.
- **Порядок «добавили поле»** записан примером: поле в тип → дефолтный параметр в фабрику → тесты не
  меняются.
- **Сторож** описан двумя тестами, форматом падения (`path:line`, имя параметра), негативными
  прогонами (прямое создание → FAILED; обязательный параметр → FAILED; после отката PASSED) и
  ограничением текстового скана (строки/комментарии и ложные срабатывания на них, `copy(...)`,
  `Type` и `.of(` на разных строках, пакеты вне правила, `UP-TO-DATE` инкрементального
  arch-tests-прогона, дефолт с `<`/`>`, аннотация после модификатора, fail-open без `project.root`).
- **Раскладка.** В дерево добавлены `FakeModelProvider.kt`, `FakeProfilePortProvider.kt`,
  `FakePersistencePortProvider.kt`, `FakeVkFacadeProvider.kt`, `FakeCommandProvider.kt`,
  `PersistenceDataRequestProvider.kt`, `PersistenceDataResponseProvider.kt`,
  `FakePublishVkEventCommandProvider.kt`; `createFromId` назван в списке методов `FakeVoProvider`.
- **Числа.** 62 теста, arch-tests 18; раскладка по модулям — из XML прогона §2.1.

## 4. Отклонения от спецификации и решения

1. **Число тестов 62/18 вместо 61/17 из §9 спецификации.** Фактический прогон на общем дереве даёт 62:
   59 прежних + 2 `TestDataFactoriesTest` + 1 `DetektExcludesPathTest` параллельной board task
   `c40b0d2a`, файл которой уже лежит в общем рабочем дереве. Документы описывают дерево как построено
   («the code wins») и цитируют фактический вывод (§2.1), поэтому в `testing.md` и `AGENTS.md` стоят
   62/18, а в таблицу архитектурных тестов добавлена строка `DetektExcludesPathTest`. На дереве без
   `c40b0d2a` те же документы читаются как 61/17 — расхождение ровно в один чужой тест.
2. **`rg` недоступен.** Спецификация требует `rg -n ...`; на машине `rg` нет (`exit 127`), запросы
   выполнены эквивалентным `grep -nE` с теми же шаблонами и зафиксированными exit code (§2.4, §2.5).
3. **`AGENTS.md` §8, первая строка.** К списку модульных `testFixtures` (`receiver`, `processor`)
   добавлен `persistence`: каталог `src/persistence/src/testFixtures` уже есть, без него норма §8
   противоречит дереву. Это в разрешённом §8 и не меняет норму.
4. **Переименование подраздела дерева.** «Раскладка fake-объектов» → «Раскладка fake-объектов и
   фабрик»: дерево теперь содержит и фейки, и фабрики, отдельного второго дерева не заводил (одно
   место, без дублирования путей).
5. **`DetektExcludesPathTest` в таблицах `testing.md`.** См. §4.1: файл есть в дереве, поэтому описан
   в «Где лежат тесты» и «Архитектурные тесты». Правило тестовых данных от этого не меняется.
6. **`FakeVoProvider.kt:14-62` → `18-69`.** Диапазон сдвинулся добавленной `createFromId` и новыми
   константами; в `testing.md` и `patterns.md` стоит проверенный `18-69` (файл — 69 строк, `object`
   с 18).
7. **Диапазоны capability-тестов** выбраны по той же семантике, что и старые: от `@Test` до
   закрывающей `}` теста (для AC-1 message-storage — оба теста `Before`/`Between`, как и раньше).
   `scripts/check-capabilities.sh` диапазоны не валидирует, поэтому проверены вручную (§2.7).
8. **Раунд 2: B1/B2 и nit 1-5** — см. §0. Все пять nit взяты; отложенных нет. Ничего из раунда 1 не
   потеряно: правило, дерево, пример «добавили поле», описание сторожа, числа и capability-диапазоны
   остались, изменились только формулировки правила, диапазоны ссылок и границы сторожа.
9. **`./gradlew build` не запускался** в этой задаче: правки только в документах, задание требует
   `./gradlew test` и `check-capabilities.sh`, а AC-6 закрыт в части `capabilitiesCheck`; полный
   `build` доказан в `factories-and-gate` на том же коде. Если ревьюер хочет полный гейт — он ничего
   не меняет в документах, команда приведена в §6.
10. **Негативные прогоны сторожа раундом 2 не повторялись:** они проверяют код сторожа, а он в этой
    задаче не менялся; их вывод зафиксирован в `factories-and-gate/impl.md` §3.1-§3.4, на который
    ссылается §2.1.

## 5. Что не сделано и что блокирует

- Ничего из AC-5 не осталось: правило записано в трёх документах, имя сторожа названо, все имена и
  пути проверены, числа обновлены, capability-диапазоны сдвинуты.
- Блокеров нет. `c40b0d2a` не трогаю (чужая board task); его `DetektExcludesPathTest` и §10 `AGENTS.md`
  остаются в общем дереве как есть.
- Вне скоупа и намеренно не сделано: код `src/**`, `build.gradle.kts`, `detekt-config.yml`,
  `.github/**`, ADR, новые capability-строки, `AGENTS.md` §10; устаревшая ссылка
  `docs/architecture/modules.md:164` (файл вне разрешённых).
- Известное ограничение вне скоупа: `docs/architecture/modules.md:164` всё ещё цитирует
  `FakeVoProvider.kt:14-62` — отдельная follow-up задача.

## 6. Быстрая перепроверка

```bash
./gradlew test                                   # 62/0, exit 0
./scripts/check-capabilities.sh                  # OK, exit 0
./gradlew capabilitiesCheck                      # exit 0
grep -nE "TestDataFactoriesTest|FakeModelProvider|FakeVkFacadeProvider|PersistenceDataRequestProvider" \
  docs/development/testing.md AGENTS.md docs/architecture/patterns.md   # все три файла
grep -nE "59 тест|15 тест" AGENTS.md docs/development/testing.md          # пусто, exit 1
grep -nE "38-49|17-33" docs/development/testing.md AGENTS.md docs/architecture/patterns.md  # пусто, exit 1
```

## 7. Журнал решений (в порядке выполнения)

1. Прочитал `spec.md` (§4, §7 AC-5, §9) и артефакты зависимости (`factories-and-gate/impl.md`,
   `review-summary.md`): фабрики и сторож уже в дереве, документы описывают состояние до них.
2. Прочитал все фабрики, `TestDataFactoriesTest`, `DetektExcludesPathTest` и шесть рефакторенных тестов
   с номерами строк — имена/пути/диапазоны беру из файлов, не из спецификации.
3. Прочитал целевые документы: `docs/development/testing.md` (разделы «Команды», «Где лежат тесты»,
   «Раскладка fake-объектов», «Провайдеры тестовых данных», «Архитектурные тесты»), `AGENTS.md` §6/§8,
   Factory в `patterns.md`, три capability-файла и `index.md`.
4. Запустил `./gradlew test --rerun-tasks --console=plain` в фоне; параллельно правил документы.
5. `patterns.md`: раздел Factory получил пункт «В тестах» и имя сторожа.
6. `AGENTS.md`: §8 — норма правила вместо строки про `FakeVoProvider`; §6 — 62 теста и 18 arch-tests с
   `TestDataFactoriesTest`. §10 не тронут.
7. `testing.md`: обновлено дерево (новые фабрики + `persistence` в модульных `testFixtures`), переписан
   раздел провайдеров в правило, добавлены перечень фабрик, пример «добавили поле», подраздел «Сторож
   правила» с негативными прогонами и ограничением; числа и раскладка — из фактического прогона;
   в таблицы добавлены `TestDataFactoriesTest` и `DetektExcludesPathTest`.
8. Capability-файлы: пересчитал диапазоны по новым файлам тестов (от `@Test` до `}`) и поменял только
   строки 84/87, 67/70, 67/70/74 — по одной правке на строку, остальной текст не тронут.
9. Дождался прогона: `BUILD SUCCESSFUL in 2m 7s`, exit 0; посчитал XML — 62/0, раскладка совпала с
   ожидаемой. Написал числа в документы.
10. Прогнал `./scripts/check-capabilities.sh` → `OK`, exit 0; `./gradlew capabilitiesCheck` → exit 0.
11. Выполнил `grep`-проверки AC-5: имена во всех трёх файлах (exit 0), старые числа отсутствуют
    (exit 1), 50 извлечённых путей существуют (0 missing), диапазоны указывают на `@Test`/`}`.
12. Повторный `./gradlew test --console=plain` после правок документов → `BUILD SUCCESSFUL`, exit 0.
13. **Раунд 2.** Прочитал `review-summary.md` раунда 1: B1 (имя семейства фабрик), B2 (устаревшие
    диапазоны), пять nit. Проверил B2 по файлам: первый тест `PersistenceDataInProcessAdapterTest` —
    `@Test` 24 / `}` 41, `VkProfileInProcessAdapterTest` — `@Test` 16 / `}` 34.
14. Исправил B1 во всех трёх документах (правило — `*Provider`, конвенция `Fake*Provider`, исключение
    `PersistenceData*Provider`) и B2 (диапазоны 24-41, 16-34). Строку с цитатой реального сообщения
    сторожа не трогал.
15. Взял все пять nit: границы сторожа, область запрета в `patterns.md`, тест вложенного контракта,
    классификатор «общие/модульные типы», дубль размещения в `patterns.md`.
16. Повторно прогнал все проверки §6 на итоговом дереве: `./gradlew test --rerun-tasks --console=plain`
    → `BUILD SUCCESSFUL in 2m 3s`, exit 0, XML `TOTAL tests=62 failures=0`; `check-capabilities.sh` →
    `OK`; `capabilitiesCheck` → exit 0; AC-5 grep → exit 0 (все три файла); старые числа и диапазоны →
    пусто, exit 1; 50/50 путей существуют; `sed` новых диапазонов — `@Test`/`}`.
17. Записал этот `impl.md`; код, build-файлы, ADR, detekt, `.github/**` и `AGENTS.md` §10 не менялись.
