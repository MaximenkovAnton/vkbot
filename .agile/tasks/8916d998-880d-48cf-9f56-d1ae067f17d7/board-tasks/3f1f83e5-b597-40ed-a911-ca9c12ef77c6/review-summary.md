# Ревью подготовки проекта vk — стадия review (раунд 2)

**Board task:** `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`, задача «Подготовка проекта: vk» (`8916d998-880d-48cf-9f56-d1ae067f17d7`).
**Стадия:** review (автоматическое ревью), раунд 2. **Дата:** 2026-10-29.
**Проект:** `/Users/asmaksime1/IdeaProjects/test/vk`, HEAD `0f0eae2`, рабочее дерево — 147 незакоммиченных путей.
**Что ревьюилось (текущие байты):** закрытие блокирующей B1 и nit'ов N1–N5 раунда 1 (rework стадии implement),
документные правки, сделанные после вердиктов раунда 1 (testing/configuration/AGENTS/README ADR 0001, `check-capabilities.sh`),
плюс повторная проверка DoD доски по всему дереву: гейты, ledger `prepared-report.md` (verify, раунд 2),
`docs/capabilities/`, ADR, docs/architecture и docs/development.
**Границы стадии:** ни один файл продукта/документов не менялся. Все негативные проверки откачены побайтно;
`git status --short` до и после — 147 путей. Правки ревьюеров отсутствуют; временные файлы — только в `/tmp/vk-review2/`.

## 0. Вердикты раунда 2

| Reviewer | Вердикт | Находки |
|---|---|---|
| `test-reviewer` | **approve** | 3 nit (n1–n3) |
| `code-reviewer` | **approve** | 2 nit (n4–n5) |
| `security-auditor` | **approve** | 8 nit/открытых рисков (n6–n9 + S1), blocking нет |

**Итог стадии: approve.** Блокирующих находок нет; все три роли сошлись на approve, поэтому rework не требуется.
Блокирующая B1 раунда 1 **закрыта** (проверено тремя ревьюерами и лидом на текущих байтах). Nit'ы — не блокируют,
передаются в следующую docs/test-работу. Открытые риски для MITM перечислены в §4.

## 1. Закрытие B1 и nit'ов N1–N5 раунда 1

**B1 (блокирующая, code-reviewer раунда 1): мёртвые REST-заглушки маскировали регрессию ADR 0004.**

- `src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt` — 38 строк (было 169): `start()` возвращает
  только `quarkus.rest-client.vk.url` и `quarkus.langchain4j.ollama.base-url` (`:24-31`);
  `setupVkFacadeStubs`/`setupPersistenceStubs`, их вызовы и `quarkus.rest-client.vk-facade.url`/`persistence.url` удалены;
  `resetToDefaultStubs()` = `server?.resetAll()` (`:19-21`); единственный вызов — `VkWebhookIntegrationTest.kt:39`.
- `@RegisterRestClient` в production-коде ровно два: `vk` (`VkClient.kt:12`) и `vane` (`VaneSearchClient.kt:11`);
  клиентов `persistence`/`vk-facade` нет. Заглушек `/profiles/*`/`/persistence/*` в `src/app/src/test` и `src/testing` нет.
- Свежий прогон e2e на текущих байтах: `VkWebhookIntegrationTest` — 6 тестов, 0 падений (XML 11:45); в `./gradlew test --rerun-tasks`
  — 59/0/0/0. При возврате HTTP-контура между модулями запрос уйдёт в несуществующий клиент/URL, и e2e покраснеет.
- `docs/development/testing.md:126-140` теперь прямо говорит: заглушек удалённых loopback-эндпоинтов нет, поэтому e2e не пройдёт
  при возврате HTTP-контура (ADR 0004); формулировки «безвредный остаток» в дереве нет.

**Nit'ы раунда 1 закрыты** (проверено code-reviewer'ом и лидом): N1 — `ProcessSummaryCommandImpl.kt` вернул DEBUG на горячих ветках
(`:38`, `:46-49`, `:68`, `:73-77`), INFO только на финальных записях (`:81-84`, `:93-96`), WARNING/ERROR на своих местах;
N2 — `AGENTS.md:73` и `patterns.md:17`: L1–L5 в `LayerDependenciesTest`, L6 — в `ModuleDependenciesTest.kt:47-58`;
N3 — `docs/README.md:32` называет оставшиеся мёртвые ключи долгом; N4 — `AGENTS.md:24` и ADR 0001:24 цитируют
`src/app/build.gradle.kts:45-56` (WireMock `:52`, Awaitility `:55-56`); N5 — `scripts/check-capabilities.sh:102` требует
`src/.../src/(test|testFixtures)/...` (негатив воспроизведён, §2.8).

