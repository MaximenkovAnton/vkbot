# review-summary.md — `docs-and-coverage-record` (round 2)

- **Board task:** `docs-and-coverage-record` (board `quick-fix`, stage `review`, round 2)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Ветка / worktree:** `fix/ai-tests` / `.worktrees/fix/ai-tests` (не переключалась)
- **Reviewer стадии:** `test-reviewer`, `code-reviewer` (агенты стадии) и `security-auditor`
  (`requiredReviewers`); все три отправили вердикты round 2.
- **Привязка вердиктов:** `implementation:impl.md` sha256
  `d846fe903335c0cd99f4e73d2188a0a12fcd09eaa87ca0d114a25bb19a59b1ef` — совпадает у всех трёх
  вердиктов и с доставленными байтами (`sha256sum`).
- **Дерево доставки:** `61fdd96b23196b0846cc695fab6bb815a22c07d207f3c2737d25c48e7c4ebe69`; все
  положительные гейты на нём — `fresh` (см. «Доказательства гейтов»).
- **Итог раунда:** **консенсус достигнут** — 3 × `approve`, новых находок нет; board task
  принимается.

## Вердикты round 2

| Reviewer | Решение | Находки | Вердикт id | Привязка |
|---|---|---|---|---|
| `test-reviewer` | **approve** | нет (B1/B2/N1/N2 закрыты, N3 carried) | `92ea4c02-6da1-4a37-b972-73635f1a1e4e` | `d846fe90…b1ef` |
| `code-reviewer` | **approve** | нет (B1/B3/N1 закрыты) | `5a971a50-406f-429c-845b-be02011506a5` | `d846fe90…b1ef` |
| `security-auditor` | **approve** | нет (carried: none; round 1 — approve) | `022902c2-7ba4-4401-a822-9da3bcc0deba` | `d846fe90…b1ef` |

Все три reviewer-а ответили на свои находки round 1 «closed/carried» и не переоткрывали уже
принятые классы; новых blocking или nit-находок в изменённых байтах нет.

## Закрытие находок round 1

| Находка | Статус | Чем подтверждено |
|---|---|---|
| **B1** (blocking, test-reviewer + code-reviewer): README называет снятый порог 0.25/0.2796 текущим | **CLOSED** | `README.md:173` — `0.40` (measured `0.5043`, ratchet target `0.60`), согласовано с `build.gradle.kts:19-20`; `grep 0.25\|0.2796` по README/AGENTS/docs даёт только исторические формулировки (`docs/development/testing.md:255`, ADR 0005:62) |
| **B2** (blocking, test-reviewer): негативные прогоны заявлены, но в ledger их нет | **CLOSED** | В ledger есть обе записи `exitCode: 1`: `5574ef9e-e318-44e6-bd83-5f19f7223975` (`check-capabilities.sh` → `FAIL: docs/capabilities/ai-answer.md references … GenerateAnswerUsecaseMissing.kt, which does not exist`) и `6d9c2398-fe85-45fb-8dba-5ec3d99861bb` (`coverageVerification` → `Rule violated for bundle vkbot: lines covered ratio is 0.50, but expected minimum is 0.99`); обе сняты на мутированном дереве `fcf788c2…` до отката (потому `stale` — ожидаемо), `impl.md` §3 цитирует именно эти id, откаты сверены `sha256` (`build.gradle.kts` `c11531f3…`, `ai-answer.md` `c8b4d393…`), `docs/capabilities/vk-webhook.md` чист |
| **B3** (blocking у lead): `ai-tools-vane.md` выдаёт регистрацию `UserAnswerAiService.tools` за проверенную тестом | **CLOSED** | `docs/capabilities/ai-tools-vane.md:73-77`: делегирование `@Tool`-методов проверено `VaneSearchToolAdapterTest`, регистрация класса тестом не покрыта — только сборкой/стартом; grep по тестам не находит ни проверки списка `tools`, ни `RegisterAiService`; `AiIntegrationTest` инжектит порты и не утверждает список инструментов |
| **N1** (nit): формула в ADR 0005 при L = 0.5043 даёт 0.50, а записан 0.40 | **CLOSED** | ADR 0005:56-60: «Формула первого порога … при L = 0.5043 она дала бы 0.50, а 0.40 — цель ратчета (см. «Ратчет»)»; решение ADR не переписано, добавлена строка ратчета (ADR:62) |
| **N2** (nit): историческая строка негативного прогона цитирует ratio 0.27 | **CLOSED** | ADR 0005:72 — `lines covered ratio is 0.50`, совпадает с фактическим прогоном и `docs/development/testing.md:256-257` |
| **N3** (open risk): temp-name JUnit XML | **CARRIED** (не дефект диффа) | `src/ai/build/test-results/test/__TES-9AQD4KUSTHLBI.…MessageRequireAnswerEventProcessorTest.xml` (2 теста, mtime `20:42:49` — тот же прогон, что fresh-запись `./gradlew test --rerun-tasks` в `20:44:58`); агрегат `src/**/build/test-results/test` = **130 / 36**, а наивный glob `TEST-*.xml` — 128 / 35; документы учитывают файл и называют 130/36 (см. «Открытые риски») |

