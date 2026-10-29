# impl.md — make-lint-path-independent

## 0. Раунд переработки: что изменено после review round 1

Review round 1 вынес **одну блокирующую находку B1**: сторож извлекал глобы только в одинарных
кавычках (`QUOTED = Regex("""'([^']+)'""")`), поэтому валидный YAML-глоб `"**/test/**"` в двойных
кавычках он не видел и оставался зелёным, хотя detekt правило применял и локальная слепота
возвращалась. Правка — только в файле теста, две строки:

```diff
-            .map { it.groupValues[1] }
+            .map { it.groupValues[2] }
-        private val QUOTED = Regex("""'([^']+)'""")
+        private val QUOTED = Regex("""(['"])([^'"]+)\1""")
```

`detekt-config.yml`, документы, `src/testing/arch-tests/build.gradle.kts`, исходники и `.github/**`
в этом раунде **не менялись** — как требует §«Что требуется в раунде переработки» review-summary.

Актуальные строки файла: `DetektExcludesPathTest.kt:38` (`groupValues[2]`), `:54` (regex).

## 1. Контракт: AC зафиксированы до правок кода

Источник — `spec.md` (разделы 2, 4, 5, 7, 8) и спецификация board-task. Обязательны AC-2, AC-3;
AC-1/AC-4/AC-5 — зелёные прогоны и документы.

- **AC-2.** КОГДА `./gradlew detekt` выполняется из `/Users/asmaksime1/IdeaProjects/test/vk`,
  СИСТЕМА ДОЛЖНА применять к main-исходникам тот же набор правил, что и из нейтрального пути.
  *Тест:* сторож `DetektExcludesPathTest` + прогоны `detekt ktlintCheck` локально и в нейтральной
  копии с одинаковым результатом (отчёты `detekt.md`).
- **AC-3.** КОГДА глоб `excludes` в `detekt-config.yml` матчит main-исходник чек-аута, лежащего в
  каталоге с именем `test`, СИСТЕМА ДОЛЖНА упасть на `:src:testing:arch-tests:test` и назвать
  виновный глоб. *Тест:* негативные прогоны
  `./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*"`; после отката — PASSED.
- **AC-1.** КОГДА `./gradlew detekt ktlintCheck` выполняется в чек-ауте без каталога `test` в пути,
  СИСТЕМА ДОЛЖНА завершиться с кодом 0 без находок.
- **AC-4.** КОГДА после правок запускается полный набор тестов (`./gradlew build`), СИСТЕМА ДОЛЖНА
  сохранить все существующие тесты зелёными (exit 0).
- **AC-5.** КОГДА изменения влиты, СИСТЕМА ДОЛЖНА сохранять карту возможностей и ADR в
  соответствии с кодом: `./scripts/check-capabilities.sh` и ADR 0005 — exit 0 / документ обновлён.

Дополнительно из спецификации board-task: пункт 1 — анкоринг всех широких тестовых глобов
(`grep -c "'**/test/**'"` → 0, `'**/*.kts'` там, где был); пункт 2 — `DetektExcludesPathTest`;
пункт 3 — негативный прогон; пункт 4 — раздел ADR 0005 и строка в `AGENTS.md` §10. Набор правил,
пороги, исходники, `.github/**` и Gradle-зависимости не меняются.

## 2. Изменённые файлы

| Файл | Раунд | Почему |
|---|---|---|
| `detekt-config.yml` | 1 | 15 списков `excludes`: каждый широкий глоб заменён анкоренным по source set (`**/test/**` → `**/src/test/**`, `**/androidTest/**` → `**/src/androidTest/**`, …, `'**/*.kts'` сохранён). Правила, пороги, `active` не тронуты. |
| `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/DetektExcludesPathTest.kt` | 1 + 2 | Сторож: извлекает глобы из `detekt-config.yml`, строит синтетические main-исходники `/tmp/test/vk/...` и `/tmp/work/vk/...`, через `java.nio.file` glob-PathMatcher падает и называет виновный глоб. Один `@Test`, на диск не пишет, новых зависимостей нет. **Раунд 2:** regex принимает оба стиля кавычек (B1). |
| `src/testing/arch-tests/build.gradle.kts` | 1 | `detekt-config.yml` объявлен входом `Test`-задач модуля (одна строка, `:43`): правка конфига перезапускает сторожа, а не оставляет задачу `UP-TO-DATE`. |
| `docs/adr/0005-quality-gates-and-ci.md` | 1 | Раздел «Независимость гейта линта от пути чек-аута» (`:35`): абсолютный путь в глобах, выключение правил локальным каталогом `test`, анкоринг на `**/src/test/**`, сторож, негативный прогон, вход конфига у `Test`-задачи. |
| `AGENTS.md` | 1 | §10, пункт про detekt (`:205-207`): анкоринг `excludes` на source sets, запрет широкого глоба по имени каталога, имя сторожа. |

