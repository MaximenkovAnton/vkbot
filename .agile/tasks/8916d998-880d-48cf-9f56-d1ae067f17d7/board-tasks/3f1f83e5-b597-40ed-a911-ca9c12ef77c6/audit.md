# Аудит проекта vk

**Стадия:** audit (project-setup), задача `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`.
**Дата:** 2026-10-28. **Окружение:** macOS, JDK 21.0.5 (Temurin), Gradle wrapper 9.3.0. Все команды выполнены из корня `/Users/asmaksime1/IdeaProjects/test/vk`. Исходники в этой стадии не менялись; после прогонов `git status` показывает только три предсуществующих изменения (раздел 6).

## 0. Короткий ответ

Проект — рабочий Kotlin/Quarkus-монолит на 11 Gradle-модулях, и два гейта действительно работают: `./gradlew build` (exit 0) и `./gradlew test` (exit 0, 41 тест, 0 падений). Всё остальное из заявленного набора гейтов отсутствует: `./gradlew detekt` и `./gradlew ktlintCheck` завершаются `NO-SOURCE` (модули не проверяются), `./gradlew jacocoTestReport` вообще не существует (exit 1), порога покрытия нет, CI запускает только build + test (`.github/workflows/build-and-test.yml:25-29`), `docs/adr/` и `docs/capabilities/` отсутствуют. Документы есть — AGENTS.md, README.md, 5 файлов `docs/architecture/` и 3 файла `docs/development/`, — но проверка против кода показала устаревшие утверждения в каждом из них (раздел 4); `docs/README.md` — только индекс. Главные факты формы: два Quarkus-раннера (корень и `src/app`), синхронные REST-вызовы между модулями внутри одного деплоя (`localhost:8080`), дублированные REST-контракты и конфиги, а тест архитектурных зависимостей `GradleDependenciesTest` не может упасть by construction. Рабочее дерево грязное: три незакоммиченных файла.

## 1. Инвентарь

### 1.1 Манифесты, сборка, окружение

| Путь | Что это | Состояние |
|---|---|---|
| `settings.gradle.kts` | 11 include-модулей, Kotlin 2.3.0, foojay-resolver 0.8.0 | есть (`:14`, `:18`, `:22-31`) |
| `build.gradle.kts` | корневой Quarkus-проект, плагины, зависимости, detekt | есть, 93 строки |
| `gradle.properties` | версии Quarkus 3.34.2, LangChain4j 1.8.4, detekt 1.23.8 | есть (`:3-10`) |
| `gradle/wrapper/gradle-wrapper.properties` + `gradle-wrapper.jar` | Gradle 9.3.0-bin | есть (`:3`) |
| `gradlew`, `gradlew.bat` | launcher | есть |
| `.sdkmanrc` | `java=21.0.5-tem` | есть |
| `.editorconfig` | ktlint/intellij_idea, final newline, wildcard imports запрещены | есть |
| `detekt-config.yml` | `maxIssues: 0` | есть (`:2`) |
| `.gitignore`, `.dockerignore` | артефакты сборки, IDE, `.env` | есть |
| `gradle/libs.versions.toml` | version catalog | **отсутствует** |
| `gradle/verification-metadata.xml` | dependency verification | **отсутствует** |

### 1.2 Исходники и тесты

11 модулей: `src/{ai,app,infrastructure,persistence,processor,receiver,share,vk-facade}` + `src/testing/{arch-tests,test-common,test-fixtures}` (`settings.gradle.kts:22-31`). Это же множество подтверждается одиннадцатью `build.gradle.kts` в `src/`.

| Метрика | Значение | Команда |
|---|---|---|
| Kotlin main | 193 файла | `find src -path '*/src/main/*' -name '*.kt' | wc -l` |
| Kotlin test | 18 файлов | то же для `*/src/test/*` |
| Kotlin testFixtures | 8 файлов | то же для `*/src/testFixtures/*` |
| Модули с тестами | app, infrastructure, processor, receiver, share, vk-facade, testing/arch-tests | `find src -path '*/src/test/*' -name '*.kt'` |
| Модули без тестов | **ai, persistence**, test-common, test-fixtures | там же |

### 1.3 CI

Один workflow: `.github/workflows/build-and-test.yml` — `push`/`pull_request` в `main`, JDK 21 (Temurin), шаги `./gradlew build --scan` и `./gradlew test` (`:25-29`). Джобов/шагов lint, format-check, coverage, docker-build нет.

### 1.4 Документы

| Путь | Файлов | Комментарий |
|---|---|---|
| `AGENTS.md` | 280 строк | конституция, есть |
| `README.md` | 263 строки | есть; лицензия — заглушка (`:263`) |
| `docs/README.md` | индекс | ссылка на `architecture/ai-tools.md` отсутствует |
| `docs/architecture/` | 5 (`overview`, `modules`, `patterns`, `data-flow`, `ai-tools`) | есть |
| `docs/development/` | 3 (`testing`, `configuration`, `deployment`) | есть |
| `docs/adr/` | 0 | **отсутствует** |
| `docs/capabilities/` | 0 | **отсутствует** |

Ожидаемые экосистемой, но отсутствующие: `LICENSE` (README сам просит «Specify your license here», `README.md:263`), `CHANGELOG`, `.env.example`, `docker-compose.yml` (локальные PostgreSQL/RabbitMQ/Ollama поднимаются DevServices), `CODEOWNERS`, `.github/ISSUE_TEMPLATE/`, бот обновления зависимостей.

### 1.5 Деплой и прочие файлы