## Что проверено по классам (чисто)

| Линза | Проверенные классы | Результат |
|---|---|---|
| `test-reviewer` | AC↔тест (все 16 AC: AC-ai-answer-1…6, AC-conversation-summary-1…7, AC-ai-tools-vane-1…3); числа JUnit XML (130/36; ai 62/14 классов; app 13/3, из них `AiIntegrationTest` 6); per-class counts в `docs/development/testing.md:33`; Jacoco root counters; «Пробелы приёмки» всех трёх файлов против тестов; ledger-записи и негативные прогоны; AC-18 (`git diff --stat -- 'src/*/src/main'` пуст) | чисто |
| `code-reviewer` | docs↔код (порог, L, покрытые строки) в README/testing.md/AGENTS §6/§8/ADR 0005; ограничение ADR «решение не переписывать»; ссылки на строки `build.gradle.kts:19-20,22-31,69-72,130-133`, `WireMockTestResource.kt:24-33`; таблица «Где лежат тесты», раскладка фикстур, «Ограничения»; отсутствие комментариев в `build.gradle.kts`; границы диффа и отсутствие временных мутаций | чисто |
| `security-auditor` | `build.gradle.kts` (только две константы; гейт усилен, не отключён; `check` → `coverageVerification`+`capabilitiesCheck`); новые байты README/ADR/capability-файлов на секреты, токены, URL/IP/email, домашние пути, исполняемые инструкции, control/bidi-символы; отсутствие изменений authn/authz, transport, crypto, логов с PII; production `src/**` не тронут — injection/SSRF/path/deserialization-поверхность не достигается | чисто |

## Доказательства гейтов (fresh; не перезапускались)

Ledger `pipeline_gate action=read`; tree `61fdd96b…`:

| criterion | command | exit | доказательство (хвост записи) |
|---|---|---|---|
| tests | `./gradlew test --rerun-tasks` | 0 | `BUILD SUCCESSFUL in 2m 13s`, 67 задач executed; JUnit XML aggregate **130 tests, 0 failures, 0 errors, 0 skipped, 36 classes** — ai 62 (14 классов), app 13 (в т.ч. `AiIntegrationTest` 6), arch-tests 18, persistence 9, receiver 9, share 8, vk-facade 7, processor 2, infrastructure 2 |
| coverage | `./gradlew coverageReport` | 0 | LINE **1057 / 2096 = 0.5043**; INSTRUCTION 0.5041; BRANCH 0.4089 |
| coverage | `./gradlew coverageVerification` | 0 | `coverageMin = 0.40`, `measuredLineCoverage = 0.5043` → порог проходит |
| capabilities | `./scripts/check-capabilities.sh` | 0 | `OK: capability map paths, test paths, statuses and ADR ids resolve` |
| build | `./gradlew build` | 0 | `:capabilitiesCheck`, `:coverageVerification`, `:src:app:quarkusBuild`; `BUILD SUCCESSFUL` |
| tests | `./gradlew test` | 0 | литеральная команда spec.md; `UP-TO-DATE` после форсированного прогона (исполнение доказывает запись `--rerun-tasks`) |

Негативное доказательство (записи `exitCode: 1`, по построению `stale` на откаченном дереве
`fcf788c2…`; повторно не запускались — это две записи на проверку, а не третий прогон):