Не тронуты: исходники приложения, набор правил и пороги detekt, `.github/**`, Gradle-зависимости,
capability-файлы (поведение продукта не меняется, строк гейтов в карте нет), артефакты других
board-task.

## 3. Команды и вывод

Базовое состояние доказательств — **чистая копия** `/tmp/vk-lint-rework`: `git archive HEAD` +
ровно 5 файлов этой задачи. Она нужна потому, что в общем рабочем дереве параллельно идёт задача
`0544056b` со своими незакоммиченными файлами (риск 1 review-summary); копия фиксирует только scope
этой задачи.

```console
$ git archive HEAD | tar -x -C /tmp/vk-lint-rework
$ for f in detekt-config.yml docs/adr/0005-quality-gates-and-ci.md AGENTS.md \
          src/testing/arch-tests/build.gradle.kts \
          src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/DetektExcludesPathTest.kt; do
    mkdir -p "/tmp/vk-lint-rework/$(dirname "$f")"; cp "$f" "/tmp/vk-lint-rework/$f"; done
$ md5 -q <каждый из 5 файлов> <тот же файл в копии>
5f7a794fb466e9c8223969f272e69947  5f7a794fb466e9c8223969f272e69947   detekt-config.yml
abb271fc5334da3831d9aea1598cfa85  abb271fc5334da3831d9aea1598cfa85   docs/adr/0005…
f570f2c13423176aacbaa7828e3a5641  f570f2c13423176aacbaa7828e3a5641   AGENTS.md
07e04c71bea7faadddbb73db0ec9015b  07e04c71bea7faadddbb73db0ec9015b   arch-tests/build.gradle.kts
79177f175056ab83565b45596c9f3d80  79177f175056ab83565b45596c9f3d80   DetektExcludesPathTest.kt
$ realpath /tmp/vk-lint-rework
/private/tmp/vk-lint-rework
```

В копии нет чужих файлов: `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/` содержит
только 4 arch-теста (без `TestDataFactoriesTest.kt`), `src/persistence/src/` — только `main test`
(без `testFixtures/`). Путь копии не содержит каталога `test`.

### 3.1 Инварианты конфига (AC-2, пункт 1 спеки)

```console
$ grep -c "'\*\*/test/\*\*'" detekt-config.yml
0
$ grep -c "'\*\*/\*\.kts'" detekt-config.yml
2
$ git diff --stat detekt-config.yml
 detekt-config.yml | 30 +++++++++++++++---------------
 1 file changed, 15 insertions(+), 15 deletions(-)
$ git diff detekt-config.yml | grep -E '^[-+]' | grep -vE '^(\+\+\+|---)' | grep -vc "excludes:"
0
```

Изменены только 15 строк `excludes:`, ни одно правило/порог не затронуто. `config.validation: true`
(`detekt-config.yml:10-15`) — прогоны ниже означают, что конфиг валиден.

### 3.2 Сторож: положительный прогон на чистом конфиге (AC-2)

```console
$ cd /tmp/vk-lint-rework
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain
> Task :src:testing:arch-tests:test

BUILD SUCCESSFUL in 8s
30 actionable tasks: 30 executed
EXIT:0
```

```console
$ grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    src/testing/arch-tests/build/test-results/test/TEST-com.simarel.vkbot.arch.DetektExcludesPathTest.xml
tests="1" skipped="0" failures="0" errors="0"
```

