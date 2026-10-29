# Подготовка проекта vk — отчёт стадии verify (раунд 2, после rework)

**Board task:** `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`, задача «Подготовка проекта: vk» (`8916d998-880d-48cf-9f56-d1ae067f17d7`).
**Стадия:** verify (testing), агент `test-writer`. **Дата прогонов:** 2026-10-29.
**Проект:** `/Users/asmaksime1/IdeaProjects/test/vk`, HEAD `0f0eae2`, рабочее дерево — **147** незакоммиченных путей.
**Окружение:** macOS 26 (Darwin arm64), Temurin **21.0.5**, Gradle **9.3.0** (wrapper), Docker **28.3.2**.
**Почему раунд 2:** ревью раунда 1 вернуло `implement` с блокирующей **B1** (мёртвые REST-заглушки
`/profiles/*` и `/persistence/*` в `WireMockTestResource` маскировали регрессию ADR 0004) и nits N1–N5.
Rework закрыл B1 и nits; этот проход независимо перепроверяет все пять критериев на текущем дереве,
включая повторную негативную проверку каждого гейта.
**Границы стадии:** тесты не добавлялись, application-код, сборка, конфиги и документы не менялись.
Артефакт стадии — только этот файл. Все временные нарушения §2 откачены: `git status --short` до и после
совпадает (147 путей, `diff` пуст), контрольные суммы восстановленных файлов — `sha256sum -c` → `OK` (§8).

## 0. Вердикт

| id | Критерий | Вердикт | Доказательство |
|---|---|---|---|
| C1 | Гейты из чистого состояния: build, test, lint, format-check, coverage | **pass** | §1 |
| C2 | Каждый гейт падает на временном нарушении и снова проходит после отката | **pass** | §2 |
| C3 | Документы против дерева: команды AGENTS.md, карта модулей, точки входа/тест-пути capabilities, ключи configuration.md, UNACTUAL audit.md | **pass** | §3 |
| C4 | Каждое устаревшее утверждение audit.md переписано либо оставлено с причиной | **pass** | §4 |
| C5 | Зависимости от машины, сети и версий названы | **pass** | §5 |

**B1 закрыт.** `WireMockTestResource` теперь 38 строк, задаёт только существующие REST-клиенты
(`quarkus.rest-client.vk.url`, `quarkus.langchain4j.ollama.base-url`); заглушек `/profiles/*` и
`/persistence/*` нет, в коде остались ровно два `@RegisterRestClient` (`vk`, `vane`). Сквозной e2e
(`full flow`) зелёный: 59 тестов, 0 падений (§3.7).
**Nits N1–N5 закрыты** (§3.8). Новых блокирующих дефектов нет. Новый nit — `README.md:98` (§3.9),
вне критериев подготовки, владелец — стадия docs; внесён в §6 как G8.

## 1. C1 — гейты из чистого состояния

Полный прогон с нуля: `./gradlew clean build --console=plain` →
```
BUILD SUCCESSFUL in 2m 8s
168 actionable tasks: 168 executed
```
**exit 0.** В clean build входят `test`, `detekt`, `ktlintCheck`, `coverageVerification`, `capabilitiesCheck`,
`:src:app:quarkusBuild`. Ниже — те же гейты отдельными командами на том же дереве.

| # | Команда | Exit | Что произошло (цитата) |
|---|---|---|---|
| 1.1 | `./gradlew clean build --console=plain` | 0 | `BUILD SUCCESSFUL in 2m 8s`; `168 actionable tasks: 168 executed` |
| 1.2 | `./gradlew test --console=plain` | 0 | `BUILD SUCCESSFUL in 1m 8s`; XML: 19 файлов, `tests=59 failures=0 errors=0 skipped=0` |
| 1.3 | `./gradlew detekt --console=plain` | 0 | `BUILD SUCCESSFUL in 771ms` |
| 1.4 | `./gradlew ktlintCheck --console=plain` | 0 | `BUILD SUCCESSFUL in 651ms` |
| 1.5 | `./gradlew coverageReport coverageVerification --console=plain` | 0 | `BUILD SUCCESSFUL in 2s`; отчёт `build/reports/jacoco/coverageReport/{html,coverageReport.xml}` создан |
| 1.6 | `./gradlew :src:testing:arch-tests:test --rerun-tasks --console=plain` | 0 | `BUILD SUCCESSFUL in 13s`; 15 тестов: `LayerDependenciesTest` 5, `ModuleDependenciesTest` 7, `GradleDependenciesTest` 3 |
| 1.7 | `./gradlew capabilitiesCheck --console=plain` | 0 | `BUILD SUCCESSFUL in 1s` |
| 1.8 | `./scripts/check-capabilities.sh` | 0 | `OK: capability map paths, test paths, statuses and ADR ids resolve` |
| 1.9 | `./gradlew :src:app:quarkusBuild --console=plain` | 0 | `BUILD SUCCESSFUL in 4s`; `src/app/build/quarkus-app/quarkus-run.jar` существует, корневого `build/quarkus-app` нет |
| 1.10 | `ruby -e "require 'yaml'; ..."` по `.github/workflows/build-and-test.yml` | 0 | `yaml ok`; job'ы `build,test,lint,coverage,docs`; триггеры `push,pull_request` |
| 1.11 | после всех негативных проверок: `./gradlew build --console=plain` | 0 | `BUILD SUCCESSFUL in 1m 29s`; `154 actionable tasks: 12 executed, 142 up-to-date` |

