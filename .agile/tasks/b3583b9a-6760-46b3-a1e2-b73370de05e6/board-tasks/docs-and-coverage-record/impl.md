# impl.md — `docs-and-coverage-record` (round 2)

- **Board task:** `docs-and-coverage-record`
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Ветка / worktree:** `fix/ai-tests` / `.worktrees/fix/ai-tests` — не переключалась
- **Стадия:** `implementation`, агент `specialist`
- **Доставлено:** AC-17, AC-18, AC-19; production-код и тесты этой стадией не менялись
- **Дерево доставки:** `61fdd96b23196b0846cc695fab6bb815a22c07d207f3c2737d25c48e7c4ebe69`
  (хеш движка = sha256(HEAD + `git status --porcelain`); записанные на нём гейты — `fresh`)

## Контракт этой board task (spec.md §5 / dod.md)

- **AC-17** — КОГДА работа сдана, СИСТЕМА ДОЛЖНА содержать в `docs/capabilities/ai-answer.md`,
  `conversation-summary.md`, `ai-tools-vane.md` AC с id и существующими путями тестов, обновлённые
  `docs/development/testing.md` и `AGENTS.md`, и `./scripts/check-capabilities.sh` должен завершиться
  `OK: ... resolve` (exit 0).
- **AC-18** — КОГДА изменения завершены, `./gradlew build` ДОЛЖЕН пройти (тесты, detekt, ktlint, покрытие,
  `capabilitiesCheck`), и в дереве не должно остаться временных мутаций production-кода.
- **AC-19** — КОГДА измеренный агрегированный L ≥ 0.40, `coverageMin` ДОЛЖЕН быть поднят до 0.40,
  `measuredLineCoverage` и числа в `docs/development/testing.md`, `AGENTS.md` и ADR 0005 обновлены, а
  `./gradlew coverageVerification` проходит с новым порогом; иначе `coverageMin` остаётся 0.25, измеренный L
  записан.

Измерено на доставленном дереве: **L = 1057 / 2096 = 0.5043**, поэтому AC-19 идёт по первому пути:
`coverageMin = 0.40`, `measuredLineCoverage = 0.5043`.

## Закрытие находок review round 1

| Находка | Что сделано | Чем подтверждено |
|---|---|---|
| **B1** (blocking, test-reviewer + code-reviewer): `README.md:173` называет снятый порог 0.25/0.2796 текущим | `README.md:173` → порог `0.40`, measured `0.5043`, следующий целевой порог `0.60`; строка `./gradlew test` в README теперь отмечает, что прогон включает юнит-тесты `ai` | `grep -rn "0\.25\|0\.2796" README.md AGENTS.md docs/ build.gradle.kts` находит только историческую формулировку («порог поднят с 0.25», «было 0.2796 при пороге 0.25»), ни одного «текущего» значения |
| **B2** (blocking, test-reviewer): негативные прогоны заявлены, но в ledger их нет | Оба негативных прогона выполнены заново и записаны `pipeline_gate` с `exitCode: 1` **на мутированном дереве до отката**; после отката записи помечены `stale` (дерево мутации `fcf788c2…`), зелёные — `fresh` на `61fdd96b…`. `impl.md` больше не ссылается на несуществующие записи | ledger `docs-and-coverage-record`: `5574ef9e…` (capabilities, exit 1), `6d9c2398…` (coverage, exit 1); положительные `be1eacc9…`, `7847e6c0…`, `fac34a53…`, `19db928d…` — `fresh`; выходы и sha256 откатов — §3 |
| **B3** (blocking у lead, nit у code-reviewer): `ai-tools-vane.md` выдаёт регистрацию `UserAnswerAiService.tools` за проверенную тестом | «Пробелы приёмки» переписаны: делегирование `@Tool`-методов проверено `VaneSearchToolAdapterTest`, регистрация класса тестом не покрыта — её проверяет только сборка/старт приложения | `docs/capabilities/ai-tools-vane.md:71-77` |
| **N1** (nit): формула в ADR 0005 при L = 0.5043 даёт 0.50, а записан 0.40 | В §Покрытие уточнено: формула относится к выбору первого порога, при L = 0.5043 она дала бы 0.50, а 0.40 — цель ратчета; решение ADR не переписано | `docs/adr/0005-quality-gates-and-ci.md:55-62` |
| **N2** (nit): историческая строка негативного прогона цитирует ratio 0.27 | Строка обновлена на фактический вывод текущего прогона: `lines covered ratio is 0.50` | `docs/adr/0005-quality-gates-and-ci.md:72` |
| **N3** (open risk): временное имя JUnit XML | Не дефект диффа; в этом прогоне `src/ai/build/test-results/test` — 14 файлов / 62 теста, агрегат по `src/**` — 36 классов / 130 тестов | §2.1 |

