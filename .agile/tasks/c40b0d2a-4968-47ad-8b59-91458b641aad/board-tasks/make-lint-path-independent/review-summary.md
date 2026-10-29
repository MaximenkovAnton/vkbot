# review-summary.md — make-lint-path-independent

- **Раунд:** 2
- **Итог:** **APPROVE — 0 blocking.** Все четыре ревьюера поставили `approve` в раунде 2:
  test-reviewer (0 находок), code-reviewer (0 находок), security-auditor (2 нита),
  docker-expert (1 нит). Обязательные AC (2, 3, 1, 4, 5) выполнены и подтверждены
  воспроизведёнными прогонами. Переработка не требуется; задача готова к MITM.
- **Проверенное состояние:** рабочее дерево без коммита; ровно 5 файлов задачи —
  `detekt-config.yml`, `src/testing/arch-tests/build.gradle.kts`,
  `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/DetektExcludesPathTest.kt`,
  `docs/adr/0005-quality-gates-and-ci.md`, `AGENTS.md`. Раунд 2 изменил **только** файл
  сторожа (2 строки: `QUOTED` и `groupValues[2]`); остальные четыре файла байт-идентичны
  раунду 1 (md5 сверены всеми ревьюерами). Исходники, `.github/**`, capability-файлы,
  правила и пороги detekt не тронуты.
- **Байты артефакта:** `impl.md` sha256 `7e4c7d446de63e55f9064319d083c3f6ad1cfe87a94de682869a64c63adde4a4`
  — совпадает с зарегистрированным артефактом; все четыре вердикта раунда 2 привязаны к нему.
- **Метод:** каждый вывод ниже воспроизведён lead'ом и/или ревьюером в чистой копии
  (`git archive HEAD` + 5 файлов задачи; путь без сегмента `test`), потому что общее рабочее
  дерево заражено незакоммиченными файлами параллельной задачи `0544056b`. См. риск 1.

## Что было блокирующим в раунде 1 и как закрыто

**B1 (test-reviewer, blocking в раунде 1): сторож не видел глобы в двойных кавычках.**
Раунд 1: `QUOTED = Regex("""'([^']+)'""")` — валидный YAML `"**/test/**"` detekt применял,
а сторож оставался зелёным (локальная слепота возвращалась). Раунд 2: `QUOTED = Regex("""(['"])([^'"]+)\1""")`
(`DetektExcludesPathTest.kt:54`) и `groupValues[2]` (`:38`).

Воспроизведено (lead и все четыре ревьюера независимо, каждая копия — delivered config):

| Негативный сценарий | Результат |
|---|---|
| delivered config | PASSED, `tests="1" skipped="0" failures="0" errors="0"`, exit 0 |
| inline `"**/test/**"` в `MagicNumber` (форма B1, двойные кавычки) | **FAILED**, exit 1, `detekt-config.yml excludes match main sources: [**/test/**]` |
| block-форма `- "**/test/**"` (двойные кавычки) | **FAILED**, то же сообщение с именем глоба |
| inline `'**/test/**'` (одинарные кавычки, регресс старого пути) | **FAILED**, то же сообщение |
| многострочный flow-list `excludes: [\n "**/test/**", … ]` | **FAILED** (test-reviewer) |
| `git show HEAD:detekt-config.yml` (15 широких глобов) | **FAILED**, `**/test/**` назван 15 раз |
| откат к delivered config | PASSED, `tests="1" failures="0"`; задача перезапущена (`1 executed`), не `UP-TO-DATE` |
| вакуум: все `excludes` заменены на `[]` | **FAILED** `detekt-config.yml has no excludes globs` (`:22`) — зелёным «молча» не станет |

