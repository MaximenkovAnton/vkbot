# Скаффолд проекта VK Bot

**Стадия:** implement (implementation), board task `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`, задача «Подготовка проекта: vk».
**Вход:** `design.md` §11 (дерево изменений) и `audit.md` этой же задачи. Реализовано ровно дерево дизайна; новых проектных решений эта стадия не принимала. Отклонения от буквы дерева перечислены в §3 с причиной.
**Окружение прогонов:** macOS, JDK 21.0.5 (Temurin), Gradle wrapper 9.3.0, Docker 28.3.2 (нужен DevServices для интеграционного теста), корень `/Users/asmaksime1/IdeaProjects/test/vk`. Все команды ниже выполнены из корня.
**Rework после review раунда 1 (2026-10-29):** задача возвращена на `implement` с блокирующей находкой B1 (мёртвые REST-заглушки в `WireMockTestResource.kt` маскируют регрессию ADR 0004) и nit N1 (уровни логирования). Правки и повторные прогоны — §7. Документные правки B1/N2–N5 остаются стадии `docs` (review §5).

## 0. Итог

- `./gradlew clean build` → **exit 0**, `BUILD SUCCESSFUL in 2m 1s`, 167 задач; повторный `./gradlew build` → **exit 0**.
- Деплой один: `src/app/build/quarkus-app/quarkus-run.jar` существует, `build/quarkus-app` в корне отсутствует (smoke §13 дизайна).
- Тесты: **59 тестов, 0 падений, 0 ошибок, 0 пропусков** (`src/**/build/test-results/test/*.xml`).
- Все гейты падают на своих негативных проверках и проходят после откота: цитаты и exit-коды — `tooling.md` §3.
- Работает ровно то, что описано: доки пишет стадия `docs`, карту возможностей — стадия `capabilities`; в этом дереве `AGENTS.md`, `README.md`, `docs/**` и `docs/adr/**` не изменялись (кроме временных файлов доказательств, удалённых после прогона).

## 1. Дерево проекта (11 модулей, один деплой)

```
vk/
├── build.gradle.kts               # агрегатор + конвенции: base, subprojects{}, detekt, ktlint, jacoco, coverageReport/coverageVerification
├── settings.gradle.kts            # без изменений (11 include, Kotlin 2.3.0, foojay 0.8.0)
├── gradle.properties              # без изменений (Quarkus 3.34.2, LangChain4j 1.8.4, detekt 1.23.8)
├── detekt-config.yml              # без изменений (maxIssues: 0), используется всеми модулями
├── .editorconfig                  # без изменений (источник стиля ktlint)
├── scripts/
│   └── check-capabilities.sh      # НОВЫЙ: чек карты возможностей (пути + ADR id)
├── .github/workflows/
│   └── build-and-test.yml         # 5 job'ов: build, test, lint, coverage, docs
└── src/
    ├── share/                     # домен и контракты; + domain/event/{SummaryRequestedPayload,SummaryReadyPayload}
    ├── receiver/                  # вход VK Callback → MESSAGE_NEW
    ├── persistence/               # хранение; + adapter/input/inprocess, adapter/output/json, port/output/ForwardedMessagesSerializationPort
    ├── processor/                 # решение «нужен ли AI-ответ»
    ├── ai/                        # AI-ответ, саммаризация, Vane, OCR (REST-контуры удалены)
    ├── vk-facade/                 # выход в VK API; + adapter/input/inprocess
    ├── infrastructure/            # RabbitMQ-диспетчер, retry/DLQ, декораторы (копии конфига удалены)
    ├── app/                       # единственный деплой: @QuarkusMain в package com.simarel.vkbot.app
    ├── docker/                    # Dockerfile.jvm|legacy-jar|native|native-micro (источник артефакта — src/app/build)
    └── testing/
        ├── test-common/           # общий тестовый application.yaml
        ├── test-fixtures/         # fake-объекты
        └── arch-tests/            # ModuleDependenciesTest, GradleDependenciesTest, LayerDependenciesTest
```

Разрешённый граф production-зависимостей после правок: `receiver|processor|ai|persistence|vk-facade|infrastructure -> share`, `app -> все 7 бизнес-модулей`, `share -> (никого)`. Проверяется тестом `GradleDependenciesTest`.

## 2. Дерево дизайна §11 — файл за файлом

### 2.1 Сборка и CI (§11.1)