## 2. DoD доски — проверки лида на текущих байтах

| # | Критерий «Готово, когда» | Вердикт | Доказательство (мои прогоны) |
|---|---|---|---|
| 1 | Сборка проходит | **pass** | `./gradlew build --console=plain` → `BUILD SUCCESSFUL in 1m 12s`, `154 actionable tasks: 8 executed`, exit 0. Clean build из ledger verify: `BUILD SUCCESSFUL in 2m 8s`, 168 задач, exit 0. |
| 2 | Тесты одной командой, отчёт покрытия, порог валит сборку | **pass** | `./gradlew test --rerun-tasks --console=plain` → `BUILD SUCCESSFUL in 1m 55s`, `64 actionable tasks: 64 executed`, exit 0; по 19 XML: **tests=59, failures=0, errors=0, skipped=0** (app 7, infrastructure 2, persistence 9, processor 2, receiver 9, share 8, arch-tests 15, vk-facade 7; ai 0 — названный пробел). `coverageReport.xml`: LINE **586/1510=0.2796**, INSTRUCTION 3076/10753=0.2861, BRANCH 61/406=0.1502; `coverageMin = 0.25`. Негатив: `coverageMin 0.25→0.99` → `BUILD FAILED in 1m 11s`, **exit 1**; откат (sha256 `68de3b8…` = исходный) → exit 0. `./gradlew build --dry-run` содержит `:coverageReport/:coverageVerification/:capabilitiesCheck`, тесты, detekt, ktlint, `:src:app:quarkusBuild`. |
| 3 | Линтер валит сборку | **pass** | Негатив detekt: временный файл со строкой 140 символов → `> Task :src:share:detekt FAILED`, `...:3:1: Line detected, which is longer … [MaxLineLength]`, `BUILD FAILED`, exit 1; файл удалён → exit 0. `./gradlew ktlintCheck detekt` на текущем дереве → `BUILD SUCCESSFUL`, exit 0 (`maxIssues: 0`). |
| 4 | CI запускает build + lint + test + coverage | **pass** | `.github/workflows/build-and-test.yml`: триггеры `push`/`pull_request` в `main`; 5 джобов: `build` (`:src:app:quarkusBuild` + `test -f …quarkus-run.jar` + `test ! -e build/quarkus-app`), `test` (`./gradlew test`), `lint` (`detekt ktlintCheck`), `coverage` (`test coverageReport coverageVerification` + upload), `docs` (`check-capabilities.sh`); у всех `contents: read`. |
| 5 | AGENTS.md: выполненные команды и карта модулей | **pass** | `settings.gradle.kts` — **11** `include`; `AGENTS.md` §3 — 11 строк модулей с точками входа и зависимостями; §6 — таблица выполненных команд с результатами, включая неподдерживаемый `jacocoTestReport` с причиной; §7 — dev-запуск. Артефакт `src/app/build/quarkus-app/quarkus-run.jar` существует; `build/quarkus-app` отсутствует (единственный деплой, ADR 0002). |
| 6 | docs/architecture и docs/development описывают код со ссылками | **pass** | Мой скрипт по 265 backtick-ссылкам `path:N[-M]` в `AGENTS.md`, `README.md`, `docs/**`: все пути резолвятся, диапазонов за концом файла — 0. «Несуществующие» токены — ложные срабатывания парсера (модульные id `src:share`, перечни `path:41,47,55`, GitHub-action `setup-gradle@v4`, `gradle/libs.versions.toml`, названный в ADR 0001 как *не вводимый*). |
| 7 | docs/capabilities: строка на каждую возможность; чек падает на мёртвый путь/ADR id | **pass** | `index.md` + 9 файлов возможностей, все `delivered`, сирот нет; «Не отображено» объясняет OCR, health/info, OTel, декораторы, мёртвый канал, `@QuarkusMain`, тестовые модули. 17 AC в 7 файлах, все тест-пути под `src/**/src/(test\|testFixtures)/`. `./scripts/check-capabilities.sh` → `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0. Негативы: несуществующий тест-путь → `FAIL: …TestGone.kt… does not exist`, exit 1; `ADR 0004→ADR 0099` → `FAIL: … ADR 0099 … does not exist`, exit 1; production-путь в AC → `FAIL: …:81: acceptance criterion without a test path`, exit 1. Все три откачены (sha256 `c19fb76…`, `ca01dcd…`). |
| 8 | Противоречащий ADR получил преемника, история сохранена | **pass (N/A)** | До прохода `docs/adr/` не существовало; созданы ADR 0001–0006, все `accepted`, `Supersedes: —`, ни один не удалён; `docs/README.md` индексирует все 6. Противоречащих accepted ADR с историей нет. Удалён `docs/architecture/ai-tools.md` — не ADR, содержимое перенесено в `docs/capabilities/ai-tools-vane.md`. |

## 3. Находки раунда 2 (все — nit, не блокируют)

| id | Находка | Evidence | Автор |
|---|---|---|---|
| n1 | `docs/capabilities/ai-answer.md` («Пробелы приёмки») утверждает, что e2e проверяет «сборку контекста, форматирование промпта, публикацию `ANSWER_MESSAGE` и текст отправленного ответа». E2e проверяет только вызов `/method/messages.send` с `peer_id`/`2000000001`; текст ответа не проверяется, `/api/chat`-заглушка игнорирует вход | `VkWebhookIntegrationTest.kt:171-177`, заглушка `:55-63`; файл capability — раздел «Пробелы приёмки» | test-reviewer |
| n2 | `docs/development/testing.md:155`: «instruction 0.2862, branch 0.1527»; по `coverageReport.xml` — INSTRUCTION 3076/10753=**0.2861**, BRANCH 61/406=**0.1502** (LINE 0.2796 совпадает) | `build/reports/jacoco/coverageReport/coverageReport.xml` | test-reviewer |
| n3 | `MessageTextTest.kt:24` называется `of throws ValidationException when value is null`, но assert'ит отсутствие исключения и `""`; `MessageText.of` = `value ?: ""`, `assertThrows` в файле нет; имя скопировано из `ConversationMessageIdTest.kt:24` | `src/share/src/test/.../MessageTextTest.kt:24-32` | test-reviewer |
| n4 | `docs/development/testing.md:119` ссылается на весь класс `VkWebhookIntegrationTest.kt:27-177`, а класс занимает `:27-308` (full-flow кончается `:177`, далее ещё 5 `@Test`) | `testing.md:119`, `VkWebhookIntegrationTest.kt:27-308` | code-reviewer |
| n5 | `README.md:98-99`: «`./gradlew build` also checks that no second runner is produced». В Gradle такой проверки нет; она есть только в CI-джобе `build` (`.github/workflows/build-and-test.yml:27`), что верно названо в ADR 0002:30 и `deployment.md:122` (verify G8) | `README.md:98-99`, `build.gradle.kts` (grep `quarkus-app` — 0) | code-reviewer, test-reviewer |
| n6 | Единственный деплой везёт `quarkus-info` + `quarkus-smallrye-health`, в артефакте зарегистрированы анонимные `/q/info` и `/q/health*`, management-порт не выделен: build/JDK/OS-метаданные доступны без аутентификации (CWE-200). Health уже описан как ограничение; `/q/info` в документах не назван | `src/app/build.gradle.kts:27,36`; `generated-bytecode.jar` → `/q/info`, `/q/health*` | security-auditor |
| n7 | CI использует изменяемые теги `actions/*@v4` вместо SHA и не задаёт `timeout-minutes` (CWE-1357); секретов, `pull_request_target`, `curl\|sh` и `--scan` в workflow нет, права у всех джобов `contents: read` — риск ограничен | `.github/workflows/build-and-test.yml` | security-auditor |
| n8 | Pre-existing security-nits (не ухудшены этим diff): секрет VK сравнивается `==` и полное тело отклонённого вебхука пишется в ERROR (CWE-208/532; противоречит `AGENTS.md` §9); `ExceptionHandler` подставляет `${exception.message}` в JSON без экранирования (CWE-209/116); нет `quarkus.http.limits.*`/rate limit на `/vk/callback`; OTel/`langchain4j` экспортируют prompt/completion переписки | `VkMediatorRouterSecurityDecorator.kt:22,25`; `ExceptionHandler.kt:20-23,31-38`; `application.yml:35-38,45-46,59-64` | security-auditor |
| n9 | `scripts/check-capabilities.sh` — dev-only, без `eval`/`exec`/`curl`, `set -euo pipefail`, `bash -n` OK; неquoted `for file in $checked_files` — устойчивость, не инъекция | `scripts/check-capabilities.sh` | security-auditor |

## 4. Открытые риски для человека (MITM; не блокируют подход к среде)

- **S1. Анонимные REST-эндпоинты в деплое (pre-existing, в раунде 2 не изменился).** `@Path("/persistence")` —
  чтение переписки и `fullSummary`, запись `POST /summaries/pending` → `/{id}/complete` → `SUMMARY_READY` →
  `messages.send` токеном сообщества (`PersistenceDataController.kt:22,28,57,109,119`; `PersistenceDataUsecase.kt:132-140` →
  `SummaryReadyEventProcessor.kt:45-48` → `VkSendMessageOutputAdapter.kt:19-24`); `@Path("/api")` — анонимный прокси к VK API
  (`VkFacadeController.kt:13-14`). Аутентификации нет, артефакт слушает `0.0.0.0:8080`. OWASP A01:2021, API1/API5:2023, CWE-306.
  Diff не добавил входов и не снял проверок; ADR 0004:58 осознанно оставляет публичный REST, карта кодифицирует его как публичный
  journey (`docs/capabilities/index.md:20`, `message-storage.md:54`). Решение продукта: удалить контроллеры, либо закрыть auth +
  задеплоить периметр (TLS/reverse proxy, наружу только `/vk/callback`) + регрессионный тест «без креденшелов → 401/403» и обновить
  ADR/карту/доки.
- **`/q/health*` отдаёт 503 DOWN** (7 incoming-каналов без downstream); описано как известное ограничение; на гейты не влияет.
- **`/q/info`** анонимно отдаёт метаданные сборки (n6); выключить или вынести на management-порт.
- **CI ни разу не исполнялся на GitHub** (нет раннера); локальные аналоги зелёные; `test`-джобу нужен Docker (на `ubuntu-latest` есть).
  Actions на изменяемых `@v4`-тегах, без `timeout-minutes` (n7).
- **Docker-образы** (JVM/native/legacy) не собирались; `Dockerfile.legacy-jar` COPY-пути соответствуют только legacy-jar packaging
  (наблюдение security-auditor, вне его ленза; на безопасность не влияет).
- **Экспорт содержимого чатов:** OTel `include-prompt/completion` и `langchain4j.log-requests/responses` отправляют текст переписки
  в коллектор (pre-existing); рекомендация — `include-prompt/completion=false` в prod, OTLP только внутри периметра.
- **Продуктовые пробелы (честно названы картой):** в модуле `ai` 0 тестов; у `conversation-summary` и `ai-tools-vane` 0 AC; REST `/persistence/*`,
  сохранение в БД и публикация `SUMMARY_READY` без тестов — следующая тестовая работа (test-authoring).
- **e2e-флейк:** `VkWebhookIntegrationTest` — Awaitility poll 5 s / atMost 120 s + Docker/DevServices; на холодном раннере возможен таймаут.
- **Регрессионная защита B1 не автоматизирована:** ничто не мешает снова добавить заглушки `/profiles/*`/`/persistence/*` или rest-client-ключи;
  сейчас дерево чистое (open risk code-reviewer).
- **`failOnNoDiscoveredTests = false`** (`build.gradle.kts:69-72`): модуль, у которого тесты перестали обнаруживаться, останется зелёным;
  ловится только счётом 59 тестов. Названо в `testing.md`.
- **Ledger-константа `measuredLineCoverage = 0.2796`** совпадает с текущим измерением; на гейт не влияет (гейт сравнивает `coverageMin`).

## 5. Что проверено и найдено чистым (по лензам)

- **Test-ленс:** все 17 AC в 7 файлах возможностей находят существующие тесты под `src/**/src/(test|testFixtures)/`, каждый диапазон
  внутри файла; тесты проверяют SUT через fake-коллабораторы и могут упасть (проверены `MessageSendUsecaseTest`, `VkSendMessageOutputAdapterTest`,
  `PersistenceDataInProcessAdapterTest`, `VkProfileInProcessAdapterTest`, `MessageReceivedUsecaseInputTest`, `ReceiveMessageUsecaseTest`,
  `VkMediatorRouterIntegrationTest`, `ExceptionHandlerTest`, `PublishEventCommandImplTest`, `SendVkMessageCommandImplTest`, `MessageTextTest`).
  Mocking-фреймворков нет, `Thread.sleep` в тестах нет, снапшотов нет, тавтологичных assert нет; `InitTest` — реальный startup-smoke
  (`@QuarkusTest`); единственная асинхронная проверка — Awaitility в e2e, она падает при разрыве цепочки. ArchUnit-правила не вакуумные:
  15 тестов `5+7+3`, `GradleDependenciesTest` находит 12 build-файлов и проверяет allowlist (негатив — в ledger verify). Ledger честен:
  counts/coverage воспроизведены, негативные пары в `/tmp/vk-verify3` реальны, восстановленные файлы сверены sha256.
- **Code-ленс:** мёртвой REST-обвязки, неиспользуемых rest-client-ключей и stub-функций не осталось; уровни логирования соответствуют HEAD;
  документы переписаны из кода (ADR 0001–0006 целы, `Supersedes: —`, ни один не удалён); ссылки в изменённых секциях документов резолвятся;
  рабочее дерево 147 путей, временных файлов нет.
- **Security-ленс:** инъекции (jOOQ параметризован; новых SQL/shell/path/template/log-инъекций нет; capability-скрипт dev-only, без `eval`/`exec`);
  пути/upload/архивы/симлинки отсутствуют; SSRF (только фиксированный VK URL и env `VANE_URL`, у Vane появились connect/read таймауты);
  десериализация (Java serialization нет, Jackson default typing выключен, чтение по конкретным типам); auth/authz без изменений,
  webhook-секрет работает (403-тесты); секретов/ключей в дереве нет, `.env` git-ignored, удалённый `application-local.yml` содержал только
  placeholder'ы; нет Swagger/CORS/CSRF/дефолтных кредов; новых сторонних зависимостей нет (`jackson-module-kotlin:latest` удалён);
  non-root Docker-пользователи сохранены; in-process проводка убрала внутреннюю loopback-REST-поверхность (положительная дельта).

## 6. Воспроизведение и целостность

```bash
./gradlew build --console=plain                                  # BUILD SUCCESSFUL in 1m 12s, 154 tasks, exit 0
./gradlew test --rerun-tasks --console=plain                     # BUILD SUCCESSFUL in 1m 55s, 59/0/0/0, exit 0
./gradlew build --dry-run | grep -E 'coverage|capabilitiesCheck|detekt|ktlintCheck|quarkusBuild'
# coverage-негатив: coverageMin 0.25→0.99 → BUILD FAILED exit 1 → откат sha256 68de3b8 → exit 0
# detekt-негатив: строка 140 символов в :src:share → [MaxLineLength], BUILD FAILED exit 1 → удаление → exit 0
./scripts/check-capabilities.sh                                  # OK, exit 0
# негативы карты: несуществующий тест-путь / ADR 0099 / production-путь в AC → exit 1; все откачены
./gradlew ktlintCheck detekt --console=plain                     # BUILD SUCCESSFUL, exit 0
git status --short | wc -l                                       # 147 (до и после)
```

Логи и резервные копии: `/tmp/vk-review2/` (`lead-test.log`, `build.gradle.kts.bak`, `event-dispatch.bak`, `ms.bak`);
негативные прогоны verify — `/tmp/vk-verify3/`. Все временные правки откачены побайтно: `build.gradle.kts` sha256 `68de3b8…`,
`docs/capabilities/event-dispatch.md` `ca01dcd…`, `docs/capabilities/message-storage.md` `c19fb76…` — совпадают с исходными.
Вердикты ревьюеров привязаны к текущим байтам артефактов (выборочно сверено: `AGENTS.md` `9827680…`, `docs/development/testing.md`
`9bf7b6…`, `docs/README.md` `47bb22…`, `docs/development/configuration.md` `94704931…`); текст этого summary написан после вердиктов
и не меняет ни один из артефактов, на которые они смотрели.