## 1. Что изменено и зачем (накопительно по board task)

| Файл | Изменение | Зачем |
|---|---|---|
| `docs/capabilities/ai-answer.md` | AC-ai-answer-2…6 с путями юнит-тестов `src/ai/src/test/**`; AC-ai-answer-1 (e2e) сохранён; «Пробелы приёмки» переписаны: контекст/промпт/публикация/разбор события покрыты, неиспользуемый `Message.forwardedContextString()` остаётся пробелом | AC-17: продуктовый рекорд описывает существующие тесты |
| `docs/capabilities/conversation-summary.md` | AC-conversation-summary-1…7 с путями тестов; «Пробелы приёмки» переписаны (остались `SummaryReadyEventProcessor`/`handleSaveCompletedSummary` и недостижимый пустой `SUMMARY_ENABLED_CHATS`) | AC-17 |
| `docs/capabilities/ai-tools-vane.md` | AC-ai-tools-vane-1…3 с путями тестов; «Пробелы приёмки» переписаны по B3 | AC-17 |
| `docs/development/testing.md` | команды для `ai` и `AiIntegrationTest`; распределение 130 тестов (ai 62, app 13, в т.ч. `AiIntegrationTest` 6); таблица «Где лежат тесты» (строка `ai`; `app` + `AiIntegrationTest`); раскладка `src/ai/src/testFixtures`; интеграционный раздел; «Покрытие» (L, порог, ратчет, негативная проверка); «Ограничения» без «юнит-тестов `ai` нет» | AC-17, AC-19 |
| `AGENTS.md` | §6: `./gradlew test` → 130 тестов; `coverageVerification` → `coverageMin = 0.40` (L 0.5043). §8: порог 0.40 сегодня, ратчет 0.60 — отдельная задача; `ai` добавлен в перечни модульных `testFixtures` | AC-17, AC-19 |
| `docs/adr/0005-quality-gates-and-ci.md` | §Покрытие: `coverageMin = 0.40`, `measuredLineCoverage = 0.5043`, 1057 строк, уточнение формулы (N1), строка ратчета; §Ратчет: 0.40 выполнен, 0.60 отдельно; негативный прогон: ratio 0.50 (N2). Решение ADR не переписано | AC-19 |
| `build.gradle.kts` | `measuredLineCoverage = 0.5043`, `coverageMin = 0.40`; комментариев нет | AC-19: порог поднят только вместе с измеренным L |
| `README.md` | §Testing: порог `0.40` (measured `0.5043`, ратчет `0.60`); команда `./gradlew test` упоминает юнит-тесты `ai` | B1: документ не должен называть снятый порог текущим |

`src/**` (production и тесты), `src/ai/build.gradle.kts`, `src/app/build.gradle.kts` этой стадией не
трогались; их `M`/`??` в `git status` — артефакты завершённых зависимых board tasks.

## 2. Команды и вывод (цитаты, exit code)

Все команды — из корня worktree, на доставленном дереве `61fdd96b…`; записи — в gate ledger этой board task.

### 2.1 Тесты

```
$ ./gradlew test --rerun-tasks          # exit 0
> Task :src:ai:test
> Task :src:app:test
> Task :src:receiver:test
> Task :src:testing:arch-tests:test
> Task :src:vk-facade:test
BUILD SUCCESSFUL in 2m 13s
67 actionable tasks: 67 executed
```

Агрегат JUnit XML (`src/**/build/test-results/test`):

```
TOTAL 130 tests, 0 failures, 0 errors, 0 skipped, 36 classes
ai 62 (14 классов), app 13 (3 класса, в т.ч. AiIntegrationTest 6), arch-tests 18, persistence 9,
receiver 9, share 8, vk-facade 7, processor 2, infrastructure 2
```

```
$ ./gradlew test                        # exit 0 (литеральная команда spec.md; прогон — предыдущей записью)
BUILD SUCCESSFUL in 1s
67 actionable tasks: 67 up-to-date
```

### 2.2 Покрытие

```
$ ./gradlew coverageReport              # exit 0
BUILD SUCCESSFUL in 1m 20s
63 actionable tasks: 4 executed, 59 up-to-date
```