| Файл | Действие по дизайну | Сделано |
|---|---|---|
| `build.gradle.kts` | переписать: `base`-агрегатор + `subprojects {}`, Kotlin/JDK21/BOM/detekt/ktlint/jacoco/test, задачи `coverageReport` и `coverageVerification`, `check` зависит от порога, убрать `io.quarkus` и зависимости корня | да |
| `src/app/build.gradle.kts` | единственный Quarkus-деплой; сюда переезжают deployable-расширения, убрать BOM и явные JUnit-версии | да: добавлены `quarkus-info`, `quarkus-logging-json`, `quarkus-opentelemetry`, `quarkus-micrometer-opentelemetry`, `quarkus-observability-devservices`, `quarkus-observability-devservices-lgtm`, `quarkus-smallrye-health`, `quarkus-rest-client-jackson` |
| `src/ai/build.gradle.kts` | убрать `project(":src:persistence")`, BOM, `:latest` | да |
| `src/processor/build.gradle.kts` | убрать `project(":src:ai")`, BOM | да (и неиспользуемый `testFixturesImplementation(project(":src:ai"))`, см. §3) |
| `src/persistence/build.gradle.kts` | убрать BOM, `quarkus-rest-client-jackson`, явные JUnit-версии; оставить jOOQ/Liquibase/rest | да |
| `src/receiver/build.gradle.kts` | убрать BOM, `:latest` | да |
| `src/vk-facade/build.gradle.kts` | убрать BOM, `:latest` | да |
| `src/infrastructure/build.gradle.kts` | убрать BOM, `:latest` | да |
| `src/share/build.gradle.kts` | убрать BOM | да |
| `src/testing/arch-tests/build.gradle.kts` | добавить `project(":src:persistence")` и `project(":src:app")`, убрать BOM | да; плюс `systemProperty("project.root", rootDir.absolutePath)` — тесту нужен корень репозитория |
| `src/testing/test-fixtures/build.gradle.kts` | убрать BOM | да (и перенос `test-common` в test-конфигурацию, см. §3) |
| `detekt-baseline.xml` в модулях с находками | создать один раз `./gradlew detektBaseline`, закоммитить | созданы 8: `src/{ai,app,infrastructure,persistence,processor,share,vk-facade}/detekt-baseline.xml`, `src/testing/arch-tests/detekt-baseline.xml` |
| `.github/workflows/build-and-test.yml` | 5 job'ов из §7.4 | да |
| `scripts/check-capabilities.sh` | новый чек §7.1/§10, исполняемый | да (`chmod +x`), 70 строк bash |
| `gradle.properties`, `.editorconfig`, `settings.gradle.kts` | без изменений | не тронуты |

### 2.2 Конфигурация и деплой (§11.2)

| Файл | Действие | Сделано |
|---|---|---|
| `src/app/src/main/resources/application.yml` | добавить `aiUseranswerSystemprompt`, `aiSumarizationSystemprompt`, `aiOcrSystemPrompt`, `vk.bot.id`, `vk.bot.mention`, `quarkus.rest-client.vane`; убрать `quarkus.jooq`, `quarkus.rest-client.persistence`, `quarkus.rest-client.vk-facade` | да; `vane` перенесён целиком (`url: ${VANE_URL}`, `scope`, `connect-timeout: 30000`, `read-timeout: 120000`) |
| `src/infrastructure/src/main/resources/application.yml` | удалить | удалён |
| `src/infrastructure/src/main/resources/application-queues.yml` | удалить | удалён (`diff -q` с app-копией подтверждал идентичность) |
| `src/infrastructure/src/main/resources/application-local.yml` | удалить | удалён |
| `src/docker/Dockerfile.native` | `build/*-runner` → `src/app/build/*-runner` | да |
| `src/docker/Dockerfile.native-micro` | то же | да |
| `src/docker/Dockerfile.legacy-jar` | `build/lib/*`, `build/*-runner.jar` → `src/app/build/...` | да |
| `src/docker/Dockerfile.jvm` | без изменений (уже `src/app/build`) | не тронут |

### 2.3 Код: in-process порты и контракты (§11.3)