- `src/docker/{Dockerfile.jvm, Dockerfile.legacy-jar, Dockerfile.native, Dockerfile.native-micro}` — есть.
- `src/persistence/src/main/resources/db/changelog/` — Liquibase master + 4 change-файла (`001`–`004`).
- `vk-router-test.connekt.kts` — ручной HTTP-сценарий для `/vk/callback`, в CI и гейты не входит.
- `vk.iml` — IDEA-модуль.
- `.claude/`, `.serena/`, `.omc/` — артефакты агентских инструментов (часть в git: `.claude/plans/...`), не документация проекта.
- `.ast-index/` — индекс ast-index, untracked (создан этой сессией).

## 2. Стек по декларациям

| Слой | Объявление | Где |
|---|---|---|
| Язык / runtime | Kotlin 2.3.0; JVM toolchain 21; установлен JDK 21.0.5 | `settings.gradle.kts:14`, `build.gradle.kts:77-81`, `.sdkmanrc` |
| Сборщик | Gradle 9.3.0 (wrapper), Kotlin DSL, foojay-resolver 0.8.0 | `gradle/wrapper/gradle-wrapper.properties:3`, `settings.gradle.kts:18` |
| Фреймворк | Quarkus 3.34.2 | `gradle.properties:3-7`; BOM продублирован числом в каждом модуле (`src/ai/build.gradle.kts:18`, `src/app/build.gradle.kts:25`, `src/persistence/build.gradle.kts:24` и т.д.) |
| AI | LangChain4j 1.8.4 через `quarkus-langchain4j-ollama` | `gradle.properties:8`, `build.gradle.kts:46` |
| БД | PostgreSQL (`quarkus-jdbc-postgresql`), Agroal; миграции **Liquibase** (`quarkus-liquibase`) | `build.gradle.kts:31-35`, `src/persistence/build.gradle.kts:48-51`; changelog: `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml` |
| jOOQ | `org.jooq:jooq:3.19.10`, кодогенерация `nu.studer.jooq` 9.0 | `src/persistence/build.gradle.kts:4`, `:45`, `:94-130` |
| Брокер | RabbitMQ: `quarkus-messaging-rabbitmq`, `smallrye-reactive-messaging-rabbitmq` | `build.gradle.kts:38`, `src/infrastructure/build.gradle.kts:19-21` |
| Прочее runtime | REST, rest-client-jackson, config-yaml, logging-json, smallrye-health, micrometer-opentelemetry, opentelemetry, observability-devservices(+lgtm), **quarkus-flyway**, **quarkus-jacoco** | `build.gradle.kts:31-46` |
| Тесты | JUnit 5, quarkus-junit5, rest-assured, WireMock 3.9.1, Awaitility 4.2.0, ArchUnit 1.3.0 | `build.gradle.kts:56-58`, `src/app/build.gradle.kts:39-52`, `src/testing/arch-tests/build.gradle.kts:12` |
| Линт/формат (объявлены) | detekt 1.23.8 и ktlint-плагин 13.1.0 — **только на корне** | `build.gradle.kts:5-6`, `:83-92`, `gradle.properties:9-10` |

Отклонения, которые важны для дизайна:
- JDK 21 — предыдущий LTS; профиль требует текущий LTS. Решение за стадией design.
- `jackson-module-kotlin:latest` — плавающая версия в четырёх модулях (`build.gradle.kts:50`, `src/infrastructure/build.gradle.kts:27`, `src/receiver/build.gradle.kts`, `src/vk-facade/build.gradle.kts`); сборка невоспроизводима по этой зависимости.
- `quarkus-bom:3.34.2` захардкожен в модулях, хотя есть property в `gradle.properties:7` — две точки изменения версии.
- `quarkus-flyway` объявлен (`build.gradle.kts:33`), но Flyway нигде не сконфигурирован; миграции — Liquibase (`src/app/src/main/resources/application.yml:28-30`).
- `quarkus-jacoco` объявлен (`build.gradle.kts:35`), но ни одного jacoco-артефакта после тестов не появляется (раздел 5).

## 3. Форма проекта

### 3.1 Модули (11) — что чем владеет

| Модуль | Build-зависимости | Владеет | Ключевые классы |
|---|---|---|---|
| `src:share` | нет (только BOM/CDI/messaging libs) | домен: `Message`, `ResponseMessage`, `Summary`, VO, `Event`, базовые `Port`/`Command`/`OutputPort`, исходящие порты persistence/vk | `share/domain/model/Message.kt`, `share/domain/Event.kt:3-10`, `share/command/Command.kt:3-5`, `share/port/output/OutputPort.kt` |
| `src:receiver` | share | вход VK webhook, DMZ-маппинг, публикация `MESSAGE_NEW` | `VkMediatorRouterImpl.kt:16-40`, `VkMediatorRouterSecurityDecorator.kt:11-29`, `ReceiveMessageUsecase.kt:11-37`, `PublishVkEventCommandImpl.kt:10-24` |
| `src:persistence` | share, jOOQ, Liquibase | хранение сообщений/профилей/саммари, REST-API запросов, потребление `MESSAGE_NEW`, синхронный вызов vk-facade за профилями | `MessageNewEventProcessor.kt:12-23`, `SaveMessageUsecase.kt:20-55`, `PersistenceDataController.kt:21+`, `JooqMessageRepository.kt`, `JooqConfig.kt:11-22` |
| `src:processor` | ai, share | решение «нужен ли ответ» и публикация `MESSAGE_REQUIRE_ANSWER` | `MessageReceivedEventProcessor.kt:12-23`, `MessageReceivedUsecaseInput.kt:14-45`, `IsRequireAnswerCommand.kt:10-34` |
| `src:ai` | share, persistence | ответы AI, саммаризация, OCR, Vane-инструменты, потребление `MESSAGE_REQUIRE_ANSWER`/`SUMMARY_REQUESTED`, публикация `ANSWER_MESSAGE`/`SUMMARY_READY` | `MessageRequireAnswerEventProcessor.kt`, `SummaryRequestedEventProcessor.kt`, `GenerateAnswerUsecase.kt:30`, `SummarizationAiAdapter.kt`, `VaneSearchToolAdapter.kt:13-28` |
| `src:vk-facade` | share | выход в VK API, профили, REST-эндпоинты `/api/profiles/*`, потребление `ANSWER_MESSAGE`/`SUMMARY_READY` | `SendMessageEventProcessor.kt:22`, `SummaryReadyEventProcessor.kt:55`, `VkFacadeController.kt:13-36`, `VkClient.kt`, `SendVkMessageCommandImpl.kt`, `MessageSendUsecase.kt` |
| `src:infrastructure` | share | RabbitMQ-диспетчер и retry/DLQ, публикация событий, logging-декораторы, Jackson-конфиг | `EventDispatcher.kt:19-112`, `PublishEventOutputAdapter.kt:14-33`, `CommandLoggingDecorator.kt`, `UsecaseLoggingDecorator.kt`, `MapperConfig.kt`, `DlqConfig.kt` |
| `src:app` | все | Quarkus-точка входа, runtime-конфиг, интеграционные тесты | `Application.kt:9-14`, `src/app/src/main/resources/application.yml`, `VkWebhookIntegrationTest.kt` |
| `src:testing:test-fixtures` | share, vk-facade | fake objects для тестов | `FakeVkClient.kt`, `FakeVoProvider.kt`, `FakePublishEventOutputPort.kt` |
| `src:testing:test-common` | нет | тестовые ресурсы (`application.yaml`) | `src/main/resources/application.yaml` |
| `src:testing:arch-tests` | все бизнес-модули | ArchUnit-тесты зависимостей | `ModuleDependenciesTest.kt`, `GradleDependenciesTest.kt` |