### 3.3 Негативный прогон `'**/test/**'` в одинарных кавычках (AC-3)

В список `excludes` правила `MagicNumber` (`detekt-config.yml:624`) добавлен первый элемент
`'**/test/**'`; больше ничего не менялось.

```console
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain
DetektExcludesPathTest > detekt excludes do not match main sources when the checkout directory is named test() FAILED
...
BUILD FAILED in 6s
30 actionable tasks: 1 executed, 29 up-to-date
EXIT:1
```

Точная строка JUnit-отчёта (в атрибуте `<`/`>` экранированы; суффикс `expected/but was` — тот, на
который указывал nit N2 предыдущего review):

```xml
<failure message="org.opentest4j.AssertionFailedError: detekt-config.yml excludes match main sources: [**/test/**]. Anchor test source set globs, for example **/src/test/**. ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;" type="org.opentest4j.AssertionFailedError">
```

### 3.4 B1: негативный прогон `"**/test/**"` в двойных кавычках (исправление раунда 1)

Тот же список, глоб записан в **двойных** кавычках (валидный YAML, detekt его применяет). В раунде 1
этот прогон был зелёным — теперь падает:

```console
$ sed -n '624p' detekt-config.yml
    excludes: ["**/test/**", '**/src/test/**', '**/src/androidTest/**', '**/src/commonTest/**', '**/src/jvmTest/**', '**/src/androidUnitTest/**', '**/src/androidInstrumentedTest/**', '**/src/jsTest/**', '**/src/iosTest/**', '**/*.kts']
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain
DetektExcludesPathTest > detekt excludes do not match main sources when the checkout directory is named test() FAILED
...
BUILD FAILED in 13s
30 actionable tasks: 1 executed, 29 up-to-date
EXIT:1
```

```xml
<failure message="org.opentest4j.AssertionFailedError: detekt-config.yml excludes match main sources: [**/test/**]. Anchor test source set globs, for example **/src/test/**. ==&gt; expected: &lt;true&gt; but was: &lt;false&gt;" type="org.opentest4j.AssertionFailedError">
```

Проверка покрытия тем же тестом: `tests="1" skipped="0" failures="1" errors="0"`.

### 3.5 B1: block-форма с двойными кавычками

Список `MagicNumber` переписан в block-форму, все элементы в двойных кавычках:

```console
$ sed -n '622,632p' detekt-config.yml
  MagicNumber:
    active: true
    excludes:
      - "**/test/**"
      - "**/src/test/**"
      - "**/src/androidTest/**"
      ...
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain
BUILD FAILED in 6s
30 actionable tasks: 1 executed, 29 up-to-date
EXIT:1
```

Сообщение то же: `detekt-config.yml excludes match main sources: [**/test/**]` (block-форма с двойными
кавычками в раунде 1 тоже не ловилась).

### 3.6 «До правки конфига»: исходный конфиг целиком

```console
$ git show HEAD:detekt-config.yml > /tmp/vk-lint-rework/detekt-config.yml
$ grep -c "'\*\*/test/\*\*'" detekt-config.yml
15
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain
BUILD FAILED in 6s
EXIT:1
```

В сообщении падения 15 раз повторён `**/test/**` (по одному на виновный список) — сторож называет
глоб, а не просто «упал».

### 3.7 Откат конфига → снова PASSED

```console
$ cp /tmp/detekt-config.rework.yml detekt-config.yml
$ grep -c "'\*\*/test/\*\*'" detekt-config.yml
0
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain

BUILD SUCCESSFUL in 7s
30 actionable tasks: 1 executed, 29 up-to-date
EXIT:0
$ grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    src/testing/arch-tests/build/test-results/test/TEST-com.simarel.vkbot.arch.DetektExcludesPathTest.xml
tests="1" skipped="0" failures="0" errors="0"
```

Задача выполнилась (`1 executed`), а не взята из кэша: правка `detekt-config.yml` перезапускает её
благодаря `inputs.file` (§2, строка `arch-tests/build.gradle.kts`).

### 3.8 Сторож в реальном чек-ауте