| Файл | Действие | Сделано |
|---|---|---|
| `ai/adapter/output/persistence/PersistenceService.kt` | удалить | удалён |
| `ai/adapter/output/persistence/PersistenceDataRestAdapter.kt` | удалить | удалён |
| `ai/adapter/output/persistence/SummaryPersistenceRestAdapter.kt` | удалить | удалён |
| `ai/port/output/persistence/*.kt` (9 файлов) | удалить | удалены |
| `ai/command/summary/GenerateSummaryCommandImpl.kt` | импорты `ai.port.output.persistence.*` → `share.port.output.persistence.*` | да (имена классов: `*AiStored*` → `Stored*`) |
| `ai/command/summary/ProcessSummaryCommandImpl.kt` | те же импорты + `io.quarkus.logging.Log` → `java.lang.System.Logger` (поверх WIP-правок) | да |
| `persistence/adapter/output/client/vkfacade/VkFacadeService.kt` | удалить | удалён |
| `persistence/adapter/output/vkfacade/VkFacadeRestAdapter.kt` | удалить | удалён |
| `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` | создать: 9 классов через `PersistenceDataInputPort` | создан |
| `vk-facade/adapter/input/inprocess/VkProfileInProcessAdapter.kt` | создать: 2 класса через `VkProfileInputPort` | создан |
| `share/domain/event/SummaryRequestedPayload.kt` | создать | создан |
| `share/domain/event/SummaryReadyPayload.kt` | создать | создан |
| `persistence/usecase/SaveMessageUsecase.kt` | payload из share, убрать `ObjectMapper`, инжектить порт сериализации | да (`ForwardedMessagesSerializationPort`) |
| `ai/adapter/input/event/SummaryRequestedEventProcessor.kt` | payload из share | да |
| `persistence/usecase/PersistenceDataUsecase.kt` | payload из share | да |
| `vk-facade/adapter/input/event/SummaryReadyEventProcessor.kt` | payload из share | да |
| `persistence/port/output/ForwardedMessagesSerializationPort.kt` | создать | создан (`OutputPort<Request, Response>`, `Request(messages: List<Message>)`, `Response(json: String?)`) |
| `persistence/adapter/output/json/JacksonForwardedMessagesAdapter.kt` | создать | создан |
| `persistence/domain/entity/Tables.kt` | перенести в `adapter/output/persistence/jooq/`, сменить `package` | `git mv` + package |
| `adapter/output/persistence/jooq/{JooqMessageRepository,JooqSummaryRepository,JooqVkGroupProfileRepository,JooqVkUserProfileRepository}.kt` | обновить импорт `Tables` | да |
| `persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt` | `Log` → `System.Logger` | да |
| `receiver/usecase/ReceiveMessageUsecase.kt` | `Log` → `System.Logger` | да |
| `app/Application.kt` | перенести в `.../app/`, package `com.simarel.vkbot.app` | `git mv` + package |

Удалённых файлов — 17 (3 REST-адаптера ai, 9 внутренних портов ai, 2 REST-клиента persistence, 3 копии конфига infrastructure). Созданных — 12 файлов кода плюс `scripts/check-capabilities.sh` и 8 baseline-файлов.

### 2.4 Архитектурные тесты и тесты нового кода (§11.4)

| Файл | Действие | Сделано |
|---|---|---|
| `ModuleDependenciesTest.kt` | `APP_PKG` теперь соответствует реальному пакету `com.simarel.vkbot.app` | да — самим переносом `Application.kt`; сам тест править не потребовалось, правило для app стало настоящим |
| `GradleDependenciesTest.kt` | переписать: невакуумный обход, allowlist production-рёбер, testing-модули только в тестах | переписан: 3 `@Test` |
| `LayerDependenciesTest.kt` | создать: L1–L5, по одному `@Test` | создан: 5 `@Test` |
| `persistence/src/test/.../PersistenceDataInProcessAdapterTest.kt` | создать: fake `PersistenceDataInputPort`, маппинг всех 9 портов | создан: 9 `@Test` |
| `vk-facade/src/test/.../VkProfileInProcessAdapterTest.kt` | создать: fake `VkProfileInputPort`, 2 порта | создан: 2 `@Test` |

### 2.5 Документы (§11.5)

Не в этой стадии: `AGENTS.md`, `README.md`, `docs/**`, `docs/adr/**`, `docs/capabilities/**` пишут стадии `docs` и `capabilities` своими контрактами. В этом дереве они не изменялись; `scripts/check-capabilities.sh` до стадии `capabilities` возвращает exit 1 на отсутствующем `docs/capabilities/index.md` — это ожидаемое состояние, а не дефект (см. `tooling.md` §3.7).

## 3. Отклонения от буквы дерева дизайна (и почему без них нельзя)

Все четыре — механика, а не проектное решение; форма из §1–§4 дизайна сохранена.