`build/reports/jacoco/coverageReport/coverageReport.xml` (корневые счётчики):

```
LINE         covered 1057, missed 1039, total 2096, ratio 0.5043
INSTRUCTION  covered 5421, missed 5332, total 10753, ratio 0.5041
BRANCH       covered 166,  missed 240,  total 406,  ratio 0.4089
```

```
$ ./gradlew coverageVerification        # exit 0
BUILD SUCCESSFUL in 4s
64 actionable tasks: 1 executed, 63 up-to-date
```

### 2.3 Карта возможностей

```
$ ./scripts/check-capabilities.sh       # exit 0
OK: capability map paths, test paths, statuses and ADR ids resolve
```

### 2.4 Сборка

```
$ ./gradlew build                       # exit 0
> Task :capabilitiesCheck
> Task :coverageVerification
> Task :src:app:quarkusBuild
BUILD SUCCESSFUL in 2s
163 actionable tasks: 1 executed, 162 up-to-date
```

### 2.5 Production-дерево не тронуто

```
$ git diff --stat -- 'src/*/src/main'   # exit 0
(пусто)
```

## 3. Негативные проверки (две записи, каждая с `exitCode: 1`)

Обе записи сняты **до отката**, на мутированном дереве `fcf788c2…`; после отката дерево вернулось к
`61fdd96b…`, поэтому негативные записи в ledger `stale` (это ожидаемо), а положительные — `fresh`.
Причина двух хешей: `treeHash` движка учитывает только HEAD и список путей `git status --porcelain`, а
мутация файла, уже помеченного `M`, списка не меняет — поэтому на время мутации дерево дополнительно
отличалось одной временной правкой чистого файла (пустая строка в `docs/capabilities/vk-webhook.md`),
которая откачена байт-в-байт.

### 3.1 `check-capabilities.sh` на сломанном пути (AC-17)

Временно в `docs/capabilities/ai-answer.md` путь AC-ai-answer-2 заменён на `GenerateAnswerUsecaseMissing.kt`:

```
$ ./scripts/check-capabilities.sh       # exit 1
FAIL: docs/capabilities/ai-answer.md references src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseMissing.kt, which does not exist
```

Откат; `sha256sum docs/capabilities/ai-answer.md` = `c8b4d393a8f19b5bda6ddd9386d06525743c7a6b0dc0b36d5c79e24ea5852ef7`
(совпадает с состоянием до мутации). Повторный прогон на доставленном дереве — `OK: ... resolve`, exit 0 (§2.3).
Ledger: `5574ef9e-e318-44e6-bd83-5f19f7223975` (exit 1, stale) и `be1eacc9-…` (exit 0, fresh).

### 3.2 `coverageVerification` на завышенном пороге (AC-19)

Временно `coverageMin = 0.99` в `build.gradle.kts`:

```
$ ./gradlew coverageVerification        # exit 1
> Task :coverageVerification FAILED
   > Rule violated for bundle vkbot: lines covered ratio is 0.50, but expected minimum is 0.99
BUILD FAILED in 1s
Exit code 1.
```

Откат (`coverageMin = 0.40`); `sha256sum build.gradle.kts` =
`c11531f34250cd59014f42bcf0a02bf745963767409a1b49390c322dd5db27d3` (совпадает с состоянием до мутации).
Повторный прогон — `BUILD SUCCESSFUL`, exit 0 (§2.2). Ledger: `6d9c2398-…` (exit 1, stale) и
`7847e6c0-…` (exit 0, fresh).

### 3.3 Временных правок в итоговом дереве нет

```
$ git status --short
 M AGENTS.md
 M README.md
 M build.gradle.kts
 M docs/adr/0005-quality-gates-and-ci.md
 M docs/capabilities/ai-answer.md
 M docs/capabilities/ai-tools-vane.md
 M docs/capabilities/conversation-summary.md
 M docs/development/testing.md
 M src/ai/build.gradle.kts
 M src/app/build.gradle.kts
 M src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt
?? .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/
?? src/ai/src/test/
?? src/ai/src/testFixtures/
?? src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt
?? src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeEventProvider.kt
```

`git status --porcelain docs/capabilities/vk-webhook.md` пуст (временная правка откачена), `*.bak` в
`docs/` нет. `src/ai/build.gradle.kts`, `src/app/build.gradle.kts`, `WireMockTestResource.kt`, новые
тесты/фикстуры — артефакты зависимых board tasks, не этой стадии.