Тесты по модулям (из `src/**/build/test-results/test/*.xml`): app 7, infrastructure 2, persistence 9,
processor 2, receiver 9, share 8, testing/arch-tests 15, vk-facade 7; **ai — 0** (собственных тестов нет —
известный продуктовый пробел, §6 H5). Сумма 59.

Покрытие (`coverageReport.xml`, агрегат по 8 бизнес-модулям, `src/testing/*` исключены):
```
LINE         covered=586  missed=1510  ratio=0.2796   (порог coverageMin=0.25, build.gradle.kts:20)
INSTRUCTION  covered=3076 missed=7677  ratio=0.2862
BRANCH       covered=61   missed=345   ratio=0.1502
```
Артефакт деплоя: `test -f src/app/build/quarkus-app/quarkus-run.jar` → OK; `test ! -e build/quarkus-app` → OK
(единственный деплой — `:src:app`, ADR 0002).

## 2. C2 — доказательство, что каждый гейт падает

Схема на текущем дереве: рабочее состояние → временное нарушение → гейт падает (цитата, exit) → откат →
гейт снова зелёный. Это те же классы нарушений, что в implement-стадии (`tooling.md` §3.3–3.9),
воспроизведённые заново.

| # | Гейт | Временное нарушение | Падение (цитата, exit) | После отката (цитата, exit) |
|---|---|---|---|---|
| 2.1 | detekt | `src/share/.../GateProofDetekt.kt`, строка 145 символов | `> Task :src:share:detekt FAILED`; `...GateProofDetekt.kt:3:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]`; `BUILD FAILED in 889ms`; **exit 1** | `BUILD SUCCESSFUL in 648ms`; **exit 0** |
| 2.2 | ktlintCheck | `src/processor/.../GateProofKtlint.kt`, `val gateProofKtlintValue=1` | `> Task :src:processor:ktlintMainSourceSetCheck FAILED`; `...GateProofKtlint.kt:3:25 Missing spacing around "="`; `BUILD FAILED in 1s`; **exit 1** | `--rerun-tasks` → `BUILD SUCCESSFUL in 1s`; **exit 0** |
| 2.3 | ArchUnit: модули | класс с пакетом `com.simarel.vkbot.receiver` внутри модуля `persistence` зависит от `JooqMessageRepository` | `ModuleDependenciesTest > receiver module should only depend on share() FAILED`; `BUILD FAILED in 9s`; **exit 1** | `BUILD SUCCESSFUL in 9s`; **exit 0** |
| 2.4 | ArchUnit: слои L3 | класс в `ai/usecase` зависит от `ConversationContextRestAdapter` (adapter.output) | `LayerDependenciesTest > L3 use cases and commands depend on no adapter, database or transport type() FAILED`; `BUILD FAILED in 9s`; **exit 1** | `BUILD SUCCESSFUL in 9s`; **exit 0** |
| 2.5 | Граф сборки | ребро `implementation(project(":src:persistence"))` в `src/ai/build.gradle.kts` | `GradleDependenciesTest > production project dependencies follow the allowlist() FAILED`; `BUILD FAILED in 7s`; **exit 1** | ребро убрано, sha256 `59c5b17…` → `BUILD SUCCESSFUL in 7s`; **exit 0** |
| 2.6 | Покрытие | `coverageMin` 0.25 → 0.99 | `> Task :coverageVerification FAILED`; `Rule violated for bundle vkbot: lines covered ratio is 0.27, but expected minimum is 0.99`; `BUILD FAILED in 1s`; **exit 1** | `coverageMin=0.25`, sha256 `68de3b8…` → `BUILD SUCCESSFUL in 1s`; **exit 0** |
| 2.7 | Карта: путь | `JooqMessageRepository.kt:19-66` → `JooqMessageRepositoryGone.kt:19-66` в `docs/capabilities/message-storage.md` | `FAIL: docs/capabilities/message-storage.md references ...JooqMessageRepositoryGone.kt:19-66, which does not exist`; скрипт **exit 1**; `> Task :capabilitiesCheck FAILED`, `BUILD FAILED in 1s`; **exit 1** | sha256 `c19fb76…`; `OK: ...`; **exit 0** |
| 2.8 | Карта: ADR | `ADR 0004` → `ADR 0099` в `docs/capabilities/event-dispatch.md` | `FAIL: docs/capabilities/event-dispatch.md references ADR 0099, but docs/adr/0099-*.md does not exist`; **exit 1**; `> Task :capabilitiesCheck FAILED`; **exit 1** | sha256 `ca01dcd…`; `OK: ...`; **exit 0** |
| 2.9 | Карта: AC без теста (фикс nit N5) | в `docs/capabilities/vk-profiles.md:65` тест-путь AC-vk-profiles-1 заменён на production-путь `VkFacadeController.kt:14-32` | `FAIL: docs/capabilities/vk-profiles.md:65: acceptance criterion without a test path`; скрипт **exit 1**; `> Task :capabilitiesCheck FAILED`; **exit 1** | sha256 `…: OK`; `OK: ...`; **exit 0** |
| 2.10 | CI YAML | испорченная копия workflow (`jobs: broken: [unclosed`) | `Psych::SyntaxError ... mapping values are not allowed in this context at line 9 column 13`; **exit 1** | реальный файл: `yaml ok`, **exit 0** |