### 3.2 Точка входа и деплой

- Приложение: `@QuarkusMain` в `src/app/src/main/kotlin/com/simarel/vkbot/Application.kt:9-14`.
- HTTP-входы: `POST /vk/callback` (`VkMediatorRouterImpl.kt:16-40`), `GET /api/profiles/users|groups/batch` (`VkFacadeController.kt:13-36`), REST `/persistence/*` (`PersistenceDataController.kt:21+`).
- MQ-вход: `@Incoming("all-events-queue")` (`EventDispatcher.kt:63-64`), маршрутизация по `EventProcessor.event()` (`EventDispatcher.kt:24`).
- **Два Quarkus-деплоя.** Корневой `build.gradle.kts:1-7` применяет `io.quarkus`, и `src/app/build.gradle.kts:1-5` тоже. У обоих есть `build/quarkus-app/quarkus-run.jar` (корень `<root>/build/quarkus-app/`, модуль `src/app/build/quarkus-app/`). При этом в корневом `build/quarkus-app/app/` нет ни одного класса, а в `src/app/build/quarkus-app/app/` лежит `app-1.0.0-SNAPSHOT.jar`. `./gradlew quarkusDev --dry-run` запускает оба: `:quarkusDev` и `:src:app:quarkusDev`. Dockerfile'ы расходятся: JVM берёт `src/app/build/quarkus-app/` (`Dockerfile.jvm:87-90`), native — корневой `build/*-runner` (`Dockerfile.native:24`). Канонический деплой не зафиксирован ни в коде, ни в документах.
- Композиционный корень размазан по CDI-аннотациям; явные producers есть только в `receiver/config/UsecaseConfiguration.kt`, `persistence/config/JooqConfig.kt`, `infrastructure/config/{DlqConfig,MapperConfig}.kt`.

### 3.3 Направление зависимостей и межмодульные контракты

- На уровне импортов классы бизнес-модулей зависят только от `share` (проверено `grep '^import com.simarel.vkbot'` по `src/*/src/main`). На уровне build есть лишние рёбра: `processor → ai` (`src/processor/build.gradle.kts:13`), `ai → persistence` (`src/ai/build.gradle.kts:15`), классами они не используются.
- Между модулями два канала: события RabbitMQ (основной поток) и **синхронный REST**: `ai → persistence` (`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/persistence/PersistenceService.kt:18-19`, `configKey = "persistence"`) и `persistence → vk-facade` (`src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/client/vkfacade/VkFacadeService.kt:12-13`, `configKey = "vk-facade"`). Оба URL — `http://localhost:8080` (`src/app/src/main/resources/application.yml:47-52`), то есть HTTP-петля внутри одного процесса.
- Контракты дублированы, а не общие: интерфейс `PersistenceService` с десятью методами существует и на сервере (`src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceService.kt:18-90`), и копией на клиенте (`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/persistence/PersistenceService.kt:18-90`); `CreatePendingSummaryRequest`/`CompleteSummaryRequest` продублированы в обоих файлах. Изменение одной стороны не ломает сборку другой.
- Архитектурные тесты: `ModuleDependenciesTest` — 7 правил уровня «пакет модуля не зависит от пакетов других модулей» (`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/ModuleDependenciesTest.kt:47-124`). Правило для `app` использует пакет `com.simarel.vkbot.app..` (`:24`), а классы `app` лежат в `com.simarel.vkbot` (`Application.kt:1`) — `app` не проверяется. `GradleDependenciesTest` вакуумен: `findBuildGradleKtsFiles()` отбрасывает все файлы фильтром `!it.path.contains("build")` (`GradleDependenciesTest.kt:57-66`), а каждый путь `build.gradle.kts` содержит «build», поэтому список всегда пуст и тест не может упасть.
- Контракты «adapter → port.in → usecase → command → port.out → adapter.out» и «единственный метод у output-порта» из `AGENTS.md:55-121` не проверяются ни одним тестом или линтером.

### 3.4 Сгенерированное против ручного