## 4. Отклонения от spec.md и решения, оставленные на меня

1. **`README.md` вне списка §4/§8 spec.md.** Правка добавлена по блокирующей находке review B1: §2
   spec.md разрешает править документы, а прежний текст называл снятый порог текущим. Изменены две строки:
   порог и упоминание юнит-тестов `ai` в команде `./gradlew test`.
2. **ADR 0005: формула (N1) и ratio негативного прогона (N2).** Изменены только фактические числа и
   уточнение, какой порог из чего следует; решение ADR (гейты, ратчет, «порог поднимается только вместе
   с L») не переписано.
3. **Порог 0.40, а не 0.50.** AC-19 и ADR 0005 называют цель 0.40; формула `floor(L*20)/20` при
   L = 0.5043 дала бы 0.50, но порог выше цели ратчета не ставился.
4. **`./gradlew test --rerun-tasks` для доказательства исполнения.** Литеральный `./gradlew test` после
   форсированного прогона — `UP-TO-DATE`; он записан отдельной записью (exit 0), а исполнение доказывает
   `--rerun-tasks` (130 тестов, 67 задач executed).
5. **Временная правка чистого файла ради различимого `treeHash` негативной проверки** (§3). Это
   механическое требование движка (хеш по списку путей, а не по содержимому), в итоговом дереве правки нет.
6. **Тестовые числа — из дерева и прогонов.** 62 в `ai` (14 классов), 13 в `app`, 18 arch-tests, 130 всего;
   имена классов и фабрик сверены с деревом, ни одно не придумано.

## 5. Что не сделано и что блокирует

- Ничего из AC-17…AC-19 не осталось незакрытым; блокеров нет (Docker для `@QuarkusTest` доступен).
- Вне объёма (spec.md §2): удаление/использование `Message.forwardedContextString()` — отдельная кодовая
  задача (остаётся пробелом в `ai-answer`); OCR-путь и `TransientChatMemoryProvider` не трогались;
  ратчет 0.60 — отдельная задача (ADR 0005).
- `SummaryReadyEventProcessor` (`vk-facade`) и `handleSaveCompletedSummary` (`persistence`) остаются без
  теста — код соседних возможностей, назван в «Пробелах приёмки» `conversation-summary`; не блокер.

## 6. Decision log (по порядку)

1. Прочитаны review-summary round 1, gate ledger, spec.md, dod.md, три capability-файла,
   `docs/development/testing.md`, `AGENTS.md` §6/§8, ADR 0005, `scripts/check-capabilities.sh`.
2. Проверены факты: `build.gradle.kts:19-20` (`0.5043`/`0.40`), `README.md:173` (устаревший порог),
   `ai-tools-vane.md:71-77` (ложное утверждение о регистрации).
3. **B1:** `README.md` — порог `0.40`, measured `0.5043`, ратчет `0.60`; в команду `./gradlew test`
   добавлено упоминание юнит-тестов `ai`.
4. **B3:** «Пробелы приёмки» `ai-tools-vane.md` переписаны: делегирование проверено
   `VaneSearchToolAdapterTest`, регистрация класса — нет (только сборка/старт).
5. **N1/N2:** ADR 0005 — уточнение формулы (0.40 = цель ратчета, формула дала бы 0.50) и фактический
   ratio негативного прогона 0.50; решение ADR не переписано.
6. **B2:** `check-capabilities.sh` на сломанном пути — `FAIL ... does not exist`, exit 1; запись
   `pipeline_gate` `criterion=capabilities`, `exitCode=1`, затем откат, sha256 сверен (`c8b4d393…`).
7. **B2:** `coverageMin = 0.99` — `Rule violated ... 0.50 ... 0.99`, exit 1; запись `pipeline_gate`
   `criterion=coverage`, `exitCode=1`, затем откат, sha256 сверен (`c11531f3…`).
8. На доставленном дереве заново выполнены и записаны: `./gradlew test --rerun-tasks` (130 тестов, 0
   падений), `./gradlew test` (exit 0, up-to-date), `./gradlew coverageReport` (L = 0.5043),
   `./gradlew coverageVerification` (exit 0), `./scripts/check-capabilities.sh` (OK), `./gradlew build`
   (exit 0). Все записи `fresh` на `61fdd96b…`.
9. `git diff --stat -- 'src/*/src/main'` пуст; в итоговом дереве нет временных мутаций и `*.bak`.