1. **Корневой `build.gradle.kts` объявляет `kotlin("jvm") apply false`, `detekt apply false`, `ktlint apply false` и `org.kordamp.gradle.jandex apply false`, а в `subprojects {}` применяет `org.jetbrains.kotlin.jvm`, detekt, ktlint и jacoco (как в §7.2).** Toolchain 21 объявлен в каждом модуле (`grep -c "jvmToolchain(21)"` = 1 во всех 11). Без объявлений `apply false` конвенционный блок не работает: (а) плагины detekt/ktlint, применённые из `subprojects {}`, ссылаются на классы Kotlin-плагина, которые обязаны быть в classloader'е корня (`NoClassDefFoundError: KotlinProjectExtension`); (б) при отсутствии любого Quarkus/Kotlin-плагина в корне плагин `org.kordamp.gradle.jandex` 2.3.0 в модулях падает с `ClassCastException: Banner$Inject_ cannot be cast to Banner` (класс грузится двумя classloader'ами). Объявление `apply false` в корне лечит оба случая и не применяет плагины к корню. Проверено: `./gradlew projects` и `./gradlew build`.
2. **Корневой `repositories {}`.** `coverageReport`/`coverageVerification` разрешают `org.jacoco:org.jacoco.ant`, а `repositories` в `subprojects {}` корень не покрывает. Без этого блока гейт покрытия падает с `Cannot resolve external dependency org.jacoco:org.jacoco.ant:0.8.14 because no repositories are defined`.
3. **`src/testing/test-fixtures`: `implementation(project(":src:testing:test-common"))` → `testFixturesImplementation(...)`.** Иначе невакуумный `GradleDependenciesTest` (правило «testing-модули только в test-конфигурациях») не может быть зелёным, а allowlist §3.2 для `test-fixtures` содержит только `share` и `vk-facade`. Поведение сохранено: `test-common` остаётся на test-runtime classpath потребителей `testFixtures(project(...))`; единственный Quarkus-тест (`src/app`) объявляет `test-common` явно.
4. **`src/app/src/test/resources/application.yaml` и `src/testing/test-common/src/main/resources/application.yaml` получили `quarkus.rest-client.vane.url: http://localhost:8075`.** `quarkus.rest-client.vane.url: ${VANE_URL}` в production-конфиге (как требует §6) без дефолта, а тесты обязаны запускаться без env; значение совпадает с профилем `integration-test`, где WireMock уже слушает 8075. Это тестовая конфигурация, а не прод-дефолт.

Дополнительно, для зелёного `ktlintCheck`: `VaneSearchToolAdapter.kt` получил `@file:Suppress("ktlint:standard:function-naming")`. Имена `search_web` / `search_academic` / `search_discussions` — это имена инструментов, по которым LangChain4j публикует их модели; переименование изменило бы поведение. Подавление точечное, на файл; в проекте уже используется `@Suppress` в DTO (`VkResponseDto.kt`).

Правка `:latest` → версия из BOM сделана в четырёх файлах, как в §11.1; версия `quarkus-bom` теперь объявляется один раз — в `subprojects {}` корня (проверено: `grep enforcedPlatform` находит только корневую строку).

## 4. Не тронуто, потому что уже соответствовало дизайну

| Файл | Почему не тронут |
|---|---|
| `settings.gradle.kts` | 11 include-модулей, Kotlin 2.3.0, foojay 0.8.0 — ровно то, что фиксирует §1 дизайна |
| `gradle.properties` | версии Quarkus/LangChain4j/detekt и jvmargs уже здесь |
| `gradle/wrapper/*` | Gradle 9.3.0 уже разрешён |
| `.editorconfig` | источник стиля ktlint, включая trailing comma на call site |
| `detekt-config.yml` | `maxIssues: 0` уже стоит; конфиг не менялся |
| `src/testing/test-common/build.gradle.kts` | ни BOM-строки, ни `:latest`, ни явных JUnit-версий нет |
| `src/docker/Dockerfile.jvm` | уже копирует из `src/app/build/quarkus-app` (`:87-90`) |
| `src/app/src/main/resources/application-queues.yml` | единственная оставшаяся копия DLQ-лестницы, подключена через `quarkus.config.locations` |
| три WIP-файла (`SummarizationAiAdapter.kt`, `ProcessSummaryCommandImpl.kt`, `SaveMessageUsecase.kt`) | не откатывались; два последних правлены поверх текущего состояния, как требует §14 дизайна |

## 5. Состояние рабочего дерева