Документированные (не поддерживаемые) формы — утверждения AGENTS.md §6 проверены как падающие ровно так:

| # | Документированное утверждение | Проверка (цитата, exit) |
|---|---|---|
| 2.11 | `./gradlew jacocoTestReport` не поддерживается | `> Task :src:app:jacocoTestReport FAILED`; `Reason: Task ':src:app:jacocoTestReport' uses this output of task ':src:app:jandex' without declaring an explicit or implicit dependency.`; **exit 1** — совпадает с AGENTS.md §6 |
| 2.12 | корневая форма `test --tests` падает; модульная работает | `./gradlew test --tests "*MessageReceivedUsecaseInputTest*"` → `> Task :src:app:test FAILED`; `No tests found for given includes: [*MessageReceivedUsecaseInputTest*](--tests filter)`; **exit 1**. `./gradlew :src:processor:test --tests ...` → `BUILD SUCCESSFUL in 6s`; **exit 0** |

## 3. C3 — документы против дерева

### 3.1 Команды из AGENTS.md §6/§7

| Команда | Проверка | Вердикт |
|---|---|---|
| `./gradlew build` | §1.1 (clean) exit 0 + §1.11 (финальный) exit 0 | pass |
| `./gradlew clean build` | §1.1: `BUILD SUCCESSFUL in 2m 8s`, 168 задач, exit 0 | pass |
| `./gradlew test` | §1.2: 59/0/0/0, exit 0 | pass |
| `./gradlew :src:<module>:test --tests "*ClassName*"` | §2.12: модульная форма exit 0; корневая падает | pass |
| `./gradlew detekt` | §1.3 exit 0; негатив §2.1 | pass |
| `./gradlew ktlintCheck` | §1.4 exit 0; негатив §2.2 | pass |
| `./gradlew ktlintFormat` | `--dry-run` exit 0, перечисляет `:src:ktlintFormat` и модульные задачи | pass |
| `./gradlew coverageReport` | §1.5 exit 0, отчёт создан | pass |
| `./gradlew coverageVerification` | §1.5 exit 0; негатив §2.6 | pass |
| `./gradlew :src:testing:arch-tests:test` | §1.6 exit 0, 15 тестов; негативы §2.3–2.5 | pass |
| `./gradlew :src:app:quarkusBuild` | §1.9 exit 0, артефакт на месте | pass |
| `./gradlew :src:app:quarkusDev` | §3.1.1: стартует с env §7, smoke зелёный | pass |
| `./gradlew clean` | входит в §1.1 | pass |
| `./scripts/check-capabilities.sh` | §1.8 exit 0; негативы §2.7–2.9 | pass |
| `./gradlew capabilitiesCheck` | §1.7 exit 0; негативы §2.7–2.9 | pass |
| `./gradlew jacocoTestReport` (заявлена неподдерживаемой) | §2.11 — падает ровно как описано | pass |
| корневые `quarkusDev`/`quarkusBuild` | `./gradlew tasks --all`: корневых совпадений **0**; `src:app:quarkusDev`/`src:app:quarkusBuild` есть | pass |

#### 3.1.1 Документированный dev-запуск (AGENTS.md §7)