- Генерированного кода в репозитории нет: каталога `.../persistence/generated` не существует, `generateJooq` отключён без `JOOQ_JDBC_URL` (`src/persistence/build.gradle.kts:129`). Таблицы описаны вручную: `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/domain/entity/Tables.kt:8-18`.
- Liquibase-чейнджлоги написаны вручную (`db/changelog/changes/001..004`).
- REST-контракты не генерируются из схемы; DTO — это доменные модели `share` плюс дублированные data class'ы запросов (см. 3.3).
- Конфиг тоже ручной и продублирован: `src/app/src/main/resources/application.yml` и `src/infrastructure/src/main/resources/application.yml` содержат пересекающиеся `vk.*`/`quarkus.*` с разными значениями (`version: 5.199` в app `:5` против `5.131` в infrastructure `:5`).

### 3.5 Границы: факты без проектных решений

- Домен `share` чист от фреймворков: в `src/share/src/main/kotlin` нет импортов вне `com.simarel|kotlin|java` (grep).
- Но `src/persistence/.../domain/entity/Tables.kt:3-4` импортирует `org.jooq` — тип БД-клиента в пакете `domain` модуля persistence.
- Каркас протекает в «бизнес»-слой: `@ApplicationScoped` на use case (`MessageReceivedUsecaseInput.kt:14-18`), `@ConfigProperty` внутри команды (`IsRequireAnswerCommand.kt:17-20`), JAX-RS-аннотации на интерфейсе в `adapter/input` (`PersistenceService.kt:18-42`), `jakarta.json.JsonObject` на входном адаптере (`VkMediatorRouterImpl.kt:26`).
- Мёртвая/неподключённая конфигурация: `quarkus.jooq.dialect` (`src/app/src/main/resources/application.yml:26-27`) при отсутствии расширения `quarkus-jooq` в build-файлах; `quarkus.hibernate-orm` (`src/infrastructure/src/main/resources/application.yml:19-21`) при отсутствии Hibernate; `quarkus-flyway` (`build.gradle.kts:33`) при Liquibase-миграциях.
- Конфиг Vane есть только в неиспользуемой копии `infrastructure` (`src/infrastructure/src/main/resources/application.yml:46-50`) и в тестовом профиле (`src/app/src/test/resources/application-integration-test.yaml:94`); в production-конфиге `src/app/src/main/resources/application.yml:39-52` блока `vane` нет, хотя клиент объявлен как `configKey = "vane"` (`VaneSearchClient.kt:11`). Это риск запуска, а не доказанный отказ: аудит его не воспроизводил.

## 4. Документы: проверка против кода

Статус: **ACTUAL** — проверенные утверждения совпадают с кодом; **UNACTUAL** — есть противоречия (перечислены); **N/A** — документ не найден.

### 4.1 AGENTS.md — частично UNACTUAL

| Утверждение | Код | Статус |
|---|---|---|
| «Main modules: receiver, persistence, processor, ai, vkFacade, infrastructure, share» (`:9`) | 11 модулей; нет `app`, `testing/*`; имя каталога `vk-facade`, пакет `vkFacade` (`settings.gradle.kts:22-31`) | UNACTUAL |
| Fake Objects «located in `objectProvider/fake/`» (`:8`) | фактические fake-объекты: `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/` | UNACTUAL |
| `./gradlew quarkusDev` для dev (`:192`) | запускает и корневой пустой `:quarkusDev`, и `:src:app:quarkusDev`; README советует `:src:app:quarkusDev` (`README.md:104`) | UNACTUAL/неоднозначно |
| `./gradlew check` = «tests + linting» (`:196`), `./gradlew detekt` = lint (`:197`) | detekt-плагин только в корне, `:detekt NO-SOURCE`; lint модулей нет | UNACTUAL |
| KtLint: «No trailing commas on call sites» (`:180`) | `.editorconfig:12-13` разрешает trailing comma на call site, и код их использует | UNACTUAL |
| Поток данных `MESSAGE_NEW → persistence → MESSAGE_RECEIVED → processor → MESSAGE_REQUIRE_ANSWER → ai → ANSWER_MESSAGE → vkFacade` (`:24-29`) | совпадает с кодом (`PublishVkEventCommandImpl.kt:18`, `SaveMessageUsecase.kt:32-48`, `MessageReceivedEventProcessor.kt:22`, `GenerateAnswerUsecase.kt:30`, `SendMessageEventProcessor.kt:22`) | ACTUAL |
| `maxIssues = 0` в detekt-config (`:175`) | `detekt-config.yml:2` | ACTUAL, но гейт не применяется |
| Fake Objects в `src/testing/test-fixtures` (`:238`) | есть | ACTUAL |
| `VaneSearchToolAdapter` с `@Tool`, не команда (`:108-109`) | `VaneSearchToolAdapter.kt:13-28` | ACTUAL |
| `UserAnswerAiService` с `@SystemMessage` (`:266`) | `UserAnswerAiService.kt:17` | ACTUAL |

Дополнительно: AGENTS.md не упоминает `src/app` как точку входа и не фиксирует, какой из двух раннеров канонический; таблица гейтов не отражает, что lint/format/coverage не работают.

### 4.2 README.md — частично UNACTUAL