```console
$ cd /Users/asmaksime1/IdeaProjects/test/vk
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*" --console=plain
BUILD SUCCESSFUL in 7s
32 actionable tasks: 2 executed, 30 up-to-date
EXIT:0
$ grep -o 'tests="[0-9]*" failures="[0-9]*"' \
    src/testing/arch-tests/build/test-results/test/TEST-com.simarel.vkbot.arch.DetektExcludesPathTest.xml
tests="1" failures="0"
```

### 3.9 Гейты в реальном чек-ауте (локальный путь содержит `/test/`)

```console
$ ./gradlew detekt ktlintCheck --rerun-tasks --console=plain
BUILD SUCCESSFUL in 5s
85 actionable tasks: 85 executed
EXIT:0
$ grep -h "## Findings" src/*/build/reports/detekt/detekt.md src/testing/*/build/reports/detekt/detekt.md | sort | uniq -c
  10 ## Findings (0)
```

`--rerun-tasks` обязателен: без него задачи могли быть `UP-TO-DATE` и прогон ничего бы не доказывал.
10 модулей с detekt-отчётами — 0 находок.

### 3.10 Гейты в нейтральной копии (AC-1, путь без сегмента `test`)

```console
$ cd /tmp/vk-lint-rework
$ ./gradlew detekt ktlintCheck --console=plain
BUILD SUCCESSFUL in 4s
83 actionable tasks: 83 executed
EXIT:0
$ grep -h "## Findings" src/*/build/reports/detekt/detekt.md src/testing/*/build/reports/detekt/detekt.md | sort | uniq -c
  10 ## Findings (0)
```

### 3.11 Локальный и нейтральный пути дают одинаковый результат (AC-2)

Чистая копия с локальным по форме путём (`/private/tmp/test/vk-lint-rework` — сегмент `test` есть):

```console
$ rsync -a --exclude build --exclude .gradle --exclude .kotlin /tmp/vk-lint-rework/ /tmp/test/vk-lint-rework/
$ cd /tmp/test/vk-lint-rework && realpath .
/private/tmp/test/vk-lint-rework
$ ./gradlew detekt ktlintCheck --rerun-tasks --console=plain
BUILD SUCCESSFUL in 12s
83 actionable tasks: 83 executed
EXIT:0
$ grep -h "## Findings" src/*/build/reports/detekt/detekt.md src/testing/*/build/reports/detekt/detekt.md | sort | uniq -c
  10 ## Findings (0)
```

Сравнение отчётов всех 10 модулей с нейтральной копией (без строки timestamp):

```console
$ diff <(grep -v "generated with" /tmp/vk-lint-rework/src/<mod>/build/reports/detekt/detekt.md) \
       <(grep -v "generated with" /tmp/test/vk-lint-rework/src/<mod>/build/reports/detekt/detekt.md)
# пусто для всех 10 модулей
IDENTICAL:10 DIFFERENT:0
```

### 3.12 Сборка и тесты (AC-4)

Нейтральная копия:

```console
$ ./gradlew build --console=plain
BUILD SUCCESSFUL in 1m 58s
152 actionable tasks: 40 executed, 112 up-to-date
EXIT:0
$ ./gradlew test --console=plain
BUILD SUCCESSFUL in 1m 20s
62 actionable tasks: 4 executed, 58 up-to-date
EXIT:0
clean copy: tests=60 failures=0
```

`build` включает `detekt`, `ktlintCheck`, `test`, `coverageVerification`, `capabilitiesCheck` и
`:src:app:quarkusBuild`. Arch-модуль (`LayerDependenciesTest` 5, `GradleDependenciesTest` 3,
`ModuleDependenciesTest` 7, `DetektExcludesPathTest` 1) — 16 тестов, 0 падений.

Реальное дерево (в нём дополнительно лежат файлы параллельной задачи `0544056b`):

```console
$ ./gradlew build --console=plain
BUILD SUCCESSFUL in 1m 25s
157 actionable tasks: 11 executed, 146 up-to-date
EXIT:0
$ # arch-tests: 18 тестов (16 + 2 из TestDataFactoriesTest), failures=0
```