```
$ export VK_SECRET=test VK_CONFIRMATION_CODE=test VK_API_TOKEN=test VK_BOT_ID=1 \
         VANE_URL=http://localhost:3000 SUMMARY_ENABLED_CHATS=1,2,3
$ ./gradlew :src:app:quarkusDev
  vk-bot 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.34.2) started in 19.983s. Listening on: http://localhost:8080
$ curl -X POST /vk/callback -d '{"type":"confirmation","secret":"test"}'  -> http=200, body "test"
$ curl -X POST /vk/callback -d '{"type":"confirmation","secret":"wrong"}' -> http=403, {"error":"Secret code not provided or incorrect"}
$ curl /q/health                                                          -> http=503
```
Dev-процесс остановлен; порты 8080 и 5005 свободны. DevServices-контейнеры старта: `postgres:18` PT1.14s,
`rabbitmq:latest` PT3.59s, `ollama/ollama:latest` PT1.20s, `grafana/otel-lgtm:0.12.0` PT4.93s,
`testcontainers/ryuk:0.13.0` PT0.30s.

### 3.2 Карта модулей

- `settings.gradle.kts:22-32` — **11** `include`; все каталоги существуют (`src/{ai,app,infrastructure,persistence,processor,receiver,share,vk-facade}` + `src/testing/{arch-tests,test-common,test-fixtures}`).
- **12** `build.gradle.kts` (корень + 11 модулей); `GradleDependenciesTest > build files are discovered` проходит (§1.6).
- В `AGENTS.md` §3 — 11 строк модулей; в `docs/architecture/modules.md` — секции на все 11.
- `jvmToolchain(21)` объявлен во всех **11** модулях (`grep -c` = 11), как написано в AGENTS.md:17.
- Классы/файлы, на которые ссылается карта (`VaneSearchToolAdapter.kt:10-32`, `SendMessageEventProcessor.kt:12-22`,
  `MessageNewEventProcessor.kt:12-22`, `PersistenceDataInProcessAdapter.kt:17-144`, `VkProfileInProcessAdapter.kt:8-36`),
  существуют и попадают в проверку 655 backtick-ссылок (§3.5).

### 3.3 Точки входа и тест-пути `docs/capabilities/`