| Утверждение | Код | Статус |
|---|---|---|
| `./gradlew jacocoTestReport` (`:210`) | задача отсутствует (`exit 1`) | UNACTUAL |
| `/metrics` — Prometheus-метрики (`:163`, `:226-232`) | в зависимостях нет ни одного Prometheus-registry; есть `quarkus-micrometer-opentelemetry` (`build.gradle.kts:39`), кода с `MeterRegistry`/метриками нет | UNACTUAL/не доказано |
| «jOOQ type-safe queries» (`:195`) | jOOQ подключён, но кодогенерация отключена, таблицы вручную (`Tables.kt`) | частично UNACTUAL |
| `VANE_URL`, `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD` — обязательные/поддерживаемые env (`:85-88`) | в `application.yml` эти переменные не читаются; datasource на DevServices (`src/app/src/main/resources/application.yml:17-25`) | UNACTUAL |
| «Liquibase migrations» (`:11`) | Liquibase есть (`persistence/build.gradle.kts:51`) | ACTUAL |
| «OpenTelemetry observability» (`:14`) | `quarkus-otel` + конфиг `otel.*` (`application.yml:31-38`) | ACTUAL |
| «Retry strategies with DLQ» (`:15`) | `EventDispatcher.kt:28-51,90-111`, `application-queues.yml` | ACTUAL |
| «WireMock, ArchUnit» (`:31`) | `src/app/build.gradle.kts:48`, `arch-tests/build.gradle.kts:12` | ACTUAL |
| Модульная структура (`:38-53`) | совпадает с 11 модулями | ACTUAL |
| `./gradlew :src:app:quarkusDev` (`:104`) | задача существует | ACTUAL |

### 4.3 docs/README.md — ACTUAL как индекс

Индекс актуален по ссылкам, кроме отсутствующей ссылки на существующий `docs/architecture/ai-tools.md` (в README.md она есть, `README.md:246`). Требует только дополнения.

### 4.4 docs/architecture/overview.md — UNACTUAL

- События: перечислены `MESSAGE_RECEIVED`, `MESSAGE_REQUIRE_ANSWER`, `SEND_MESSAGE` (`:29`), диаграмма использует `SEND_MESSAGE` (`:73`). В коде `SEND_MESSAGE` нет; есть `MESSAGE_NEW`, `ANSWER_MESSAGE`, `SUMMARY_REQUESTED`, `SUMMARY_READY` (`share/domain/Event.kt:3-10`).
- «Output Adapters: клиенты для VK API, AI сервисов» (`:41`) — VK-клиент и AI-адаптер есть, но AI-вызов идёт через LangChain4j-адаптеры, а не отдельный «AI-клиент»; не критично.
- Утверждение о независимости от БД/фреймворка (`:10-14`) верно только для `share` (см. 3.5); в остальных модулях Quarkus-аннотации в use case и jOOQ в `domain`.
- Остальное (Clean Architecture, event-driven, DMZ, security decorator) подтверждается кодом: `VkMediatorRouterSecurityDecorator.kt:11-29`, `VkMediatorRouterImpl.kt`, `EventDispatcher.kt`.

### 4.5 docs/architecture/modules.md — UNACTUAL

| Утверждение | Код | Статус |
|---|---|---|
| `processor`: `NewMessageEventProcessor`, `NewMessageUsecaseInput`, `MessageRequireAnswerEventProcessor`, `GenerateAnswerCommand` (`:49-52`) | `MessageReceivedEventProcessor.kt`, `MessageReceivedUsecaseInput.kt`, `IsRequireAnswerCommand.kt`; `MessageRequireAnswerEventProcessor` и `GenerateAnswerCommand` — в `ai` | UNACTUAL |
| `ai`: `ChatSummarizationService` (`:72`) | класс не существует; есть `SummarizationAiService`/`SummarizationAiAdapter` | UNACTUAL |
| `persistence`: `JsonbConverter` (`:107`) | класса нет; `forwardedMessages` пишется Jackson-строкой (`SaveMessageUsecase.kt:66-70`) | UNACTUAL |
| `vk-facade` обрабатывает `SEND_MESSAGE` (`:121`, `:129`) | обрабатывает `ANSWER_MESSAGE` (`SendMessageEventProcessor.kt:22`) | UNACTUAL |
| `infrastructure`: `RetryStrategies`, `DlqFinalHandler` (`:146-147`) | классов нет; retry в `EventDispatcher.handleRetry` (`:90-111`), DLQ-конфиг `DlqConfig.kt` | UNACTUAL |
| `share` содержит `adapter/` (`:173`) | каталога `share/.../adapter` нет (только `command`, `domain`, `port`) | UNACTUAL |
| Таблица зависимостей (`:257-281`) | реальные build-зависимости: `processor→ai`, `ai→persistence`; `receiver→infrastructure` только testImplementation; REST-вызовы `ai→persistence`, `persistence→vk-facade` вообще не отражены | UNACTUAL |
| «Модули взаимодействуют только через события; нет прямых зависимостей между бизнес-модулями» (`:325-328`) | `ai` синхронно вызывает `persistence` по REST, `persistence` — `vk-facade` (3.3) | UNACTUAL |
| Базовые паттерны, DMZ, UseCase/Command, `@Decorator` (`:32-39`, `:287-302`) | подтверждаются кодом | ACTUAL |

### 4.6 docs/architecture/patterns.md — UNACTUAL

- Примеры портов/классов не существуют: `MessageNewInputPort`/`NewMessageUsecaseInput` (`:13-25`), `MessageAnswerTextGenerateCommandImpl`/`AiChatbotAnswerMessageOutputPort` (`:48-58`). Реальные: `MessageReceivedInputPort`, `GenerateAnswerCommand`.
- Enum событий с `SEND_MESSAGE` (`:151-155`) — в коде нет (`Event.kt:3-10`).
- «Event Sourcing / CQRS Pattern» (`:145-147`) — не реализовано: события не сохраняются, event store отсутствует, CQRS-разделения нет.
- DMZ: `PublishVkEventCommand.mapVkEventToEvent()` (`:203`) — метода нет; маппинг VK JSON → домен делает `MessageMapper` (`receiver/.../mapper/MessageMapper.kt`), маршрутизация типов — `ReceiveMessageUsecase.kt:16-35`.
- Factory `Message.of(...)` (`:220-238`) — сигнатура в коде с nullable-параметрами и `forwardedMessages` (`Message.kt:21-38`).
- «Strategy для AI провайдеров», «Repository для разных БД», «Chain of Responsibility» (`:262-273`) — в коде нет соответствующих реализаций (есть Command/Decorator/Ports).
- Decorator-логирование (`:71-88`) и VK-security-декоратор (`:91-110`) — подтверждаются (`CommandLoggingDecorator.kt`, `VkMediatorRouterSecurityDecorator.kt`).

