# review-summary.md — resolve-hidden-findings

- **Раунд:** 1
- **Итог:** **PASS. Блокирующих находок нет.** Все четыре ревьюера — `approve`; найденные замечания имеют
  severity `nit` (полировка документации) и не требуют раунда переработки. Конфликтов между ревьюерами
  нет, поэтому `pipeline_resolution` не потребовался.
- **Проверенное состояние:** коммит `4b551b4` («ktlint issues»), рабочее дерево чистое. Коммит содержит
  ровно 15 изменённых файлов из спеки + новый `src/testing/test-fixtures/detekt-baseline.xml` + два
  артефакта задачи (`.agile/.../spec.md`, `impl.md`). `detekt-config.yml`, `.github/**`,
  `src/testing/arch-tests/**` не тронуты (`git diff --name-only 4b551b4~1 4b551b4`).
- **Байты артефакта:** `impl.md` sha256 `d90d49060efca70f2c81040377d02b47c58fb8ef15645761186845a0648d5ff1`
  — совпадает с зарегистрированным артефактом; все вердикты привязаны к нему. Ревьюеры начали работу
  на том же содержимом до коммита и подтвердили, что содержимое `4b551b4` байт-идентично осмотренному.

## Acceptance criteria: доказательства

| AC | Результат | Доказательство |
|---|---|---|
| **AC-1** — `detekt ktlintCheck` зелёный в чек-ауте без сегмента `test` | **PASS** | `realpath /tmp/vk-lint-check` → `/private/tmp/vk-lint-check`, сегмента `test` нет; `rsync` + `diff -r` (с исключениями `build/.git/.gradle/.idea/.ast-index/.kotlin/.agile/.DS_Store`) — идентично репозиторию. `cd /tmp/vk-lint-check && ./gradlew detekt ktlintCheck --rerun-tasks` → `BUILD SUCCESSFUL in 5s`, `83 actionable tasks: 83 executed`, `AC1_EXIT:0`; во всех 10 модулях `detekt.md` — `## Findings (0)`. Негативные контроли (test-reviewer, qa-expert) в собственных нейтральных копиях: снятие `@Suppress("TooGenericExceptionCaught")` даёт `GenerateSummaryCommandImpl.kt:59:18 TooGenericExceptionCaught`; снятие baseline — `FakeVoProvider.kt:18:8 TooManyFunctions`; снятие `SpreadOperator` — `Application.kt:10:20`; снятие `"FunctionNaming"` — 3 находки в `VaneSearchToolAdapter.kt:16/22/28`. Гейт живой, слепота пути не маскирует результат. |
| **AC-4** — существующие тесты зелёные | **PASS** | `cd /tmp/vk-lint-check && ./gradlew build --rerun-tasks` → `BUILD SUCCESSFUL in 2m 9s`, `154 actionable tasks: 154 executed`, `AC4_EXIT:0`, строк `FAILED`/`FAILURE` — 0; `:capabilitiesCheck` и `:coverageVerification` входят в прогон. Агрегация `src/**/build/test-results/test/*.xml`: `tests=59 failures=0 errors=0 skipped=0` (19 suites). Ни один тестовый исходник диффом не изменён. |
| **AC-5** — карта возможностей и ADR соответствуют коду | **PASS** (проверки зелёные; см. nit N1) | `./scripts/check-capabilities.sh` → `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0 (воспроизведено lead и code-reviewer). ADR 0005: «9 файлов `detekt-baseline.xml`» и перечень совпадают с 9 существующими файлами (`ai, app, infrastructure, persistence, processor, share, vk-facade, testing/arch-tests, testing/test-fixtures`). Три обновлённых capability-диапазона проверены по текущим файлам: `TransientChatMemoryProvider.kt:11-18`, `ProcessSummaryCommandImpl.kt:37-126`, `GenerateSummaryCommandImpl.kt:27-90`, `SummarizationAiAdapter.kt:17-42`, `VaneSearchCommand.kt:31-77`. |
| **AC-6** — имена `@Tool` не меняются | **PASS** | `grep -n "fun search_" .../VaneSearchToolAdapter.kt` → `16 search_web`, `22 search_academic`, `28 search_discussions`, exit 0; делегирование в `VaneSearchCommand` сохранено; в файле изменена только строка 1 (`@file:Suppress`). |

AC-2 и AC-3 (анкоринг тестовых `excludes` + `DetektExcludesPathTest`) — по спеке §6 и board-task spec
относятся к следующей задаче `make-lint-path-independent` и в этой задаче не проверялись. Локальный
`./gradlew detekt` остаётся зелёным, но слепым до следующей задачи — это ожидаемо и не использовалось
как доказательство AC-1.

## Тесты и «театр»

- Новых тестов в задаче нет и по спеке не требуется (сторож линта — следующая задача). Дифф не меняет
  ни одного тестового файла.
- Театра не найдено: цитируемые в `impl.md` прогоны воспроизведены lead'ом (AC-1, AC-4) и двумя
  независимыми негативными контролями (test-reviewer, qa-expert), а не приняты на слово. Число тестов
  59/0 взято из XML-отчётов, а не из пересказа.
- **Честно о покрытии:** изменённый код тестами не покрыт — `:src:ai:test` это `NO-SOURCE`; ни один
  тест не проверяет `MAX_MESSAGES=10`, границы `FakeVoProvider`, `Application.main`, catch-ветки
  `EventDispatcher`/`FetchUserProfile`, деградацию `VaneSearch`. Сохранение поведения здесь
  обеспечивается формой диффа (только аннотации и литералы→константы с теми же значениями;
  `kotlin.Suppress` имеет `SOURCE`-retention и в байткод не попадает) и живым detekt-гейтом. Тесты на
  это — не-цель спеки (будущая задача про покрытие `ai`).

## Ревьюеры: что проверено и признано чистым

- **test-reviewer** — валидность оракула и «может ли проверка упасть»: нейтральность пути, идентичность
  копии, исполнение (а не up-to-date) задач, негативные контроли, агрегация тестов из XML, вакуумность
  `check-capabilities.sh`, осмысленность `grep` для AC-6, загрузка нового baseline. Находки: N1 (nit).
- **code-reviewer** — минимальность и адресность диффа: ровно 7 `@Suppress("TooGenericExceptionCaught")`
  на указанных функциях, `SpreadOperator` только на `main`, `FunctionNaming` добавлен в существующий
  файловый suppress, значения констант сохранены литерал-в-литерал (`Random.nextLong` from-inclusive /
  until-exclusive), baseline лежит в `projectDir` и подхватывается `build.gradle.kts:63-66`, новых
  комментариев/рефакторинга/мёртвого кода нет. Отдельно разобрал `src/ai/detekt-baseline.xml`: одна
  строка `LongMethod` синхронизирована, потому что аннотация входит в подпись baseline; это
  необходимое, а не «пересборочное» изменение, других записей не затронуто. Находка: N1 (nit).
- **security-auditor** — поверхность атаки: injection, пути, SSRF, десериализация, authN/authZ, утечки
  данных, крипто/секреты, transport/config, output/redirects, зависимости/сборка, resource abuse.
  Большинство классов недостижимы (нет изменённых runtime-путей/конфигов); все 7 catch-тел
  байт-идентичны, поэтому подавления не прячут новую ошибку: EventDispatcher по-прежнему уводит любую
  ошибку в retry-лестницу, summary помечается FAILED, деградация поиска и необязательность профилей
  сохранены. Находок нет (0).
- **qa-expert** — 11 сценариев: baseline точечный и несущий (снятие → ровно `TooManyFunctions`; возврат
  литералов → 3 `MagicNumber` всё ещё репортятся), старая подпись `LongMethod` открывает находку
  (значит, правка baseline необходима и не «переоткрывает» её), правила реально работают в нейтральном
  пути, эквивалентность констант, счётчики тестов, документы. Находка: N1 (nit).

## Некритичные находки (nit, переработка не требуется)

**N1. Устаревшие диапазоны `path:start-end` в документах вне трёх файлов, которые правила item 13.**
Этот же дифф сдвинул строки в `EventDispatcher.kt` (+1), `FetchUserProfileCommandImpl.kt` (+2),
`Application.kt` (+1), `FakeVoProvider.kt` (+4), а `docs/capabilities/index.md`, `event-dispatch.md`,
`message-storage.md`, ADR 0002/0004, `AGENTS.md` и architecture/testing-доки остались со старыми
координатами. Проверено:

| Место | Сейчас | Фактически |
|---|---|---|
| `docs/capabilities/index.md:31` | `ProcessSummaryCommandImpl.kt:36-125` | `37-126` (execute 37-107 + fetchMessages 109-126) |
| `docs/capabilities/index.md:33` | `EventDispatcher.kt:63-111` | `63-112` |
| `docs/capabilities/index.md:45` | `Application.kt:6-12` | `6-13` |
| `docs/capabilities/event-dispatch.md:21` | `EventDispatcher.kt:63-88` | `63-89` |
| `docs/capabilities/event-dispatch.md:22,74` | `EventDispatcher.kt:90-111` | `91-112` |
| `docs/capabilities/message-storage.md:33` | `FetchUserProfileCommandImpl.kt:26-95` | `26-97` |

Дополнительно qa-expert/code-reviewer перечислили тот же класс в `docs/adr/0002:15`, `docs/adr/0004:44`,
`AGENTS.md:43,44,169,192`, `docs/architecture/{overview,modules,patterns,data-flow}.md`,
`docs/development/{testing,configuration}.md` (34 одиночных ссылки + 2 внутри списков). `check-capabilities.sh`
срезает диапазоны (`strip_line_range`) и проверяет только существование пути, поэтому AC-5-тест зелёный;
блокирующим это не является. Требуемое действие (если человек захочет точности): либо отдельная
docs-правка перечисленных координат, либо явно зафиксировать, что синхронизация диапазонов строк вне
трёх capability-файлов — отдельная задача (следующая `make-lint-path-independent` менять текст
capability-файлов не вправе). Ни одна из находок не требует переработки кода.

## Открытые риски (не блокируют)

1. **`src/ai/detekt-baseline.xml` теперь содержит `@Suppress` в подписи `LongMethod`.** Если позже
   аннотацию на `ProcessSummaryCommandImpl.execute` передвинут/удалят, `LongMethod` (62 > 60) снова
   всплывёт, и baseline придётся регенерировать. Следующая задача `ai`-исходники не трогает, поэтому
   сейчас безопасно; стоит строки в заметках следующей задачи.
2. **Локальный detekt остаётся зелёным, но слепым** до `make-lint-path-independent` — по спеке
   ожидаемо; оракул этой задачи — только нейтральная копия.
3. **Коммит `4b551b4` включает `.agile/.../spec.md` и `impl.md`** вместе с кодовым диффом. Репозиторий
   уже отслеживает артефакты задач, поэтому это выглядит согласованно; если человек хотел кодовый
   коммит отдельно — его решение, на AC не влияет.
4. **Нет теста-сторожа на подавления/константы** (спека относит это к задаче покрытия `ai`); регрессию
   поймает только detekt-гейт (в нейтральном пути), а изменение `MAX_MESSAGES`/границ fake-id — нет.
5. **`AGENTS.md:205`** («do not add new findings to it») читается строже, чем согласованное в спеке §4
   решение добавить baseline `test-fixtures`; согласование это покрывает, формулировку можно уточнить
   позже.
6. **`src/ai/detekt-baseline.xml` формально вне пунктов 1–13 спеки.** Изменение минимально (одна
   подпись, сгенерированная detekt в нейтральной копии), не добавляет и не снимает ни одной находки,
   кроме той же самой `LongMethod`; альтернатива (расширить `@Suppress` до `LongMethod`) скрыла бы
   реальный долг, а рефакторинг метода запрещён. Считаю обоснованным.

## Команды-доказательства

```console
$ rsync -a --exclude build --exclude .git --exclude .gradle --exclude .idea --exclude .ast-index \
    --exclude .kotlin --exclude .agile --exclude .DS_Store ./ /tmp/vk-lint-check/
$ diff -r ... . /tmp/vk-lint-check/   # пусто
$ realpath /tmp/vk-lint-check
/private/tmp/vk-lint-check             # сегмента test нет

$ (cd /tmp/vk-lint-check && ./gradlew detekt ktlintCheck --rerun-tasks)
BUILD SUCCESSFUL in 5s
83 actionable tasks: 83 executed
AC1_EXIT:0

$ (cd /tmp/vk-lint-check && ./gradlew build --rerun-tasks)
BUILD SUCCESSFUL in 2m 9s
154 actionable tasks: 154 executed
AC4_EXIT:0

$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
CAPS_EXIT:0

$ grep -n "fun search_" .../VaneSearchToolAdapter.kt
16:    fun search_web(query: String): String {
22:    fun search_academic(query: String): String {
28:    fun search_discussions(query: String): String {
GREP_EXIT:0
```

Логи lead-прогонов: `/tmp/lead-ac1.log`, `/tmp/lead-build.log`. Сумма тестов из XML оракула:
`tests=59 failures=0 errors=0 skipped=0`.