| criterion | command | exit | доказательство |
|---|---|---|---|
| capabilities | `./scripts/check-capabilities.sh` | 1 | `FAIL: docs/capabilities/ai-answer.md references …/GenerateAnswerUsecaseMissing.kt, which does not exist` |
| coverage | `./gradlew coverageVerification` | 1 | `> Task :coverageVerification FAILED` … `Rule violated for bundle vkbot: lines covered ratio is 0.50, but expected minimum is 0.99` |

## Независимые проверки (read-only, без запуска Gradle)

- **JUnit XML:** 13 файлов `TEST-*.xml` + `__TES-…MessageRequireAnswerEventProcessorTest.xml` в
  `src/ai/build/test-results/test` = 14 классов / 62 теста; `app` = 3 класса / 13 тестов
  (`InitTest` 1, `VkWebhookIntegrationTest` 6, `AiIntegrationTest` 6); суммарно 130 / 36.
  Все per-class counts строки `ai` в `docs/development/testing.md:33` совпадают.
- **Jacoco XML:** `build/reports/jacoco/coverageReport/coverageReport.xml` — LINE covered 1057,
  missed 1039, total 2096, ratio 0.5043; INSTRUCTION 0.5041, BRANCH 0.4089.
- **Карта возможностей:** все 16 AC-блоков содержат путь `src/.../src/(test|testFixtures)/...`,
  каждый путь существует; `scripts/check-capabilities.sh` — свежая запись OK.
- **«Пробелы приёмки» против дерева:** ни один тест не проверяет регистрацию `UserAnswerAiService.tools`
  и не ссылается на `SummaryReadyEventProcessor`/`handleSaveCompletedSummary`;
  `Message.forwardedContextString()` (`src/share/.../Message.kt:40`) не имеет вызовов; пустой
  `SUMMARY_ENABLED_CHATS` недостижим по ADR 0006; парсинг списка с пробелами проверен
  (`ProcessSummaryCommandImplTest` — `execute recognises enabled chats listed with surrounding spaces`);
  `VaneSearchToolAdapterTest` действительно покрывает три `@Tool`-метода (3 parameterized кейса).
- **AC-18:** `git diff --stat -- 'src/*/src/main'` пуст; временных `*.bak`/`*.orig` нет;
  `docs/capabilities/vk-webhook.md` чист (временная правка откачена).
- **AC-19:** порог `0.40` не выше измеренного `L = 0.5043`; `measuredLineCoverage` обновлён;
  README/testing.md/AGENTS/ADR согласованы с `build.gradle.kts:19-20`.

## Открытые риски (не блокируют приёмку)

1. **Temp-name JUnit XML (N3).** `__TES-9AQD4KUSTHLBI.…MessageRequireAnswerEventProcessorTest.xml`
   — имя, которое Gradle даёт результату при определённых прогонах; наивный `TEST-*.xml` даёт
   128/35, документированные числа (130/36) корректны, потому что учитывают этот файл. Это свойство
   артефакта сборки, а не дефект диффа; при следующей правке docs стоит отметить его в testing.md.
2. **Docker для `@QuarkusTest`.** `AiIntegrationTest` (6 тестов) и `VkWebhookIntegrationTest`
   поднимают DevServices; `./gradlew build` в окружении без Docker упадёт. Это осознанная цена
   AC-16/порога 0.40, а не дефект.
3. **Агрегат покрытия** считается по 8 модулям с исключением `src/testing/*`; 0.5043 — именно этот
   агрегат, не «покрытие всего репозитория».

## Итог

- **AC-17 подтверждён:** в трёх capability-файлах 16 AC с id и реальными путями тестов
  (`ai-answer.md:68-93`, `conversation-summary.md:81-112`, `ai-tools-vane.md:57-70`),
  `docs/development/testing.md`, `AGENTS.md` и `README.md` обновлены, `check-capabilities.sh` — OK.
- **AC-18 подтверждён:** `./gradlew build` свежий, exit 0; `src/*/src/main` не изменён; временных
  мутаций в итоговом дереве нет.
- **AC-19 подтверждён:** L = 1057/2096 = 0.5043 ≥ 0.40; `coverageMin = 0.40`,
  `measuredLineCoverage = 0.5043`; числа записаны в `testing.md`, `AGENTS.md`, `README.md`,
  ADR 0005; `coverageVerification` зелёный, негативный прогон с 0.99 — FAILED (exit 1).
- Блокирующих находок round 2 нет; board task завершается, дальнейшая приёмка — MITM-стадией.