### 4.7 docs/architecture/data-flow.md — UNACTUAL

- `SEND_MESSAGE` в диаграмме, map, sequence (`:26`, `:28`, `:117`, `:183`, `:295`, `:297`); в коде `ANSWER_MESSAGE` (`Event.kt:7`).
- `MessageNewEventProcessor` назван обработчиком `MESSAGE_RECEIVED` и `NewMessageUsecaseInput` логикой сохранения (`:14-19`, `:115`, `:127-150`) — на деле `MESSAGE_NEW` обрабатывает `persistence/MessageNewEventProcessor.kt:22`, а `MESSAGE_RECEIVED` — `processor/MessageReceivedEventProcessor.kt:22`; сохранение выполняет `SaveMessageUsecase` (`persistence`).
- `jooq.insertInto(MESSAGES)...set(MESSAGES.METADATA, jsonbConverter...)` (`:160`) — полей `METADATA`/`JsonbConverter` нет; есть `FORWARDED_MESSAGES` (`Tables.kt:18`).
- `Tracer`-инъекция в `EventDispatcher` (`:349-352`) и `MDC.put` (`:370-371`) — в коде нет `Tracer`/`MDC`/`OpenTelemetry` (grep пуст); `@RunOnVirtualThread` есть (`EventDispatcher.kt:63-64`).
- `MeterRegistry.counter(...)` (`:380`) — кода нет.
- Бенчмарки (`:399`) — не подтверждаются ничем в репозитории.
- Retry-секция и DLQ-лестница (`:97-110`) — подтверждаются (`application.yml`, `application-queues.yml`, `EventDispatcher.kt:90-111`).
- `@RunOnVirtualThread` и `@Incoming` — подтверждаются (`EventDispatcher.kt:63-64`).

### 4.8 docs/architecture/ai-tools.md — UNACTUAL

- `@Tool` на `VaneSearchCommandImpl` (`:20-23`, `:118-128`) — `AGENTS.md:108-109` это запрещает, и в коде `@Tool` живут в `VaneSearchToolAdapter.kt:13-28`; `VaneSearchCommand` — обычный `Command` (`command/vane/VaneSearchCommand.kt:11-21`).
- Имена инструментов `searchWeb`/`searchAcademic`/`searchDiscussions` (`:13-15`) — в коде `search_web`/`search_academic`/`search_discussions` (`VaneSearchToolAdapter.kt:14,20,26`).
- Регистрация `@RegisterAiService(tools = [VaneSearchCommandImpl::class, ...])` (`:130`) — в коде `tools = [VaneSearchToolAdapter::class]` (`UserAnswerAiService.kt:15`).
- `export VANE_URL=https://your-vane.com/api/providers` (`:98`) — base URL клиента не включает `/api/providers`: клиент добавляет `/api` и `/providers` (`VaneSearchClient.kt:12-22`).
- Остальное (Vane/Perplexica, `/api/providers`, выбор моделей, таймауты) — в основном соответствует `VaneSearchClient.kt`/`VaneSearchOutputAdapter.kt`.

### 4.9 docs/development/testing.md — UNACTUAL

- Структура fake-объектов (`:48-67`): в реальности `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/{adapter/input,adapter/output/{mq,vk},command,command/vkFacade,domain,objectProvider,port/output/vk}`; перечисленных `adapter/output/ai/FakeAiChatbotAnswerMessageOutputPort.kt`, `command/processor/FakeMessageAnswerTextGenerateCommand.kt`, `service/FakeUserAnswerAiService.kt` нет.
- Классы `MessageAnswerTextGenerateCommand`, `AiChatbotAnswerMessageOutputPort`, `FakeMessageAnswerTextGenerateCommandProvider` (`:75-82`, `:162-178`) — таких нет; реальные `GenerateAnswerCommand`, `FakePublishEventCommand`, `FakeCommand`.
- `FakeVkClient` показан с двумя параметрами и `sendMessage(peerId, message, rand)` (`:93-104`); реальный класс принимает три DTO и переопределяет `sendMessage(peerId, message, rand, forwardMessages)` (`FakeVkClient.kt:9-36`).
- `FakeVoProvider` показан с `createDate/createFromId/createMessageText` (`:132-137`); реальный — `createDate/createHumanFromId/createGroupFromId/createGroupChatFromId/createGroupId/createPeerId/createConversationMessageId/createMessageText/createMessage` (`FakeVoProvider.kt:11-40`).
- Философия Fake Objects, `ConcurrentLinkedQueue`, провайдеры — подтверждаются.

### 4.10 docs/development/configuration.md — UNACTUAL