Отчёты `detekt.md` реального дерева и чистой копии различаются только метриками (число
properties/functions), потому что в дереве есть чужие новые файлы; находок `0` в обоих:

```diff
# src/receiver/build/reports/detekt/detekt.md: нейтральная копия vs реальное дерево
-* 72 number of properties
+* 70 number of properties
```

Это различие scope, а не пути: сравнение «локальный путь vs нейтральный» на одинаковом дереве —
§3.11 (`IDENTICAL:10 DIFFERENT:0`).

### 3.13 Карта возможностей (AC-5)

```console
$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
CAP_EXIT:0
```

В нейтральной копии — тот же `OK` (входит и в `build` как `capabilitiesCheck`, §3.12).

## 4. Отклонения от спеки и принятые решения

1. **`src/testing/arch-tests/build.gradle.kts`: `detekt-config.yml` — вход `Test`-задач (+1 строка,
   `:43`).** Файл вне перечня пунктов 1–4 спеки, но без него пункт 3 недоказуем: конфиг не входит во
   входы `test`, и после правки конфига Gradle оставляет задачу `UP-TO-DATE`, поэтому негативный
   прогон возвращал `BUILD SUCCESSFUL` вместо `FAILED` (замер раунда 1; в этом раунде видно по
   `1 executed` в §3.3–3.7). Это не Gradle-зависимость и не изменение правил — одна строка входа,
   которая делает сторожа перезапускаемым.
2. **Анкорены все 8 широких глобов, а не только `'**/test/**'`.** Пункт 1 спеки говорит «заменить
   широкие тестовые глобы … и т.д.», а «минимально достаточно `['**/src/test/**']`» — нижняя граница
   для репозитория без Android/iOS/JS source sets. Анкоринг всех вариантов сохраняет исходный смысл
   списков и не меняет поведение там, где таких source sets нет.
3. **Сторож проверяет оба синтетических пути** — `test` и нейтральный — и непустоту извлечённых
   глобов. Спека требует строить оба пути и утверждать про test-путь; проверка нейтрального пути
   добавлена в тот же единственный `@Test`, а проверка непустоты исключает «вакуумно зелёный» тест
   при поломке парсера.
4. **Стиль документов.** ADR 0005 — русский, как соседние разделы; строка в `AGENTS.md` — английская,
   как весь файл §10 (репозиторий пишет этот файл по-английски).
5. **Имя артефакта.** Спецификация называет его `fix.md`, доска объявляет выход стадии `impl.md`;
   доказательства собраны в `impl.md` (та же схема, что в `resolve-hidden-findings`).
6. **Порядок «до/после».** Конфиг был исправлен до написания сторожа, поэтому «до правки конфига
   сторож падал» показано прогоном против исходного конфига из `HEAD` (§3.6) и временным возвратом
   глоба в один список (§3.3–3.5); откат — §3.7.
7. **Раунд 2: изменён только файл теста.** По указанию review-summary не трогались ни конфиг, ни
   документы, ни build-скрипт, ни исходники. Дополнение в ADR 0005, предложенное nit N3 (сторож
   читает проектный конфиг, а не merged default), не внесено: это правка документа, а раунд
   ограничен тестом; риск зафиксирован в §5.
8. **Парсер сторожа — regex, не YAML.** Покрыты все законные формы `excludes` в этом репозитории:
   inline-список (в том числе разнесённый по строкам внутри `[...]`) и block-форма, в одинарных и
   двойных кавычках. Не покрыты: unquoted-скаляры (в YAML недопустимы для глобов) и folded-scalar.
   YAML-парсер потребовал бы новой зависимости, что спека запрещает.
9. **Цитаты падений.** По nit N2 копирую `<failure>` целиком, вместе с JUnit-суффиксом
   `==> expected: <true> but was: <false>` (в XML он экранирован как `==&gt;`/`&lt;`/`&gt;`).

## 5. Что не сделано и что блокирует