- Изменено/добавлено 134 пути: 98 файлов в `git diff --stat` (`762 insertions(+), 621 deletions(-)`) плюс новые файлы (§2). Значительная часть diff — разовый `./gradlew ktlintFormat` по существующему коду (trailing comma на call site, пробелы вокруг операторов), как предписывает §7.2 дизайна.
- `build/`, `.ast-index/`, `.agile/` не коммитятся: `build/` в `.gitignore`, `.ast-index/` и `.agile/` остаются untracked и в набор изменений не входят.
- Коммит не создавался: набор изменений передаётся следующей стадии в рабочем дереве, три предсуществующих WIP-изменения остаются как были.

## 6. Вне scope этой стадии (по §12 дизайна)

Апгрейд JDK/Quarkus/Kotlin/LangChain4j, version catalog, dependency verification; удаление публичных REST-эндпоинтов `/persistence/*` и `/api/profiles/*`; идемпотентность/дедупликация сообщений и outbox; типизация `StoredMessage.forwardedMessages: String?`; удаление неиспользуемой OCR-команды; LICENSE/CHANGELOG/CODEOWNERS/`docker-compose`/Prometheus-registry; native-сборка как CI-гейт.

## 7. Rework после review раунда 1 (B1 + N1)

### 7.1 B1 (blocking): удалены мёртвые REST-заглушки удалённых loopback-контуров

Файл `src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt` сокращён 169 → 34 строки (−131). Удалено ровно то, что перечислило review:

- `setupVkFacadeStubs()` (2 заглушки `/profiles/*`) и `setupPersistenceStubs()` (9 путей `/persistence/*`);
- их вызовы в `resetToDefaultStubs()` (было `:29-30`) и в `start()` (было `:151-152`);
- переопределения `quarkus.rest-client.vk-facade.url` и `quarkus.rest-client.persistence.url` (было `:160-161`);
- KDoc «Internal services (vk-facade, persistence)» (было `:8-14`);
- ставший неиспользуемым импорт `com.github.tomakehurst.wiremock.client.WireMock`.

Итоговое поведение: `resetToDefaultStubs()` = `server?.resetAll()`; `start()` возвращает только `quarkus.rest-client.vk.url` и `quarkus.langchain4j.ollama.base-url` — два REST-клиента, которые в коде действительно есть (`VkClient`, `VaneSearchClient`). Проверка на остаток: `grep -n "setupVkFacadeStubs\|setupPersistenceStubs\|rest-client.vk-facade\|rest-client.persistence\|WireMock\." WireMockTestResource.kt` → 0 совпадений (exit 1). Смысл правки: `VkWebhookIntegrationTest` больше не может пройти с HTTP-контуром между модулями — доказательство ADR 0004 восстановлено (заглушок для внутренних сервисов нет, запрос в них некуда деть).

### 7.2 N1 (nit): логирование `ProcessSummaryCommandImpl` возвращено к DEBUG

В `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt` три ветки, которые в HEAD логировали `Log.debugf`, снова пишут `System.Logger.Level.DEBUG`: «already have pending summary» (`:46-49`), «No messages found» (`:68`), «Not enough messages» (`:73-77`). INFO на «Creating summary» / «Summary completed» и ERROR на сбое не менялись — как в HEAD.

### 7.3 Прогоны rework-прохода (exit-коды)

```
$ ./gradlew :src:app:test
BUILD SUCCESSFUL in 1m 7s            exit=0     # 39 actionable tasks
$ ./gradlew test
BUILD SUCCESSFUL in 1m 14s           exit=0     # 64 actionable tasks: 4 executed
$ ./gradlew build
BUILD SUCCESSFUL in 6s               exit=0     # 154 actionable tasks: 13 executed
$ ./gradlew build                     (повторно, дерево после откатов негативов)
BUILD SUCCESSFUL in 1s               exit=0     # 154 actionable tasks
```

Test XML после rework: 19 файлов, **tests=59, failures=0, errors=0, skipped=0** (`src/app`: `InitTest` 1 + `VkWebhookIntegrationTest` 6; сумма по модулям та же, что в ledger verify).

### 7.4 Что осталось другим стадиям и что не тронуто

- `docs` (review §5, п.3–7): `docs/development/configuration.md:117-118`, `docs/development/testing.md:132-135`, `AGENTS.md:73`, `docs/architecture/patterns.md:17`, `docs/README.md:32`, `docs/adr/0001-...:24`, формулировка AC-ветки `scripts/check-capabilities.sh:102` (N5).
- Не тронуты: production-код (кроме уровней логов N1), `docs/**`, `docs/adr/**`, `docs/capabilities/**`, build-конфигурация и CI. Рабочее дерево: 147 изменённых/удалённых/переименованных путей до и после rework, временных файлов негативных прогонов нет (`find src -name 'GateProof*'` пуст).