- Env-таблица `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST` (`:45-48`) — в конфигах не читаются (grep по `src/**/resources`); datasource — DevServices (`application.yml:17-20`).
- RabbitMQ `username/password/host/port` (`:91-94`) — в `application.yml` нет; только devservices (`:53-55`).
- Таймауты `vk` rest-client (`:142-143`) — в `application.yml:43-46` их нет (есть только у `vane` в `infrastructure`, `:49-50`).
- Логирование `quarkus.logging.json`, `quarkus.log.level/category` (`:153-166`) — в конфигах нет.
- OTel exporter `endpoint`/`propagators` (`:184-193`) — в `application.yml:31-38` только `logs/metrics/traces` без exporter.
- Prometheus `quarkus.micrometer` (`:196-204`) — в конфигах нет.
- Профили `%dev`, `%test`, `%prod` (`:208-250`) — в `src/main/resources` профилей нет; единственный `%test` — `src/app/src/test/resources/application.yml:1-8`.
- Health-checks `quarkus.health`/`smallrye-health.check.*` (`:252-264`) — в конфигах нет; `quarkus-smallrye-health` подключён (`build.gradle.kts:45`).
- Dockerfile-сниппет `gradle:8-jdk21` (`:273-287`) — реальный `Dockerfile.jvm:81-97` использует готовый `src/app/build/quarkus-app` и UBI-образ.
- jOOQ codegen (`:336-348`): команда `:src:persistence:generateJooq` существует (`./gradlew quarkusDev --dry-run` показывает `:src:persistence:generateJooq SKIPPED`), ручные table constants — верно.
- VK API version 5.131 (`:14`) — в app-конфиге 5.199 (`application.yml:5`), в infra-копии 5.131; актуальная копия — app.

### 4.11 docs/development/deployment.md — частично UNACTUAL

- JVM-сборка/запуск (`:7-31`) — путь `Dockerfile.jvm` существует, но env `DATABASE_URL/DB_USERNAME/DB_PASSWORD/RABBITMQ_HOST` (`:26-29`) не читаются конфигом.
- Native (`:33-52`): команда `./gradlew build -Dquarkus.native.enabled=true...` собирает и корневой раннер, и `src/app`; `Dockerfile.native:24` копирует корневой `build/*-runner`, у которого нет app-классов. Источник native-артефакта не совпадает с JVM-источником.
- JVM tuning (`:54-70`) — не проверялось (нет запуска контейнера).

### 4.12 docs/adr/ и docs/capabilities/

Каталогов нет; противоречащих ADR с историей нет, преемников назначать нечему. ADR-набор создаётся с нуля из кода и решений стадии design; карта возможностей — стадией capabilities (аналитик).

## 5. Гейты: фактические прогоны

Все команды выполнены из корня, последовательно, с фиксацией exit code.

| # | Команда (как объявлена) | Exit | Что реально произошло |
|---|---|---|---|
| 1 | `./gradlew build` | **0** | `BUILD SUCCESSFUL in 1m 21s`, 77 задач; собраны jar'ы модулей и **два** `quarkus-app` (корень и `src/app`) |
| 2 | `./gradlew test` | **0** | `BUILD SUCCESSFUL in 47s`; 16 XML-файлов результатов, **41 тест, 0 failures, 0 errors, 0 skipped** |
| 3 | `./gradlew detekt` | 0 | `> Task :detekt NO-SOURCE` — исходников модулей detekt не видит (плагин только на корне, `build.gradle.kts:5`, `:83-92`) |
| 4 | `./gradlew ktlintCheck` | 0 | `runKtlintCheckOverMainSourceSet NO-SOURCE`, `ktlintMainSourceSetCheck SKIPPED`, `ktlintTestSourceSetCheck SKIPPED` — проверяются только sourceSets корня, которых нет |
| 5 | `./gradlew jacocoTestReport` | **1** | `Task 'jacocoTestReport' not found in root project 'vkbot' and its subprojects`; jacoco-артефактов (`*.exec`, отчётов) после тестов нет |
| 6 | `./gradlew check` (объявлена в AGENTS.md) | 0 | 69 задач up-to-date; внутри снова `:detekt NO-SOURCE` и ktlint-задачи `SKIPPED` |
| — | `./gradlew ktlintFormat` (объявлена в AGENTS.md) | не запускалась | это форматтер, а не гейт; запуск изменил бы исходники, что запрещено стадией |
| — | Docker-сборки | не запускались | нет подтверждения сборки образов в этом аудите |

Детализация тестов по модулям (из `src/*/build/test-results/test/*.xml`): app — 7, receiver — 9, processor — 2, share — 8, vk-facade — 5, infrastructure — 2, arch-tests — 8; **ai — 0, persistence — 0**. `app`-тесты включают `VkWebhookIntegrationTest` (6 тестов) и используют DevServices/WireMock; локально прошли, в CI не проверялись (конфиг CI есть, прогона CI в аудите нет).

Архитектурные тесты: `ModuleDependenciesTest` — 7 тестов, все зелёные; `GradleDependenciesTest` — 1 тест, зелёный, но вакуумный (3.3).

CI: `.github/workflows/build-and-test.yml:25-29` — `./gradlew build --scan`, затем `./gradlew test`. Линт/формат/покрытие в CI отсутствуют; шага docker-build нет. Как конфигурация CI присутствует, как фактический прогон — не проверен.

## 6. Рабочее дерево

```
 M src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapter.kt
 M src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt
 M src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/usecase/SaveMessageUsecase.kt
?? .ast-index/
```

Три изменения — предсуществующий WIP (в `SaveMessageUsecase` payload `SUMMARY_REQUESTED` переведён с VO на `Long`; в AI — правки логирования). Аудит их не трогал; артефакты сборки (`build/`, `.gradle/`) gitignore'нуты. Исходники после прогона гейтов не изменились.

## 7. Вердикт по критериям