- Ничего не заблокировано; все обязательные AC (2, 3, 1, 4, 5) реализованы и подтверждены выводом.
- **Nit N3 не закрыт и не может быть закрыт этой задачей:** detekt запущен с
  `buildUponDefaultConfig = true` (`build.gradle.kts:54`), встроенный default-конфиг detekt 1.23.8
  содержит те же 15 широких `'**/test/**'`. Сторож читает только проектный `detekt-config.yml`;
  если удалить строку `excludes` из проектного конфига, правило молча откатится на default и локальная
  слепота вернётся при зелёном стороже. Закрытие требует доступа к merged-конфигу (зависимость на
  detekt — запрещена спекой) → отдельное решение человека/задача.
- Парсер не является YAML-разбором (см. §4.8) — сознательный компромисс без новых зависимостей.
- Capability-файлы не менялись: правка не меняет наблюдаемого поведения продукта, строки гейтов в
  карте возможностей нет, `check-capabilities.sh` зелёный.
- AC-6 родительской спеки (имена `search_web`/`search_academic`/`search_discussions`) относится к
  предыдущей задаче; исходники здесь не трогались, имена сохранены.

## 6. Журнал решений (в порядке выполнения, раунд 2)

1. Прочитал `review-summary.md`: единственная блокирующая находка B1 — regex извлекает только
   одинарные кавычки; раунд ограничен тестом. Прочитал `DetektExcludesPathTest.kt` и убедился, что
   правка сводится к двум строкам (`QUOTED`, `groupValues[2]`).
2. Поменял regex на `Regex("""(['"])([^'"]+)\1""")` и индекс группы на `groupValues[2]`
   (`DetektExcludesPathTest.kt:38,54`). Комментариев, новых зависимостей, изменений конфига/доков нет.
3. Построил чистую копию `/tmp/vk-lint-rework` из `git archive HEAD` + 5 файлов задачи, сверил md5 —
   причина: в общем дереве лежат незакоммиченные файлы задачи `0544056b` (риск 1 review-summary),
   и гейты должны мерить только scope этой задачи.
4. Положительный прогон сторожа в копии: `BUILD SUCCESSFUL`, `tests=1 skipped=0 failures=0` (§3.2).
5. Негативный прогон `'**/test/**'` в `MagicNumber`: `BUILD FAILED`, сообщение называет глоб; выписал
   точную строку `<failure>` с JUnit-суффиксом (nit N2) (§3.3).
6. Негативный прогон `"**/test/**"` (B1): `BUILD FAILED` — в раунде 1 он был зелёным, дефект
   воспроизведён как исправленный (§3.4).
7. Block-форма с двойными кавычками: `BUILD FAILED`, имя глоба в сообщении (§3.5).
8. Полный исходный конфиг из `HEAD` (15 широких глобов): `BUILD FAILED`, 15 имён в сообщении (§3.6).
9. Откат → `BUILD SUCCESSFUL`, `tests=1 failures=0`, задача перезапущена (`1 executed`), не `UP-TO-DATE`
   (§3.7).
10. Локальный путь-эквивалент `/private/tmp/test/vk-lint-rework`: `detekt ktlintCheck --rerun-tasks` →
    exit 0, 83 исполненных задачи, 10×`Findings (0)`; отчёты совпали с нейтральной копией
    `IDENTICAL:10 DIFFERENT:0` (§3.11).
11. Нейтральная копия: `detekt ktlintCheck` → exit 0 (§3.10); `build` → `BUILD SUCCESSFUL in 1m 58s`,
    152 задачи; `test` → 60 тестов, 0 падений (§3.12).
12. Реальное дерево: сторож → exit 0; `detekt ktlintCheck --rerun-tasks` → exit 0, 85 задач,
    10×`Findings (0)`; `build` → exit 0, 157 задач, arch-tests 18/0 (§3.8, §3.9, §3.12).
13. `./scripts/check-capabilities.sh` → `OK`, exit 0 (§3.13).
14. Проверил инварианты: широких глобов `0`, `'**/*.kts'` — `2`, дифф конфига — только строки
    `excludes:`, md5 теста в дереве совпадает с копией (`79177f17…`) (§3.1).
15. Переписал `impl.md`: точные выводы, суффикс JUnit в цитатах, статус B1 и открытый риск N3.