Парсер-оракул: 122 глоба из 17 `excludes`-записей — без пропусков и без лишних (сверено
lead'ом и code-reviewer независимой реализацией того же regex+PathMatcher).

## Acceptance criteria: доказательства

| AC | Результат | Доказательство |
|---|---|---|
| **AC-1** — `detekt ktlintCheck` в чек-ауте без сегмента `test` → exit 0 без находок | **PASS** | Чистая копия `/tmp/lint-r2-lead` (`realpath /private/tmp/lint-r2-lead`): `./gradlew detekt ktlintCheck` → `BUILD SUCCESSFUL`, exit 0, `83 actionable tasks: 83 executed`, 10 модулей `## Findings (0)`. Повторено test-reviewer и docker-expert (`83 executed`, 10×0). |
| **AC-2** — локальный detekt применяет тот же набор правил, что CI | **PASS** | (1) Конфиг: 15 `excludes`-списков анкорены на `**/src/test/**`, широких глобов 0, `**/*.kts` сохранён ×2; `git diff` — только строки `excludes:` (обратная замена анкоров даёт файл, байт-идентичный `HEAD` — code-reviewer). (2) Сторож (см. выше) доказывает, что ни один глоб не матчит синтетические пути `/tmp/test/vk/...` и `/tmp/work/vk/...`. (3) **Реальный detekt-прогон:** probe `val local = 4242` в `src/main` — под `HEAD`-конфигом на `/test/`-пути `:src:app:detekt` → `BUILD SUCCESSFUL` (правило `MagicNumber` скрыто), а под delivered-конфигом тот же путь → `BUILD FAILED`, `Probe.kt:5:21 … [MagicNumber]`, идентично нейтральному пути; test-source probe остаётся исключённым. (4) Локальный путь `/test/` и нейтральная копия: 10/10 отчётов совпадают (test-reviewer, docker-expert, lead — на идентичном дереве). |
| **AC-3** — сторож падает на широком глобе и называет его | **PASS** | Таблица негативных прогонов выше (6 сценариев + откат); сообщение содержит имя виновного глоба и подсказку `**/src/test/**`. B1 закрыт. |
| **AC-4** — существующие тесты зелёные | **PASS** | Чистая копия `/tmp/lint-r2-build` (полный прогон с нуля): `./gradlew build` → **exit 0**, `BUILD SUCCESSFUL in 4m 8s`, `152 actionable tasks: 152 executed`, 10×`Findings (0)`, arch-tests 16/16 (`1+3+5+7`, 0 failures), `capabilitiesCheck`/`coverageVerification` прошли. test-reviewer независимо: `build` 4m32s exit 0, 60 тестов, 0 падений; docker-expert: `build` 2m20s exit 0. Реальное дерево: `./gradlew detekt ktlintCheck --rerun-tasks` → exit 0, 85 executed, 10×0 (lead). |
| **AC-5** — карта возможностей и ADR соответствуют коду | **PASS** | `./scripts/check-capabilities.sh` → `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0 (lead, test-reviewer, docker-expert). ADR 0005 — раздел «Независимость гейта линта от пути чек-аута»; `AGENTS.md:205-207` — правило про source-set-анкоринг и имя сторожа. Capability-файлы не менялись (наблюдаемое поведение продукта не меняется). |

AC-6 родительской спеки (имена `search_web`/`search_academic`/`search_discussions`) — вне этой
задачи; исходники не тронуты, имена на месте (подтверждено в раунде 1).

## Ревьюеры: что проверено и признано чистым

- **test-reviewer — `approve`, 0 находок.** Классы: отображение AC→тест, фальсифицируемость
  (6 негативных прогонов + откат), вакуумность (пустой список глобов → FAILED), театр (нет
  mock/sleep/network/ordering; реальный `detekt-config.yml` и реальный `java.nio` glob-PathMatcher;
  один `@Test`, два осмысленных assert), полнота парсера (122/122), `inputs.file`/`UP-TO-DATE`,
  включённость сторожа в CI (`./gradlew test`), честность evidence-строк `impl.md` (цитаты падений
  теперь с JUnit-суффиксом — раундовый нит N2 закрыт), scope диффа.
- **code-reviewer — `approve`, 0 находок.** Классы: минимальность диффа (0 не-`excludes` строк;
  обратная замена анкоров → байт-идентично `HEAD`), корректность round-2 regex и индекса группы,
  отсутствие комментариев/TODO/println/строк >120, один `@Test`, отсутствие записи на диск и новых
  зависимостей, дизайн сторожа (детерминированные синтетические пути, имя виновного глоба),
  соответствие ADR/AGENTS коду (детект читает `rootProject.files("detekt-config.yml")`, сторож —
  тот же файл), `config.excludes: ''` не даёт глобов.
- **security-auditor — `approve`, 2 нита.** Классы: целостность гейта (16 rule-level `excludes`,
  15 анкоренных списков, широких 0; default-конфиг detekt не протекает — все 15 rule-key
  переопределены), main анализируется, тесты исключены (probe на `/test/`-пути), ReDoS (500 КБ–1 МБ
  adversarial input: 4–48 мс, катастрофического бэктрекинга нет), path/files (один фиксированный
  файл, 0 записей/временных файлов/exec/network), injection, зависимости/CI (+1 строка input, без
  версий и новых зависимостей, `.github/**` не тронут), секреты/PII в docs. Недостижимы: SSRF,
  authN/authZ, десериализация, крипто, transport, output/redirects.
- **docker-expert (required reviewer) — `approve`, 1 нит (N3).** Классы: CI-контракт (сторож идёт в
  `test` и `coverage` джобах через `./gradlew test`; `.github/**` байт-идентичен), build-input
  (`inputs.file` — тот же файл, что читает плагин; UP-TO-DATE при повторе, перезапуск при изменении
  контента, configuration cache чистый), CI-путь без `test`-сегмента: `HEAD` и delivered конфиг дают
  одинаковые находки в 10/10 модулях (ре-анкоринг меняет только локальный путь), якорные глобы
  по-прежнему исключают тесты на CI, config validation honoured, deployable не затронут
  (`:src:app:quarkusBuild` не содержит arch-tests, `detekt-config.yml` в артефакт не попадает;
  Dockerfile/`.dockerignore` не тронуты), rollout/rollback — только revert.

## Ниты (не блокируют, переработка не требуется)

- **N1 (security-auditor, nit).** Сторож fail-open на валидной, но неидиоматичной YAML-форме
  `excludes: # comment` + block-список и на YAML-anchor формах: detekt такой глоб применяет, а
  сторож видит 0 глобов и остаётся зелёным. Не блокирует: форма не вырабатывается форматтерами,
  конвенция репозитория запрещает комменты у отдельных свойств, ни одна текущая запись её не
  использует. Возможное усиление (по желанию): fail-closed «каждая rule-level запись даёт ≥1 глоб».
- **N2 (security-auditor, nit).** Формулировка ADR 0005:40-42 «где бы ни лежал чек-аут» сильнее
  реальности: `**/src/test/**` всё ещё матчит чек-аут, лежащий под каталогом с именем `src/test`
  (например `~/src/test/vk`) — слепота вернётся при зелёном стороже. Не блокирует: свойство самого
  detekt (матчинг по абсолютному пути), вне заявленных путей AC-2/AC-3; лечение — только смягчить
  формулировку ADR, кодом не закрывается.
- **N3 (docker-expert, nit, перенесён из раунда 1).** Сторож читает только проектный
  `detekt-config.yml`, а detekt запущен с `buildUponDefaultConfig = true` (`build.gradle.kts:54`):
  удаление проектного `excludes` вернёт широкий `**/test/**` из bundled default 1.23.8 при зелёном
  стороже. Сегодня delivered-конфиг переопределяет ровно те же 15 rule-key (ключи сверены), гэпа
  нет; риск будущий, закрытие требует зависимости на detekt (спека запрещает) — решение человека.

Ниты раунда 1, закрытые в раунде 2: N2 test-reviewer (цитаты `<failure>` без JUnit-суффикса) —
теперь цитаты полные (`impl.md:141,161`); nit code-reviewer/security-auditor про двойные кавычки —
это и был B1, исправлен.

## Открытые риски

1. **Общее рабочее дерево заражено параллельной задачей `0544056b`.** Её несохранённые файлы лежат
   в том же дереве; все доказательства собраны в чистых копиях. На момент ревью глобальные гейты в
   реальном дереве были зелёными (`detekt ktlintCheck --rerun-tasks` → exit 0, 85 executed, 10×0),
   но состояние дерева меняется параллельной сессией — при слиянии итоговый `build` должен быть
   зелёным уже за счёт `0544056b`.
2. **N3** — сторож проверяет проектный конфиг, а не merged default (см. выше): риск будущий,
   решение за человеком.
3. **Размещение сторожа:** он выполняется в CI-джобах `test`/`coverage` (через `./gradlew test`), но
   не в джобе `lint`; регрессия всё равно красит CI, однако сам lint-job остаётся «слепым» — так
   задумано (изменение `.github/**` вне объёма).
4. **Парсер сторожа — regex, не YAML** (осознанный компромисс без новых зависимостей): покрыты
   inline/block/multiline-flow формы в одинарных и двойных кавычках; folded-scalar и anchor-формы
   не покрыты (N1).
5. **Сторож синтетический:** он проверяет два синтетических пути; N2 показывает, что остаточные
   path-зависимые случаи возможны и сторожем не покрываются.

## Команды-доказательства (прогоны lead, чистая копия `/tmp/lint-r2-lead` / `/tmp/lint-r2-build`)

```console
$ md5 -q <5 файлов задачи>            # совпадают с impl.md §3: 5f7a79…, abb271…, f570f2…, 07e04c…, 79177f…
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*"
BUILD SUCCESSFUL; XML tests="1" skipped="0" failures="0" errors="0"; EXIT:0

$ # "**/test/**" (двойные кавычки) первым элементом MagicNumber-исключений
$ ./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*"
DetektExcludesPathTest > … FAILED
org.opentest4j.AssertionFailedError: detekt-config.yml excludes match main sources: [**/test/**]. … 
==> expected: <true> but was: <false>
BUILD FAILED; EXIT:1

$ # '**/test/**' (одинарные) -> FAILED; block-форма "- \"**/test/**\"" -> FAILED (то же сообщение)
$ # HEAD-конфиг (15 широких) -> FAILED, "**/test/**" 15 раз; откат -> PASSED, tests="1" failures="0"

$ # AC-2: probe `val local = 4242` в src/app/src/main на пути /private/tmp/test/probe/vk
$ #   HEAD-конфиг   -> BUILD SUCCESSFUL (MagicNumber скрыт)        EXIT:0
$ #   delivered     -> BUILD FAILED, Probe.kt:5:21 [MagicNumber]    EXIT:1
$ #   neutral path  -> та же находка                                 EXIT:1

$ ./gradlew detekt ktlintCheck          # чистая копия, путь без "test"
BUILD SUCCESSFUL; 83 actionable tasks: 83 executed; 10 модулей "## Findings (0)"; EXIT:0

$ cd <real checkout> && ./gradlew detekt ktlintCheck --rerun-tasks
BUILD SUCCESSFUL in 11s; 85 actionable tasks: 85 executed; 10×"## Findings (0)"; EXIT:0

$ ./gradlew build                        # чистая копия, с нуля
BUILD SUCCESSFUL in 4m 8s; 152 actionable tasks: 152 executed; EXIT:0
arch-tests: 16 тестов, 0 падений (в т.ч. guard tests="1" failures="0"); 10×"## Findings (0)"

$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve; EXIT:0

parser oracle: 17 excludes-записей / 122 глоба / 0 широких / 2 "**/*.kts"
```

## Решение

Все обязательные AC выполнены, блокирующих находок нет, ниты — полировка и не оправдывают
раунд переработки. **APPROVE**, задача готова к MITM. Рекомендация человеку: оставить N1/N2/N3
как отдельные решения (N3 — новая задача/строка в ADR), не возвращать задачу на implementation.