| Критерий | Вердикт | Доказательство |
|---|---|---|
| Build | **present** | `./gradlew build` → exit 0, `BUILD SUCCESSFUL in 1m 21s`; но собирает два раннера, канонический не зафиксирован |
| Tests | **present, с пробелами** | `./gradlew test` → exit 0; 41 тест, 0 падений; в `ai` и `persistence` тестов нет; интеграционные тесты зависят от Docker/DevServices |
| Lint | **missing** | `:detekt NO-SOURCE`; detekt не применён к модулям; `autoCorrect = true` (`build.gradle.kts:87`) сделал бы его форматтером, а не гейтом |
| Format check | **missing** | `ktlintCheck` проверяет только пустые sourceSets корня; модули не проверяются |
| Coverage | **missing** | задачи `jacocoTestReport` не существует (exit 1); отчётов и `.exec` нет; порога нет |
| Architecture tests | **present, слабые** | 7 правил module-level работают; `GradleDependenciesTest` не может упасть; `app` не покрыт; контракты портов/команд и слои внутри модулей не проверяются |
| CI | **present, неполный** | `.github/workflows/build-and-test.yml:25-29` — только build + test; lint/format/coverage/docker отсутствуют; прогон CI не подтверждён |
| ADR | **missing** | `docs/adr/` отсутствует |
| AGENTS.md | **present, UNACTUAL** | команды и пути расходятся с кодом (4.1); не описывает `src/app`, два раннера, реальные гейты |
| docs/architecture | **present, UNACTUAL** | все 5 файлов содержат противоречия коду (4.4–4.8) |
| docs/development | **present, UNACTUAL** | все 3 файла содержат противоречия коду (4.9–4.11) |
| docs/capabilities | **missing** | каталога нет; создаётся стадией capabilities |

### Документы, которые надо переписать (из кода)

1. `AGENTS.md` — карта модулей (11, включая `app` и `testing/*`), фактически работающие команды (`:src:app:quarkusDev`, без вакуумных detekt/ktlint/coverage), таблица гейтов, пути fake-объектов, правило trailing comma.
2. `README.md` — убрать `jacocoTestReport`, Prometheus/`/metrics`, неподдерживаемые env (`DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`), поправить утверждение о jOOQ-кодогенерации, заменить заглушку лицензии.
3. `docs/architecture/overview.md` — реальный enum событий (`MESSAGE_NEW`, `MESSAGE_RECEIVED`, `MESSAGE_REQUIRE_ANSWER`, `ANSWER_MESSAGE`, `SUMMARY_REQUESTED`, `SUMMARY_READY`), реальная схема потока.
4. `docs/architecture/modules.md` — реальные классы модулей, реальные build- и runtime-зависимости, REST-канал между модулями, отсутствие `adapter` в `share`.
5. `docs/architecture/patterns.md` — убрать несуществующие классы, `SEND_MESSAGE`, «Event Sourcing/CQRS», нереализованные Strategy/Chain; оставить реальные Ports & Adapters, Command, Decorator, VO, Factory, DMZ.
6. `docs/architecture/data-flow.md` — `ANSWER_MESSAGE` вместо `SEND_MESSAGE`, реальные процессоры (`MessageReceivedEventProcessor` в processor, `MessageNewEventProcessor` в persistence), `FORWARDED_MESSAGES` вместо `METADATA`, убрать `Tracer`/`MeterRegistry`/бенчмарки без кода.
7. `docs/architecture/ai-tools.md` — `@Tool` в `VaneSearchToolAdapter`, реальные имена инструментов, `tools = [VaneSearchToolAdapter::class]`, корректный base URL Vane.
8. `docs/development/testing.md` — реальные пути/классы fake-объектов и реальная сигнатура `FakeVkClient`/`FakeVoProvider`.
9. `docs/development/configuration.md` — переписать из `src/app/src/main/resources/application.yml` и `application-queues.yml`: реальные ключи, отсутствие профилей `%dev/%prod`, реальные env, отсутствие Prometheus/health-ключей, актуальная версия VK API (5.199).
10. `docs/development/deployment.md` — реальные env, источник артефакта для native (расхождение `Dockerfile.native` с `src/app`), без непроверенных JVM/бенчмарк-утверждений.
11. `docs/README.md` — добавить ссылку на `ai-tools.md`; остальное как индекс.
12. Создать `docs/adr/` (решения из кода и стадии design) и `docs/capabilities/` (стадия capabilities по контракту аналитика).

## 8. Расхождения со сканом в карточке

- Скан перечислил 8 модулей (`ai, app, infrastructure, persistence, processor, share, testing/test-common, testing/test-fixtures`); в `settings.gradle.kts:22-31` их 11 — пропущены `src:receiver`, `src:vk-facade`, `src:testing:arch-tests`.
- Скан назвал `coverage=./gradlew jacocoTestReport`; такой задачи нет (exit 1).
- Скан назвал `lint=./gradlew detekt` и `format=./gradlew ktlintCheck`; обе команды завершаются успешно, но не проверяют код модулей (`NO-SOURCE`/`SKIPPED`).
- Количество документов совпало: architecture 5, development 3, adr 0, capabilities 0.
- Вердикт сканера «partial» подтверждается, но по другим причинам: отсутствуют не только adr/capabilities, но и реальные lint/format/coverage/CI-гейты, а документы устарели.

## 9. Вопросы, которые аудит оставляет стадии design

1. Какой раннер канонический — `src/app` или корень? Один деплой или два?
2. Оставить ли синхронные REST-вызовы между модулями внутри одного деплоя или заменить in-process вызовами/контрактом?
3. Где настоящий composition root и как его сделать единственным?
4. Как сделать lint/format/coverage реальными гейтами, которые валят сборку, и что с `GradleDependenciesTest`?
5. Как избавиться от дублирования конфигов (`app` vs `infrastructure`) и мёртвых ключей (`quarkus.jooq`, `hibernate-orm`, Flyway)?
6. Какую версию JDK/зависимостей зафиксировать (JDK 21 vs текущий LTS, `latest` у Jackson, дублирование BOM)?
7. Какие ADR выпустить под текущие решения, чтобы история не потерялась.

**Границы аудита:** проект не менялся; `audit.md` — единственный артефакт стадии.