- Файлов: `index.md` + **9** возможностей; все 9 ссылок индекса резолвятся, сирот нет; у всех 9 `Статус: delivered`.
- `./scripts/check-capabilities.sh` → `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0 (§1.8).
- Acceptance criteria: **17** в **7** файлах (`vk-webhook` 5, `vk-message-send` 3, `answer-decision` 2,
  `message-storage` 2, `vk-profiles` 2, `event-dispatch` 2, `ai-answer` 1); у `conversation-summary` и
  `ai-tools-vane` AC нет — это названный пробел (§6 H5), а не pass.
- Все `src/…`-пути: **96 уникальных**, все существуют (независимая проверка, не только скрипт). Все 17 AC-блоков
  содержат тест-путь именно под `src/**/src/(test|testFixtures)/` (проверка скриптом; негатив §2.9).
  `ADR NNNN` резолвятся в `docs/adr/0001–0006`.

### 3.4 Ключи `docs/development/configuration.md`

- Из таблиц документа извлечено **34 строки-ключа** (с точкой/дефисом; **32 уникальных** — `summary.system-prompt`
  и `summary-ready-queue` повторяются в таблице мёртвых) + 3 camelCase-ключа (`aiUseranswerSystemprompt`,
  `aiSumarizationSystemprompt`, `aiOcrSystemPrompt`). Все резолвятся: 32 — YAML-ключом (с разворотом вложенности)
  или литералом в YAML/коде, 3 — `@ConfigProperty(name = …)` в коде. `UNRESOLVED=0`.
- Каналы из таблицы очередей: `all-events-queue`, `retry-5s-queue`, `retry-1h-queue`, `dlq-final-queue`,
  `summary-ready-queue`, `events-exchange` — все 6 есть в `application.yml`.
- Env-переменные: 16 документированных (`VK_*`, `VANE_URL`, `SUMMARY_*`, `USER_ANSWER_SYSTEM_PROMPT`,
  `OCR_SYSTEM_PROMPT`, `JOOQ_JDBC_*`) присутствуют в YAML/коде; `QUARKUS_DATASOURCE_JDBC_URL/_USERNAME/_PASSWORD`
  документ называет стандартным отображением env → `quarkus.datasource.*` (в коде их литералов и не должно быть);
  `SRCFG00040`/`SRMSG00208` — коды ошибок, не переменные.
- Мёртвые ключи описаны как мёртвые и существуют: `circuit-breaker.vk-profile` (`application.yml:174-179`),
  `summary.system-prompt` (`:17`), `summary-ready-queue` (канал без `@Incoming`). Строки `application.yml:14`
  (`enabled-chats: ${SUMMARY_ENABLED_CHATS:}`) и `:179` (длина файла) совпадают.
- Версии в AGENTS.md §2 сверены с кодом: Quarkus 3.34.2 (`gradle.properties:3-7`), LangChain4j 1.8.4 (`:8`),
  Kotlin 2.3.0 (`settings.gradle.kts:14`), detekt 1.23.8, ktlint 13.1.0, jOOQ 3.19.10, ArchUnit 1.3.0,
  WireMock 3.9.1, Awaitility 4.2.0, JaCoCo 0.8.14 (`dependencyInsight … jacocoAnt` → `org.jacoco.agent:0.8.14`).

### 3.5 Ссылки документов

- Скрипт по `AGENTS.md`, `README.md`, `docs/**/*.md`: **655** backtick-ссылок `path:N[-M]`. Все резолвятся
  (по полному пути, по суффиксу модульного пакета или по basename); диапазонов за концом файла — **0**;
  битых markdown-ссылок между документами — **0**. Единственная «элизия» `vk-facade/.../adapter/output/vk/exception/VkException.kt:6-8`
  указывает на существующий `src/vk-facade/.../vkFacade/adapter/output/vk/exception/VkException.kt`.

### 3.6 UNACTUAL-утверждения audit.md

Все **57** строк-утверждений audit.md §4, помеченных UNACTUAL, проверены против текущих документов:
**45 переписаны, 9 оставлены с явной причиной (отрицание/запрет/история ADR), 3 удалены, SURVIVED-FALSE — 0.**
Ни одно не выжило как фактическое утверждение. Контрольные grep'ы (мои, по `AGENTS.md`, `README.md`, `docs/`):

| Токен | Вхождений | Смысл |
|---|---|---|
| `SEND_MESSAGE` | 1 | только запрет «не документировать» (AGENTS.md:225) |
| `JsonbConverter`, `METADATA`, `NewMessageUsecaseInput`, `AiChatbotAnswerMessageOutputPort`, `MessageAnswerTextGenerateCommand`, `ChatSummarizationService`, `RetryStrategies`, `DlqFinalHandler`, `MDC.put`, `ai-tools.md` | 0 | удалены |
| `DATABASE_URL`/`DB_USERNAME`/`DB_PASSWORD` | 2 | запрет (AGENTS.md:224) + история (ADR 0006:13) |
| `/metrics`, `Tracer`, `MeterRegistry`, `Event Sourcing`, `CQRS` | только отрицания | «в коде нет» / таблица «Чего в коде нет» |
| `5.131` | 2 | история в ADR 0006; текущая версия `5.199` |

Отдельно: `./gradlew build` в этом проходе не проверяет «отсутствие второго раннера» — это утверждение
`README.md:98` не подтверждается деревом (§3.9).

### 3.7 Закрытие B1 (блокирующая находка раунда 1)

- `WireMockTestResource.kt` — 38 строк: `start()` возвращает только `quarkus.rest-client.vk.url` и
  `quarkus.langchain4j.ollama.base-url` (`:24-31`); `setupVkFacadeStubs`/`setupPersistenceStubs` и их вызовы удалены,
  `resetToDefaultStubs()` = `server?.resetAll()`.
- В production-коде ровно **2** `@RegisterRestClient`: `VkClient.kt:12` (`configKey = "vk"`) и
  `VaneSearchClient.kt:11` (`configKey = "vane"`); REST-клиентов `persistence`/`vk-facade` нет.
- `docs/development/testing.md:132-137` прямо фиксирует: «Заглушек удалённых loopback-эндпоинтов `/profiles/*` и
  `/persistence/*` в нём нет, поэтому e2e не пройдёт при возврате HTTP-контура между модулями (ADR 0004)».
- `docs/development/configuration.md` мёртвых rest-client-ключей не содержит (таблица мёртвых ключей — 3 записи,
  все реальные).
- Доказательство, что e2e работает без заглушек: `./gradlew test` → 59/0/0/0 (§1.2), app — 7 тестов, включая
  `full flow - message_new event is processed end to end with VK API call`.

### 3.8 Закрытие nits раунда 1

| id | Находка раунда 1 | Проверка сейчас |
|---|---|---|
| N1 | DEBUG→INFO на горячем пути `ProcessSummaryCommandImpl` | `logger.log(System.Logger.Level.DEBUG, …)` на горячих ветках (`:38`, `:46-47`, `:68`, `:73-74`); `INFO` — только финальные записи (`:81`, `:93`) |
| N2 | L1–L6 приписаны одному тесту | `AGENTS.md:73`: «`LayerDependenciesTest` (L1–L5…), `ModuleDependenciesTest` (L6…)»; `patterns.md:17` так же; 15 arch-тестов = 5+7+3 |
| N3 | `docs/README.md` говорил «мёртвые ключи удалены», а три остались | `docs/README.md:32`: «оставшиеся мёртвые ключи — долг» |
| N4 | Смещённые диапазоны зависимостей тестов | `AGENTS.md:24` и `ADR 0001`: `src/app/build.gradle.kts:45-56` — WireMock `:52`, Awaitility `:55-56`, всё внутри диапазона |
| N5 | Чек карты принимал production-путь за тест | `scripts/check-capabilities.sh:102` требует `src/.../src/(test|testFixtures)/...`; негатив §2.9 — `FAIL`, exit 1 |

### 3.9 Новый nit вне критериев (не блокирует)

`README.md:98`: «`./gradlew build` also checks that no second runner is produced». В `build.gradle.kts` такой
проверки нет (grep по `quarkus-app`/`runner` — 0; единственные `tasks.register` — `coverageReport`,
`coverageVerification`, `capabilitiesCheck`). Проверка одного раннера есть только в CI-джобе `build`
(`.github/workflows/build-and-test.yml:26-27`). Фактическое утверждение §6 DoD не нарушает, но формулировка
README неточна. Внесено в §6 как G8, владелец — стадия docs.

## 4. C4 — таблица устаревших утверждений audit.md

Проверены все 12 подразделов audit.md §4 (57 строк). Статусы: **переписано — 45**, **оставлено с явной
причиной — 9**, **удалено — 3**, **выжило как ложное — 0**.

| audit.md | Что было UNACTUAL | Что в дереве сейчас | Статус |
|---|---|---|---|
| §4.1 AGENTS.md | 7 модулей без app/testing; fake в `objectProvider/fake/`; `quarkusDev` неоднозначен; `check` = tests+lint; «no trailing commas» | 11 модулей (`AGENTS.md:30-47`); реальные пути fake; `:src:app:quarkusDev`; таблица гейтов с exit-кодами; trailing comma разрешена (`:201-202`) | переписано |
| §4.2 README.md | `jacocoTestReport`; `/metrics`; jOOQ-кодогенерация; env `DATABASE_URL/DB_USERNAME/DB_PASSWORD` | `coverageReport`/`coverageVerification`; `/metrics` отсутствует (отрицания); таблицы jOOQ вручную; реальные env | переписано; `jacocoTestReport` и env — с явной причиной |
| §4.3 docs/README.md | нет индекса ADR/capabilities, ссылка на удалённый `ai-tools.md` | индекс ADR 0001–0006 (`:27-32`) и capabilities (`:36-37`); `ai-tools.md` — 0 вхождений | переписано; удалённая ссылка |
| §4.4 overview.md | `SEND_MESSAGE`; «независимость от БД» для всех | реальные события `MESSAGE_NEW…ANSWER_MESSAGE`; границы домен/usecase vs адаптеры; CQRS/event sourcing — «нет» | переписано |
| §4.5 modules.md | фантомные классы, `SEND_MESSAGE`, «только события», `share/adapter` | реальные классы; `ANSWER_MESSAGE`; in-process порты; `share` без `adapter` | переписано |
| §4.6 patterns.md | фантомные классы, Event Sourcing/CQRS, Strategy/Chain | реальные Ports & Adapters/Command/Decorator/VO/Repository; отсутствующие — в таблице «Чего в коде нет» (`patterns.md:123`) | переписано; отсутствующие — с причиной |
| §4.7 data-flow.md | `SEND_MESSAGE`, перепутанные процессоры, `METADATA`/`JsonbConverter`, `Tracer`/`MDC`/`MeterRegistry`, бенчмарки | `ANSWER_MESSAGE`; верные процессоры; JSONB; «собственного кода с Tracer, MDC или MeterRegistry нет» (`:182`); бенчмарков нет | переписано; Tracer/MDC/MeterRegistry — с причиной; бенчмарки удалены |
| §4.8 ai-tools.md | `@Tool` на команде, выдуманные имена/URL | файла нет; `@Tool` на `VaneSearchToolAdapter`; имена `search_web/…`; содержимое — в `docs/capabilities/ai-tools-vane.md` | удалено/переписано |
| §4.9 testing.md | фантомные fake-классы и сигнатуры | реальные fake (`FakeVkClient` с 4-аргументным `sendMessage`, `FakeVoProvider`); 59 тестов — совпадает (§1.2) | переписано |
| §4.10 configuration.md | нечитаемые env, несуществующие ключи RabbitMQ/логов/OTel/Prometheus/профилей/health, Dockerfile `gradle:8-jdk21`, VK 5.131 | все ключи таблиц резолвятся (§3.4); мёртвые названы; VK 5.199; `%dev/%prod` нет; `/q/health`; UBI9-образ | переписано; env/Prometheus/5.131 — с причиной |
| §4.11 deployment.md | нечитаемые env; native из корневого `build/`; JVM tuning | JVM-образ из `src/app/build/quarkus-app`; native/legacy из `src/app/build/*-runner`; env реальные; ограничение `.dockerignore` записано с причиной | переписано; JVM tuning удалён; ограничение — с причиной |
| §4.12 ADR/capabilities | каталогов нет | `docs/adr/0001–0006` (6 `accepted`; предшественников не было, `supersedes` не нужен), `docs/capabilities/index.md` + 9 файлов, чек в `check` | создано |

Записи «оставлено без изменения с причиной» (не дефекты подготовки): `jacocoTestReport` не поддерживается —
записано в AGENTS.md §6 с причиной (§2.11 воспроизводит); `/metrics`, `Tracer`, `MDC`, `MeterRegistry`,
Event Sourcing, CQRS — названы отсутствующими; `DATABASE_URL`-семейство и VK 5.131 — запрет/история ADR;
Docker-образы не собирались — вне scope `design.md` §12, причина в `deployment.md`.

## 5. C5 — зависимости от машины, сети и версий

| Зависимость | Факт | Где видно |
|---|---|---|
| JDK 21 (Temurin 21.0.5) | `java -version` = 21.0.5; toolchain 21 во всех 11 модулях; CI `java-version: '21'` | env, build-файлы, workflow |
| Gradle 9.3.0 wrapper | `./gradlew --version` = 9.3.0; дистрибутив тянется из сети при холодном кэше | `gradle/wrapper/gradle-wrapper.properties:3` |
| Docker 28.3.2 | обязателен для app-тестов (DevServices) и `:src:app:quarkusDev`; образы `postgres:18`, `rabbitmq:latest`, `ollama/ollama:latest`, `grafana/otel-lgtm:0.12.0`, `testcontainers/ryuk:0.13.0` | dev-лог, clean build |
| OTLP-коллектор `localhost:4317` | локально отсутствует: **17** строк `Connection refused: localhost/127.0.0.1:4317` в clean build; тесты зелёные (шум логов); в CI коллектора тоже нет | `/tmp/vk-verify3/01-clean-build.log` |
| Порты | dev: HTTP 8080, JDWP 5005 — после прогона свободны; тесты: WireMock — динамический порт, Vane — `localhost:8075` из тестовых yaml, DevServices — случайные порты | dev-лог, `lsof`, тестовые конфиги |
| Версии, влияющие на результат | `jacocoTestReport` падает именно на Gradle 9.3 task-validation (jandex); JaCoCo 0.8.14; ktlint-плагин 13.1.0; Kotlin 2.3.0; Quarkus 3.34.2; LangChain4j 1.8.4; jOOQ 3.19.10; detekt 1.23.8; ArchUnit 1.3.0; WireMock 3.9.1; Awaitility 4.2.0 | build-файлы, `dependencyInsight` |
| GitHub Actions | CI на `ubuntu-latest` (Temurin 21, Docker) **в этом проходе не исполнялся** (нет раннера); проверены валидность YAML (позитив/негатив §2.10) и соответствие job'ов гейтам: `build` = `:src:app:quarkusBuild` + артефакт, `test` = `./gradlew test`, `lint` = `detekt ktlintCheck`, `coverage` = `test coverageReport coverageVerification`, `docs` = `check-capabilities.sh` | `.github/workflows/build-and-test.yml` |
| Снимки времени/счётчиков | clean build этого прохода **168 задач / 2m 8s**; документы фиксируют 2m 4s — та же природа, зависит от кэша/машины. Dev-старт: документировано 17.6 s, здесь **19.983 s** | §1.1, §3.1.1 |
| Локальная ОС | прогоны на macOS arm64 (Darwin); CI на ubuntu-latest; регистрозависимость путей не проверялась | `uname` |

## 6. Named gaps (не блокируют подготовку среды)

| id | Гэп | Доказательство | Владелец / условие |
|---|---|---|---|
| H1 | `/q/health`, `/ready`, `/live` отдают **503 DOWN**: 7 incoming-каналов без downstream-консьюмера (`retry-*-queue`, `dlq-final-queue`, `summary-ready-queue`) попадают в messaging health как `[KO]` | §3.1.1: `http=503`; документы описывают фактическое поведение | implement: исключить каналы из health или дать консьюмера; на гейты не влияет |
| H2 | `${SUMMARY_ENABLED_CHATS:}` — пустой stub-дефолт: непустое значение обязательно, иначе `SRCFG00040` | `application.yml:14`, `ProcessSummaryCommandImpl.kt:18`; документы (AGENTS §§5,7; configuration.md) описывают обязательность верно | implement: убрать пустой дефолт; документы не потребуют правки |
| H4 | Docker-образы (JVM/native/legacy) не собирались; native/legacy требуют правки `.dockerignore` | `deployment.md:53-55`; `docker build` в проходе не выполнялся | вне scope подготовки (`design.md` §12) |
| H5 | Продуктовые AC-пробелы: 17 AC в 7 возможностях; у `conversation-summary` и `ai-tools-vane` AC нет; у модуля `ai` 0 собственных тестов | §3.3, §1.2 | следующая тестовая работа (test-authoring); verify тесты не добавляет |
| G6 | DevServices-контейнеры переживают жёсткую остановку: сейчас **37** запущенных `postgres:18` (всего 146 контейнеров, накоплены проходами) | `docker ps`; после прогона порты 8080/5005 свободны | эксплуатационное примечание; на гейты не влияет |
| G7 | CI-джобы на GitHub не исполнялись в этом проходе (нет раннера) | §5: валидный YAML + локальные аналоги всех команд зелёные | первый push/PR прогонит их |
| G8 | `README.md:98` утверждает, что `./gradlew build` проверяет отсутствие второго раннера; локальной проверки нет (только CI-джоб `build`) | §3.9: `grep` 0 в `build.gradle.kts`; проверка — `build-and-test.yml:26-27` | docs: переформулировать (nit, вне критериев) |

## 7. Воспроизведение

```bash
# C1 — из чистого состояния
./gradlew clean build --console=plain
./gradlew test --console=plain
./gradlew detekt --console=plain
./gradlew ktlintCheck --console=plain
./gradlew coverageReport coverageVerification --console=plain
./gradlew :src:testing:arch-tests:test --rerun-tasks --console=plain
./gradlew capabilitiesCheck --console=plain
./scripts/check-capabilities.sh
./gradlew :src:app:quarkusBuild --console=plain

# C2 — негативные прогоны (файл-нарушитель → гейт → удалить файл / откатить файл)
# detekt: строка >120 в src/share/...; ktlint: val value=1 в src/processor/...
# ArchUnit: класс receiver-пакета в persistence; класс ai/usecase -> ConversationContextRestAdapter
# граф: implementation(project(":src:persistence")) в src/ai/build.gradle.kts
sed -i '' 's|^val coverageMin = 0.25$|val coverageMin = 0.99|' build.gradle.kts
./gradlew coverageVerification --console=plain          # FAILED, exit 1
# вернуть 0.25
# карта: путь/ADR/AC-тест-путь в docs/capabilities/*.md -> ./scripts/check-capabilities.sh  # FAIL, exit 1

# C3.1 — документированный dev-запуск и smoke
export VK_SECRET=test VK_CONFIRMATION_CODE=test VK_API_TOKEN=test VK_BOT_ID=1 \
       VANE_URL=http://localhost:3000 SUMMARY_ENABLED_CHATS=1,2,3
./gradlew :src:app:quarkusDev --console=plain
curl -X POST localhost:8080/vk/callback -H 'Content-Type: application/json' \
     -d '{"type":"confirmation","secret":"test"}'       # http=200, "test"
curl -X POST localhost:8080/vk/callback -H 'Content-Type: application/json' \
     -d '{"type":"confirmation","secret":"wrong"}'      # http=403
curl localhost:8080/q/health                            # http=503 (известное ограничение H1)
```

Логи этого прохода: `/tmp/vk-verify3/` (`01-clean-build.log`, `03-gates.log`, `neg-*.log`, `07-quarkusdev.log`,
`07-smoke.txt`, `08-final-build.log`); каталог временный и воспроизводится командами выше.

## 8. Границы стадии

- **Изменён только** `prepared-report.md`. Исходники, build-файлы, конфиги, CI, скрипты и документы не менялись:
  `git status --short` до и после стадии — одинаковые 147 путей (`diff` пуст), `HEAD` = `0f0eae2`.
- Все временные нарушения §2 откачены; контрольные суммы `build.gradle.kts`, `src/ai/build.gradle.kts`,
  `docs/capabilities/message-storage.md`, `docs/capabilities/event-dispatch.md`, `docs/capabilities/vk-profiles.md`
  восстановлены (`sha256sum -c` → `OK`); временные классы `GateProof*` удалены, каталог-нарушитель удалён.
- Dev-процесс остановлен, порты 8080/5005 свободны; тесты не добавлялись и не правились.
- Значения измеренной линии покрытия в `build.gradle.kts:19` (`measuredLineCoverage = 0.2796`) совпадают с
  фактическим `0.2796` (§1); устаревание этой константы не влияет на гейт (гейт сравнивает с `coverageMin`).
