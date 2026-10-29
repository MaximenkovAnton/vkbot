# Дизайн проекта VK Bot — конституция, границы, гейты

**Стадия:** design (spec), board task `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`, задача «Подготовка проекта: vk».
**Вход:** `audit.md` этой же задачи (342 строки) + повторная проверка кода под этот дизайн. Все утверждения ниже — с `file:line` или с прогоном.
**Окружение:** macOS, JDK 21.0.5 (Temurin), Gradle wrapper 9.3.0, корень `/Users/asmaksime1/IdeaProjects/test/vk`.
**Границы стадии:** ни один файл проекта не изменён; артефакт стадии — только этот файл. Специалист следующей стадии реализует перечисленное ниже, не принимая проектных решений.

## 0. Ответы человека и рамка

Один раунд вопросов, два ответа (2026-10-28):

| Вопрос | Ответ | Следствие |
|---|---|---|
| JDK 21 (стоит локально, в CI, toolchain всех модулей) или текущий LTS 25? | **Оставить JDK 21** | Версии JDK/сборки не меняются; апгрейд — отдельная задача с условием, ADR 0001. |
| Loopback-REST между модулями оставить или заменить in-process портами? | **Заменить на in-process порты** | Удаляются два HTTP-контура внутри деплоя и дублированные контракты; порты уже лежат в `share`. |

Не пересматриваем, потому что код уже отвечает: Kotlin, Gradle с Kotlin DSL, Quarkus, PostgreSQL + Liquibase, RabbitMQ, LangChain4j, 11 модулей, fake-объекты, event-driven поток (`settings.gradle.kts:22-31`, `gradle.properties:3-10`, `src/persistence/build.gradle.kts:45,51`, `src/infrastructure/build.gradle.kts:19-21`, `src/ai/build.gradle.kts:34`).

**DoD карточки, который закрывает этот проход:** зелёная сборка; тесты одной командой с отчётом покрытия и порогом; линтер валит сборку; CI запускает build + lint + test + coverage; AGENTS.md с выполненными командами и картой модулей; `docs/architecture` и `docs/development` описывают код со ссылками; `docs/capabilities` — строка на каждую возможность и чек, падающий на мёртвый путь/ADR id; противоречащие ADR получают преемника (сейчас ADR-набора нет — начинаем с 0001).

## 1. Профиль проекта (решения и версии)

Форма: **backend service / API**, гексагональный монолит в одном деплое: горизонтальные слои внутри модулей, поведение за портами, фреймворк на краю.

Версии фиксируются разрешённые сборкой; ничего не апгрейдим в этом проходе:

| Слой | Разрешено | Источник истины |
|---|---|---|
| Язык | Kotlin 2.3.0 | `settings.gradle.kts:14` |
| Runtime | JDK 21 (toolchain 21; CI temurin 21) | `build.gradle.kts:65-81`, `src/app/build.gradle.kts:58-65`, `.github/workflows/build-and-test.yml:16-20`, `java -version` = 21.0.5 |
| Сборщик | Gradle 9.3.0 wrapper, Kotlin DSL, foojay 0.8.0 | `gradle/wrapper/gradle-wrapper.properties:3`, `settings.gradle.kts:18` |
| Фреймворк | Quarkus 3.34.2 (`quarkus-bom`) | `gradle.properties:4,7` |
| AI | LangChain4j 1.8.4 (`quarkus-langchain4j-ollama`) | `gradle.properties:8`, `build.gradle.kts:46` |
| Хранилище | PostgreSQL, Agroal, Liquibase (Flyway не используется) | `src/persistence/build.gradle.kts:48-51`, `src/app/src/main/resources/application.yml:28-30` |
| SQL | jOOQ 3.19.10, кодогенерация выключена, таблицы вручную | `src/persistence/build.gradle.kts:45,94-130` |
| Брокер | RabbitMQ, SmallRye Reactive Messaging | `src/infrastructure/build.gradle.kts:19-21` |
| Тесты | JUnit 5 (версия из BOM), ArchUnit 1.3.0, WireMock 3.9.1, Awaitility 4.2.0, Quarkus Test, RestAssured | `build.gradle.kts:56-58`, `src/app/build.gradle.kts:40-52`, `src/testing/arch-tests/build.gradle.kts:11` |
| Линт | detekt 1.23.8 (`maxIssues: 0`), ktlint Gradle plugin 13.1.0 | `build.gradle.kts:5-6`, `detekt-config.yml:1-2`, `gradle.properties:9-10` |
| Покрытие | Jacoco (Gradle plugin); `quarkus-jacoco` не даёт артефактов и удаляется | `build.gradle.kts:35`, audit §5 (прогон 5 — exit 1) |

Решения по версиям:

- Один источник версии Quarkus: `enforcedPlatform` объявляется один раз в корневом конвенционном блоке из `gradle.properties:5-7`; строки `enforcedPlatform("io.quarkus.platform:quarkus-bom:3.34.2")` удаляются из 10 модулей (например `src/ai/build.gradle.kts:18`, `src/app/build.gradle.kts:25`, `src/persistence/build.gradle.kts:24`). Второй точки изменения версии не остаётся.
- `com.fasterxml.jackson.module:jackson-module-kotlin:latest` → версия из BOM (без `:latest`) в `build.gradle.kts:50`, `src/infrastructure/build.gradle.kts:27`, `src/receiver/build.gradle.kts:41`, `src/vk-facade/build.gradle.kts:31`: сейчас сборка невоспроизводима.
- Явные версии JUnit (`5.10.3`) убираются (BOM enforced и так их перекрывает); `wiremock`, `awaitility`, `archunit` остаются с версиями — их в BOM нет.
- Version catalog `gradle/libs.versions.toml` в этом проходе **не вводится** (отклонено: тот же эффект даёт централизация в `gradle.properties` + конвенционный блок, а переписывать 11 файлов ради каталога — churn без выгоды для DoD).
- Отклонение от профиля «текущий JDK LTS» осознанное: локально стоят только JDK 21 (`/usr/libexec/java_home -V`), CI на 21, Quarkus 3.34.2 на 25 не проверен. Записывается в ADR 0001 с условием апгрейда.

## 2. Форма: один деплой, один composition root

**Решение:** единственный деплой — `:src:app`. Корневой проект `vkbot` — агрегатор и место конвенций: `base` + `subprojects {}`, без `io.quarkus`, без runtime-зависимостей.

Обоснование: `@QuarkusMain` лежит в `src/app` (`src/app/src/main/kotlin/com/simarel/vkbot/Application.kt:9-14`), `src/app` уже зависит от всех бизнес-модулей (`src/app/build.gradle.kts:16-22`), JVM-Dockerfile уже копирует артефакт `src/app` (`src/docker/Dockerfile.jvm:87-90`).

Что не так сейчас (audit §3.2): корень тоже применяет `io.quarkus` (`build.gradle.kts:3`), собираются два `quarkus-app` (корневой пустой и `src/app`); `quarkusDev` запускает оба (`:quarkusDev` и `:src:app:quarkusDev`); `Dockerfile.native`, `Dockerfile.native-micro`, `Dockerfile.legacy-jar` копируют корневой `build/` (`src/docker/Dockerfile.native:24`, `src/docker/Dockerfile.native-micro:27`, `src/docker/Dockerfile.legacy-jar:86-87`), у которого нет app-классов.

| Вариант | Стоимость | Итог |
|---|---|---|
| A. Оставить два раннера и описать оба | дешево сейчас, дорого всегда: два артефакта, два dev-режима, native/legacy-образы собираются не из того источника | отклонён |
| B. **Один деплой `:src:app`, корень — агрегатор** | правка корневого build-файла + 3 Dockerfile + две команды в доках | **принят**, ADR 0002 |

Следствия, которые реализация обязана доказать:

- `./gradlew build` собирает все 11 модулей; dry-run подтверждает, что `:src:app:build` включает `:src:app:test`, `:src:app:check`, `:src:app:quarkusBuild`.
- артефакт деплоя — только `src/app/build/quarkus-app/quarkus-run.jar`; после `clean` корневого `build/quarkus-app/` нет.
- dev-команда — `./gradlew :src:app:quarkusDev` (README уже это советует, `README.md:104`; AGENTS.md противоречит, `AGENTS.md:192`).
- devservices (PostgreSQL, RabbitMQ, LGTM) — только dev/test; prod-конфигурация задаётся env (§6).

Composition root: **CDI Quarkus + модуль `:src:app`**. Явных `@Produces` на каждый порт нет и не добавляем: связывание идёт `@ApplicationScoped` на реализациях, а точка сборки — deployable `:src:app`. Альтернатива (все бины в `config`-классах, аннотации убрать из use case) — десятки файлов без функциональной выгоды; правило «use case зависит только от портов `share`» уже выполняется. Записывается в ADR 0003.

**Бесплатный гейт проводки:** ArC валидирует injection point на этапе сборки Quarkus. После замены REST на in-process порты `./gradlew build` упадёт, если реализация порта не найдена или найдена дважды.

## 3. Модули, владение, направление зависимостей

Оставляем 11 модулей (`settings.gradle.kts:22-31`). Горизонтальные слои — пакеты внутри модуля, вертикали (фичи) — модули. Это не «модуль на фичу-деплой»: деплой один, а граница модуля бесплатна на Gradle и делает неправильный импорт ошибкой компиляции.

### 3.1 Карта модулей

| Модуль | Владеет | Публичные входы (файл:строка) | Production-зависимости |
|---|---|---|---|
| `src:share` | домен: `Message`, `StoredMessage`, `Summary`, `VkUserProfile`/`VkGroupProfile`, VO (`@JvmInline value class`), `Event`, `Command`/`OutputPort`/`InputPort`, порты persistence/vk, payload'ы событий | только контракты | ничего |
| `src:receiver` | вход VK Callback API, DMZ-парсинг, публикация `MESSAGE_NEW` | `POST /vk/callback` (`receiver/adapter/input/vk/VkMediatorRouterImpl.kt:16-40`), `ReceiveMessageInputPort` | share |
| `src:persistence` | хранение сообщений/профилей/саммари, схема Liquibase, публичный REST запросов, in-process реализация share-портов persistence | `POST /persistence/*` (`persistence/adapter/input/rest/PersistenceDataController.kt:21-126`), `MessageNewEventProcessor` (`persistence/adapter/input/event/MessageNewEventProcessor.kt:12-22`) | share |
| `src:processor` | решение «нужен ли AI-ответ», публикация `MESSAGE_REQUIRE_ANSWER` | `MessageReceivedEventProcessor` (`processor/adapter/input/event/messageReceived/MessageReceivedEventProcessor.kt:12`) | share |
| `src:ai` | AI-ответ, саммаризация, OCR-команда, Vane-инструменты; потребление `MESSAGE_REQUIRE_ANSWER`/`SUMMARY_REQUESTED`, публикация `ANSWER_MESSAGE`/`SUMMARY_READY` | `SummaryRequestedEventProcessor` (`ai/adapter/input/event/SummaryRequestedEventProcessor.kt:11`), `MessageRequireAnswerEventProcessor` (`.../messageRequireAnswer/MessageRequireAnswerEventProcessor.kt:12`) | share |
| `src:vk-facade` | выход в VK API, профили, публичный REST `/api/profiles/*`, in-process реализация share-портов vk | rest-client `configKey="vk"` (`vkFacade/adapter/output/vk/VkClient.kt:12`), `GET /api/profiles/*` (`vkFacade/adapter/input/rest/VkFacadeController.kt:13-36`) | share |
| `src:infrastructure` | диспетчер RabbitMQ, retry-лестница/DLQ, logging-декораторы, Jackson-конфиг | `@Incoming("all-events-queue")` (`infrastructure/mq/adapter/input/EventDispatcher.kt:63-64`), retry (`:90-111`) | share |
| `src:app` | деплой, runtime-конфиг, composition root, интеграционные тесты | `@QuarkusMain` (`app/.../Application.kt:9-14` после переноса пакета) | все 7 бизнес-модулей |
| `src:testing:test-fixtures` | fake-объекты и провайдеры тестовых данных | `testfixtures/{adapter,command,domain,objectProvider,port}` | share, vk-facade (только тесты) |
| `src:testing:test-common` | общий тестовый `application.yaml` | ресурсы | ничего |
| `src:testing:arch-tests` | ArchUnit-тесты границ и build-графа | `ModuleDependenciesTest`, `LayerDependenciesTest`, `GradleDependenciesTest` | все бизнес-модули + app (только тесты) |

### 3.2 Разрешённый граф production-зависимостей

```
share            -> (нет)
receiver         -> share
processor        -> share
ai               -> share
persistence      -> share
vk-facade        -> share
infrastructure   -> share
app              -> ai, share, infrastructure, persistence, vk-facade, processor, receiver
test-fixtures    -> share, vk-facade            (testing-модуль)
test-common      -> (нет)
arch-tests       -> все бизнес-модули + app     (testing-модуль, только тесты)
```

Удаляются неиспользуемые классами рёбра, нарушающие граф: `processor -> ai` (`src/processor/build.gradle.kts:13`), `ai -> persistence` (`src/ai/build.gradle.kts:15`). Проверено: в `src/processor/src` нет импортов `com.simarel.vkbot.ai`, в `src/ai/src/main` — импортов `com.simarel.vkbot.persistence`. Test-scope рёбра (`testImplementation(project(":src:infrastructure"))` в processor/receiver/persistence) остаются: они нужны декораторам в тестах.

### 3.3 Слои внутри модуля

```
com.simarel.vkbot.<module>/
├── domain/          # сущности, VO, ошибки; без фреймворков и чужих слоёв
├── port/input/      # use-case интерфейсы, которые зовёт внешний мир
├── port/output/     # интерфейсы, которые бизнес-логике нужны снаружи
├── usecase/         # реализация port/input над domain и port/output
├── command/         # атомарные операции Command<REQ,RESP>
├── adapter/input/   # REST, consumers (@Incoming), in-process входы
├── adapter/output/  # БД (jOOQ), VK API, AI, Vane, in-process реализации портов
└── config/          # producers/конфигурация модуля, где есть
```

Такие пакеты есть в ai, persistence, processor, receiver, vk-facade, infrastructure, share (проверено `find src/<m>/src/main/kotlin -type d`). В `share` слоя `adapter` нет — корректно (audit §4.5).

### 3.4 Правила границ и enforcement (ArchUnit, один тест на правило)

`src/testing/arch-tests` получает три класса тестов.

**`ModuleDependenciesTest` (правки):**
- `APP_PKG` сейчас `com.simarel.vkbot.app..` (`ModuleDependenciesTest.kt:24`), а `Application.kt` — в `com.simarel.vkbot` (`Application.kt:1`), правило для app вакуумно. Переносим `Application.kt` в пакет `com.simarel.vkbot.app` — правило становится настоящим.
- В classpath `arch-tests` добавляются `src:persistence` и `src:app`: сейчас persistence виден только транзитивно через `ai` (`src/testing/arch-tests/build.gradle.kts:15`), а после удаления ребра `ai -> persistence` перестанет быть виден; app не виден вовсе.
- Правила «модуль зависит только от share» остаются для 6 модулей + share; правило для persistence сохраняется.

**`GradleDependenciesTest` (переписать — сейчас вакуумный):** `findBuildGradleKtsFiles()` отбрасывает все файлы фильтром `!it.path.contains("build")` (`GradleDependenciesTest.kt:57-66`), а каждый `build.gradle.kts` содержит «build», поэтому список всегда пуст. Новые тесты:
1. `build files are discovered` — ровно 12 `build.gradle.kts` (корень + 11 модулей), пустой список валит тест.
2. `production project dependencies follow the allowlist` — парсит `implementation(project(":src:..."))`/`api(project(...))` и сверяет с allowlist §3.2.
3. `testing modules are used only in test configurations` — существующая логика с исправленным обходом файлов.

**`LayerDependenciesTest` (новый, по одному `@Test` на правило):**

| # | Правило | Что запрещено |
|---|---|---|
| L1 | `domain` чист | пакеты `..domain..` не зависят от `io.quarkus..`, `jakarta..`, `org.jooq..`, `com.fasterxml..`, `io.smallrye..`, `org.eclipse..`, `dev.langchain4j..` и от своих `..adapter..`, `..usecase..`, `..command..`, `..port..`, `..config..` |
| L2 | `port` чист | пакеты `..port..` не зависят от `..adapter..`, `..usecase..`, `..command..`, `..config..` и тех же фреймворков |
| L3 | `usecase`/`command` не знают адаптеров и runtime | не зависят от `..adapter..`, `io.quarkus..`, `org.jooq..`, `com.fasterxml..`, `jakarta.ws.rs..`, `jakarta.json..`, `dev.langchain4j..`, `io.smallrye..`; разрешены DI/конфиг-аннотации `jakarta.inject..`, `jakarta.enterprise..`, `jakarta.transaction..`, `org.eclipse.microprofile.config.inject..` |
| L4 | входной адаптер не ходит в command/usecase напрямую | `..adapter.input..` не зависит от `..command..`, `..usecase..`, `..adapter.output..` |
| L5 | выходной адаптер не ходит во входной и в usecase | `..adapter.output..` не зависит от `..adapter.input..`, `..usecase..` (зависимость от `command` разрешена: `VaneSearchToolAdapter` — документированный паттерн, `AGENTS.md:108-109`) |
| L6 | share — ядро | `..share..` не зависит от пакетов других модулей (уже есть, `ModuleDependenciesTest.kt:47-58`) |

Чтобы L1–L5 были зелёными, в этом же проходе правятся четыре утечки (полный список файлов — §11):

- `persistence/domain/entity/Tables.kt:3-4` импортирует `org.jooq` → перенос в `persistence/adapter/output/persistence/jooq/Tables.kt` (4 импорта: `JooqMessageRepository.kt:4`, `JooqSummaryRepository.kt:6-7`, `JooqVkGroupProfileRepository.kt:3`, `JooqVkUserProfileRepository.kt:3`).
- `persistence/usecase/SaveMessageUsecase.kt:3` импортирует Jackson `ObjectMapper` → сериализация `forwardedMessages` уходит за порт (`persistence/port/output` + реализация в `persistence/adapter/output/json`).
- `io.quarkus.logging.Log` в бизнес-слое → `java.lang.System.Logger` (JDK, без фреймворка): `persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt:11`, `receiver/usecase/ReceiveMessageUsecase.kt:9`, `ai/command/summary/ProcessSummaryCommandImpl.kt:8`.
- `@ApplicationScoped` на use case и `@ConfigProperty` в командах остаются: это DI, разрешено L3 (ADR 0003).

## 4. Межмодульные контракты: in-process порты вместо loopback-REST

**Факт сейчас.** Два синхронных HTTP-контура внутри одного деплоя: `ai -> persistence` (`src/ai/.../adapter/output/persistence/PersistenceService.kt:18-19`, `configKey = "persistence"`) и `persistence -> vk-facade` (`src/persistence/.../adapter/output/client/vkfacade/VkFacadeService.kt:12-13`, `configKey = "vk-facade"`), оба на `http://localhost:8080` (`src/app/src/main/resources/application.yml:47-52`). Контракт продублирован дословно: сервер `persistence/adapter/input/rest/PersistenceService.kt:18-90` и клиент `ai/adapter/output/persistence/PersistenceService.kt:18-90`; изменение одной стороны не ломает сборку другой.

**Что уже готово.** В `share` лежат нужные односторонние порты:
- persistence — 9 портов `share/port/output/persistence/*` (`OutputPort<REQ,RESP>`);
- vk — `share/port/output/vk/GetUserProfilesBatchPort.kt`, `GetGroupProfilesBatchPort.kt`;
- входы исполнителей: `PersistenceDataInputPort` реализован `PersistenceDataUsecase` (`persistence/usecase/PersistenceDataUsecase.kt:34`), `VkProfileInputPort` реализован `VkProfileUsecase` (`vkFacade/usecase/profile/VkProfileUsecase.kt:9`).

**Решение.** Синхронные вызовы между модулями одного деплоя идут через порты `share`, реализованные модулем-поставщиком:

| Порт (share) | Реализация | Делегирует в |
|---|---|---|
| `FindStoredMessagesBeforePort`, `FindStoredMessagesBetweenPort`, `FindLastStoredSummaryPort`, `HasStoredPendingSummaryPort`, `CreateStoredPendingSummaryPort`, `SaveStoredCompletedSummaryPort`, `MarkStoredSummaryFailedPort`, `FindStoredUserProfilesByIdsPort`, `FindStoredGroupProfilesByIdsPort` | новый `persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` | `PersistenceDataInputPort` — тот же use case, что и у REST-контроллера |
| `GetUserProfilesBatchPort`, `GetGroupProfilesBatchPort` | новый `vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt` | `VkProfileInputPort` |

Удаляются: клиентские интерфейсы `ai/.../PersistenceService.kt` и `persistence/.../VkFacadeService.kt`; REST-адаптеры `ai/.../PersistenceDataRestAdapter.kt`, `ai/.../SummaryPersistenceRestAdapter.kt`, `persistence/.../VkFacadeRestAdapter.kt`; 9 внутренних портов `ai/port/output/persistence/*` (они побайтно равны share-портам, отличие — только строка `package`; проверено `diff`). Команды `GenerateSummaryCommandImpl` и `ProcessSummaryCommandImpl` переключаются на share-порты (замена импортов).

REST-эндпоинты остаются публичным API модулей: `POST /persistence/*` (`PersistenceDataController`) и `GET /api/profiles/*` (`VkFacadeController`); серверный интерфейс — единственная копия контракта. Контроллер и in-process адаптер — два входных адаптера к одному `port.input` (Information Expert: логика в use case, адаптеры только маппят).

События остаются на RabbitMQ: поток асинхронный по требованию (вебхук VK не должен ждать AI), retry-лестница и DLQ уже реализованы (`infrastructure/mq/adapter/input/EventDispatcher.kt:63-111`, `src/app/src/main/resources/application.yml:63-169`). Новых брокеров, сервисов и модулей не добавляем.

| Вариант | Цена | Риск |
|---|---|---|
| A. Оставить оба REST-контура, зафиксировать как долг | 0 правок сейчас; навсегда: две копии контракта, loopback-сеть, конфиг URL, таймауты, сериализация | изменение одной стороны не ломает сборку; скрытый runtime-отказ |
| B. **Порты `share` + in-process реализации** | ~6 файлов удалить, 2 добавить, 2 импорта поправить, 2 build-ребра и 2 блока конфига убрать | CDI-связывание; снимается сборкой (ArC) и полным интеграционным тестом |

Принят B (ADR 0004).

**Payload'ы событий — тоже контракт и тоже продублированы.** Сейчас `SummaryRequestedPayload` объявлен отдельно у производителя (`persistence/usecase/SaveMessageUsecase.kt:52-55`) и у потребителя (`ai/adapter/input/event/SummaryRequestedEventProcessor.kt:28-31`); `SummaryReadyPayload` — у `persistence/usecase/PersistenceDataUsecase.kt:179-184` и `vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt:57-62`. Поля совпадают, но дрейф молчалив. Оба класса переезжают в `share/domain/event/` и используются обеими сторонами. `Event` — закрытый enum из шести значений (`share/domain/Event.kt:3-10`): `MESSAGE_NEW`, `MESSAGE_RECEIVED`, `MESSAGE_REQUIRE_ANSWER`, `ANSWER_MESSAGE`, `SUMMARY_REQUESTED`, `SUMMARY_READY`. Версионирования схемы нет и не требуется: производитель и потребитель в одном деплое, меняются одним коммитом, допустимы только аддитивные изменения. Записывается в ADR 0004.

## 5. Данные: владелец, миграции, откат

- Владелец схемы — `src:persistence`: Liquibase master `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml` + changesets `001`–`004`; миграции применяются на старте (`src/app/src/main/resources/application.yml:28-30`).
- Правило: changeset только добавляется, применённые не редактируются; следующий номер — 005. Откат приложения — предыдущий образ; изменения схемы обязаны быть совместимы на один релиз назад (rollback-команд Liquibase в проекте нет — фиксируется в `docs/development/deployment.md`).
- jOOQ: кодогенерация выключена, таблицы вручную (`src/persistence/build.gradle.kts:94-130`), поэтому изменение схемы и `Tables.kt` — один диф.
- Идемпотентность: доставка at-least-once, уникального ключа у `messages` нет, `JooqMessageRepository.persist` — чистый insert. В этом проходе **не меняется** (вне DoD); фиксируется в `docs/architecture/data-flow.md` как известное ограничение с условием апгрейда (уникальный `(peer_id, conversation_message_id)` + upsert, затем outbox — если появятся дубли/потери в проде).
- `share` не владеет JSONB: `StoredMessage.forwardedMessages: String?` (`share/domain/model/StoredMessage.kt:13`) — известный долг, парсится на границе в `ai/adapter/output/context/ConversationContextRestAdapter.kt:81-105`. В этом проходе не меняется, фиксируется в `docs/architecture/data-flow.md`.

## 6. Конфигурация и окружение

**Решение:** единственный production-конфиг — `src/app/src/main/resources/application.yml` (+ `application-queues.yml`, подключаемый `src/app/src/main/resources/application.yml:12-14`).

Удаляются все три копии из `src/infrastructure/src/main/resources/`: `application.yml` (расходится: `vk.api.version` 5.131 против 5.199, `src/infrastructure/.../application.yml:5`), `application-queues.yml` (побайтно идентичен app-копии — проверено `diff -q`), `application-local.yml` (заглушка секретов). Перед удалением уникальные ключи переносятся в app-конфиг:

| Ключ | Сейчас | Почему обязателен |
|---|---|---|
| `aiUseranswerSystemprompt` | только в infra-копии: `src/infrastructure/.../application.yml:7` | `@SystemMessage("{config:aiUseranswerSystemprompt}")` — `ai/adapter/output/ai/answer/UserAnswerAiService.kt:17`; полный интеграционный тест проходит только потому, что ключ сейчас резолвится из classpath |
| `aiSumarizationSystemprompt` | только в infra-копии: `:8` | `SummarizationAiService.kt:11` |
| `aiOcrSystemPrompt` | нигде | `OcrAiService.kt:13` (OCR не подключён к use case — `RecognizeImagesCommand` используется только сам собой, `ai/command/ocr/RecognizeImagesCommandImpl.kt:10`; ключ добавляется с дефолтом) |
| `vk.bot.id` | только в тестовых конфигах | `@ConfigProperty(name = "vk.bot.id")` без дефолта — `processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:17`; в prod-конфиге отсутствует → отказ на первом сообщении |
| `vk.bot.mention` | только в тестовых конфигах | `IsRequireAnswerCommand.kt:19` |
| `quarkus.rest-client.vane.url`, `connect-timeout`, `read-timeout` | только в infra-копии `:46-50` | `configKey = "vane"` — `VaneSearchClient.kt:11` |

Удаляются мёртвые ключи/зависимости:

- `quarkus.jooq.dialect` (`src/app/src/main/resources/application.yml:26-27`) — расширения `quarkus-jooq` в сборке нет.
- `quarkus.hibernate-orm` (`src/infrastructure/.../application.yml:19-21`) — Hibernate в сборке нет; удаляется вместе с файлом.
- `quarkus-flyway` (`build.gradle.kts:33`) — миграции на Liquibase.
- `quarkus-jacoco` (`build.gradle.kts:35`) — заменяется стандартным Jacoco-плагином (§7); сейчас артефактов покрытия не даёт.
- `quarkus.rest-client.persistence` и `quarkus.rest-client.vk-facade` (`src/app/src/main/resources/application.yml:47-52`) — после §4 не нужны.

Остаются и документируются реальные ключи: `vk.*`, `summary.*`, `quarkus.datasource.*` (devservices), `quarkus.liquibase.*`, `quarkus.otel.*`, `quarkus.rest-client.vk`, `quarkus.rest-client.vane`, `mp.messaging.*` (retry/DLQ), `smallrye.messaging.observation`.

Обязательные env для prod (документируются; дефолтов-заглушек в коде не добавляем): `VK_SECRET`, `VK_CONFIRMATION_CODE`, `VK_API_TOKEN`, `VK_BOT_ID`, `VK_BOT_MENTION`, `VANE_URL`, `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`, `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`, `RABBITMQ_HOST` (+`RABBITMQ_PORT/USERNAME/PASSWORD` при необходимости), `SUMMARY_ENABLED_CHATS`, `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`. README/configuration-док перестают обещать `DATABASE_URL/DB_USERNAME/DB_PASSWORD` как читаемые — сейчас они не читаются (audit §4.2, §4.10).

Секреты в репозиторий не коммитятся; `.env` в `.gitignore`. Тестовые профили (`src/app/src/test/resources/*`, `src/testing/test-common/src/main/resources/application.yaml`) остаются как есть — они работают.

## 7. Гейты: команды, пороги, негативные проверки, CI

Гейт обязан валиться, а не только конфигурироваться.

### 7.1 Команды

| Гейт | Команда | Цель | Сейчас |
|---|---|---|---|
| Сборка деплоя | `./gradlew build` | exit 0, `src/app/build/quarkus-app/quarkus-run.jar`, корневого `build/quarkus-app/` нет | работает, но собирает два раннера (audit §5, прогон 1) |
| Тесты | `./gradlew test` | exit 0, не меньше 41 существующего теста + новые (§11) | работает: 41 тест, 0 падений |
| Линт | `./gradlew detekt` | exit 0; новое нарушение валит | `NO-SOURCE` (плагин только в корне, `build.gradle.kts:5,83-92`) |
| Формат | `./gradlew ktlintCheck` | exit 0 | проверяет пустые sourceSets корня |
| Покрытие-отчёт | `./gradlew coverageReport` | HTML+XML в `build/reports/jacoco/coverageReport/` | задачи нет (`jacocoTestReport` → exit 1) |
| Покрытие-порог | `./gradlew coverageVerification` | line ratio ≥ `coverageMin`, иначе FAIL | порога нет |
| Архитектура | `./gradlew :src:testing:arch-tests:test` (входит в `build`) | правила L1–L6 + build-граф | 8 тестов, `GradleDependenciesTest` вакуумный |
| Карта возможностей | `./scripts/check-capabilities.sh` | падает на несуществующем пути/ADR id | скрипта нет |
| CI | `.github/workflows/build-and-test.yml` | build + test + lint + format + coverage + docs | только build+test (`build-and-test.yml:25-29`) |

### 7.2 Как включаются гейты

Корневой `build.gradle.kts` становится конвенционным: `base` + `subprojects {}`:

- `apply(plugin = "org.jetbrains.kotlin.jvm")`, toolchain 21 для всех подпроектов;
- `apply(plugin = "io.gitlab.arturbosch.detekt")`: `config = rootProject.files("detekt-config.yml")`, `buildUponDefaultConfig = true`, `autoCorrect = false` (сейчас `true`, `build.gradle.kts:87` — линтер не должен править код), `maxIssues = 0` из `detekt-config.yml:2`. Baseline включается только если `${projectDir}/detekt-baseline.xml` существует; baseline генерируется один раз (`./gradlew detektBaseline`) и коммитится, новые нарушения в baseline не попадают (ADR 0005).
- `apply(plugin = "org.jlleitschuh.gradle.ktlint")`: `ktlintCheck` по всем модулям; существующий код один раз приводится `./gradlew ktlintFormat`, `.editorconfig` — источник стиля (`ij_kotlin_allow_trailing_comma_on_call_site = true`, `.editorconfig:12-13`; правило AGENTS.md «no trailing commas» удаляется как противоречащее).
- `apply(plugin = "jacoco")` + `tasks.withType<Test> { useJUnitPlatform() }`; в корне задачи `coverageReport` (`JacocoReport`, агрегирует `src/*/build/jacoco/test.exec` и class-каталоги бизнес-модулей) и `coverageVerification` (`JacocoCoverageVerification`, `LINE`/`COVEREDRATIO` ≥ `coverageMin`); корневой `check` зависит от `coverageVerification`, поэтому `./gradlew build` включает порог.
- `enforcedPlatform(quarkus-bom)` из `gradle.properties` — один раз на все подпроекты (§1).

Порог покрытия — детерминированная формула, без решения на стороне специалиста: измерить агрегатный line ratio `L` по XML отчёта; `coverageMin = max(0.10, floor(L * 20) / 20)` (округление вниз до 5 п.п.). Пример: `L = 0.32` → `coverageMin = 0.30`. Значение `L` и `coverageMin` записываются константой в `build.gradle.kts` и в ADR 0005. Цель-ратчет (не порог этого прохода): 0.40, затем 0.60 — отдельные задачи с тестами. Модули `src/testing/*` в покрытие не входят; `src/app` входит.

### 7.3 Негативные проверки (доказать, что каждый гейт падает)

Каждая проверка — временная локальная правка, прогон, откат (в отчёте implement/verify цитируется вывод):

| Гейт | Негативный тест | Ожидание |
|---|---|---|
| detekt | функция/имя с нарушением в любом модуле | `./gradlew detekt` → FAILED, finding назван |
| ktlintCheck | испортить форматирование | `./gradlew ktlintCheck` → FAILED |
| arch L3 | импорт `com.simarel.vkbot.persistence.adapter...` в `ai/usecase` | `LayerDependenciesTest` → FAILED |
| arch build-граф | `implementation(project(":src:ai"))` в `share` | `GradleDependenciesTest` → FAILED |
| arch module | импорт класса `ai` в `receiver` | `ModuleDependenciesTest` → FAILED |
| coverage | временно `coverageMin = 0.99` | `coverageVerification` → FAILED |
| capabilities | переименовать путь/ADR id в `docs/capabilities/index.md` | `scripts/check-capabilities.sh` → exit 1 |
| CI | `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build-and-test.yml'))"` | exit 0 (валидный YAML) |

### 7.4 CI

Файл остаётся `.github/workflows/build-and-test.yml`, триггеры `push`/`pull_request` в `main` (`build-and-test.yml:3-7`), JDK 21 temurin, `gradle/actions/setup-gradle@v4`. Job'ы:

| job | шаг |
|---|---|
| `build` | `./gradlew :src:app:quarkusBuild` + `test -f src/app/build/quarkus-app/quarkus-run.jar` + `test ! -e build/quarkus-app` |
| `test` | `./gradlew test` |
| `lint` | `./gradlew detekt ktlintCheck` |
| `coverage` | `./gradlew test coverageReport coverageVerification` + upload `build/reports/jacoco/coverageReport/html` артефактом |
| `docs` | `./scripts/check-capabilities.sh` |

`--scan` из шага сборки убирается (внешний сервер сканов не нужен). `./gradlew build` остаётся локальной командой «всё разом» и включает те же гейты через `check`.

## 8. ADR: что создать (`docs/adr/`, нумерация с 0001 — каталога нет)

Каталог `docs/adr/` отсутствует (audit §4.12), поэтому нумерация с 0001; преемников нет — противоречащих ADR в проекте не было, история начинается с этого набора. Статус всех — `accepted`. Формат: Контекст → Решение → Последствия → Отклонённые альтернативы, один экран.

| # | Файл | Решение |
|---|---|---|
| 0001 | `docs/adr/0001-stack-kotlin-quarkus-gradle.md` | Kotlin 2.3.0 + Gradle 9.3.0 (Kotlin DSL) + Quarkus 3.34.2 + JDK 21 (отклонение от текущего LTS с условием апгрейда) + LangChain4j 1.8.4 + PostgreSQL/Liquibase + RabbitMQ + jOOQ (ручные таблицы) + JUnit5/ArchUnit/WireMock/Awaitility; один источник версии Quarkus (BOM из `gradle.properties`), без `:latest`. Отклонены Maven, Spring Boot/Ktor, апгрейд Quarkus/JDK сейчас, version catalog в этом проходе. |
| 0002 | `docs/adr/0002-single-deployable-app-module.md` | Один деплой `:src:app`; корень — агрегатор/конвенции; Dockerfile'ы (jvm/legacy-jar/native/native-micro) — из `src/app/build`; `quarkusDev` только `:src:app`. Отклонены два раннера, корень как приложение. |
| 0003 | `docs/adr/0003-module-boundaries-and-layer-rules.md` | 11 модулей, горизонтальные слои пакетами, `share` как общее ядро, CDI как composition root, правила L1–L6 и их enforcement. Отклонены 7 layer-модулей, модуль на фичу-деплой, явные `@Produces` для всех бинов. |
| 0004 | `docs/adr/0004-in-process-ports-instead-of-loopback-rest.md` | Синхронные вызовы внутри деплоя — через порты `share` и in-process реализации; события — RabbitMQ; payload'ы событий — в `share`, версионируются одним релизом, только аддитивные изменения. Отклонены сохранение loopback REST с дублями, OpenAPI-клиент, прямое ребро `ai -> persistence`. |
| 0005 | `docs/adr/0005-quality-gates-and-ci.md` | detekt (baseline + `maxIssues: 0`, `autoCorrect = false`), `ktlintCheck`, Jacoco-агрегат с формулой порога, ArchUnit по правилам, CI-job'ы, негативные проверки. Отклонены Spotless/Checkstyle, lint без baseline/гейта, coverage без порога, порог, придуманный без измерения. |
| 0006 | `docs/adr/0006-configuration-and-secrets.md` | Один `application.yml` в `:src:app`; перенос `vane`, prompt'ов, `vk.bot.*`; devservices только dev/test; обязательные env для prod; мёртвые ключи (`quarkus.jooq`, `hibernate-orm`, flyway) удалены. Отклонены конфиг на модуль, коммит секретов, дефолт-URL Vane. |

## 9. Документы: точный набор и что каждый отвечает

Правит/создаёт стадия `docs`. «Переписать» = audit нашёл UNACTUAL; «дополнить» = частично.

| Файл | Что отвечает | Статус |
|---|---|---|
| `AGENTS.md` | конституция для агентов: стек и версии, карта 11 модулей, слои и правила направлений, реальные команды и таблица гейтов, fake-объекты и пути, конфиг, запрещённые импорты, ссылки на доки | переписать (audit §4.1) |
| `README.md` | продукт, быстрый старт (`./gradlew :src:app:quarkusDev`), команды, артефакт `src/app/build/quarkus-app/`, env, структура модулей | переписать (audit §4.2) |
| `docs/README.md` | индекс документации: architecture 4 файла, development 3, adr 6, capabilities; ссылка на `ai-tools.md` удаляется | дополнить (audit §4.3) |
| `docs/architecture/overview.md` | один деплой, 11 модулей, runtime-топология (HTTP-вход, RabbitMQ, in-process порты, VK API, Vane), реальные 6 событий | переписать (audit §4.4) |
| `docs/architecture/modules.md` | на модуль: владение, публичные входы, build-зависимости, runtime-рёбра (события, in-process порты, REST), расположение in-process реализаций | переписать (audit §4.5) |
| `docs/architecture/patterns.md` | реальные паттерны с классами: Ports & Adapters (`OutputPort<REQ,RESP>`), Command, UseCase, Decorator, Value Objects, DMZ VK JSON, Anti-Corruption на границе, AI tool adapter (Vane, `search_web/search_academic/search_discussions`), OCR | переписать (audit §4.6, §4.8) |
| `docs/architecture/data-flow.md` | webhook → MESSAGE_NEW → сохранение → MESSAGE_RECEIVED → processor → MESSAGE_REQUIRE_ANSWER → ai → ANSWER_MESSAGE → vk-facade → VK API; summary-поток; retry/DLQ; at-least-once и отсутствие дедупликации | переписать (audit §4.7) |
| `docs/development/testing.md` | команды тестов, реальные пути fake-объектов (`src/testing/test-fixtures/.../testfixtures/...`), интеграционные тесты (DevServices + WireMock), отчёт покрытия и порог | переписать (audit §4.9) |
| `docs/development/configuration.md` | реальные ключи из `src/app/src/main/resources/application.yml`, env, тестовые профили, devservices, секреты | переписать (audit §4.10) |
| `docs/development/deployment.md` | JVM/legacy/native Docker из `src/app/build`, обязательные env, health `/q/health`, Liquibase на старте, откат образом, отсутствие rollback-команд | переписать (audit §4.11) |
| `docs/adr/0001..0006*.md` | шесть решений §8 | создать |
| `docs/architecture/ai-tools.md` | — | **удалить**; содержание (Vane tool adapter, имена инструментов, base URL) входит в `patterns.md` и `modules.md`; ссылка `README.md:246` обновляется |
| `docs/capabilities/index.md` + `<capability>.md` | продуктовая карта (пишет аналитик из кода, §10) | создать в стадии capabilities |

## 10. Карта возможностей: план файлов (пишет аналитик из кода)

Формат — в контракте аналитика; здесь задаются проверяемые соглашения: в `docs/capabilities/index.md` на каждую возможность строка со ссылкой на файл; внутри файла каждый путь кода — в обратных кавычках и репозиторно-относительный (`src/...`, `docs/...`), каждое решение — токеном `ADR NNNN`; `scripts/check-capabilities.sh` проверяет существование всех таких путей и `docs/adr/NNNN-*.md`, и падает при отсутствии `docs/capabilities/index.md`.

Планируемые файлы (аналитик подтверждает по коду и может объединить/разделить, но каждая существующая возможность должна иметь строку и файл):

| Файл | Возможность | Точки входа |
|---|---|---|
| `docs/capabilities/vk-webhook.md` | приём и валидация событий VK Callback (confirmation, secret, `message_new`) | `receiver/adapter/input/vk/VkMediatorRouterImpl.kt`, `receiver/adapter/input/vk/VkMediatorRouterSecurityDecorator.kt` |
| `docs/capabilities/message-storage.md` | хранение сообщений/профилей/саммари и запросы по ним | `persistence/adapter/input/rest/PersistenceDataController.kt`, `persistence/adapter/output/persistence/jooq/` |
| `docs/capabilities/answer-decision.md` | решение, нужен ли AI-ответ | `processor/command/isRequireAnswer/`, `processor/usecase/message/messageReceived/` |
| `docs/capabilities/ai-answer.md` | генерация и отправка AI-ответа | `ai/usecase/message/requireAnswer/`, `ai/adapter/output/ai/answer/`, `vkFacade/command/sendVkMessage/` |
| `docs/capabilities/conversation-summary.md` | саммаризация переписки по порогу | `ai/usecase/summary/`, `ai/command/summary/`, `vk-facade/adapter/input/event/SummaryReadyEventProcessor.kt` |
| `docs/capabilities/vk-profiles.md` | получение и кэширование профилей VK с circuit breaker | `vkFacade/usecase/profile/VkProfileUsecase.kt`, `vkFacade/adapter/output/vk/circuitbreaker/` |
| `docs/capabilities/event-dispatch.md` | маршрутизация событий, retry, DLQ | `infrastructure/mq/adapter/input/EventDispatcher.kt`, `src/app/src/main/resources/application-queues.yml` |
| `docs/capabilities/ai-tools-vane.md` | веб-инструменты Vane для AI | `ai/adapter/output/ai/tool/VaneSearchToolAdapter.kt`, `ai/adapter/output/ai/tool/vane/VaneSearchClient.kt` |

OCR-команда реализована, но не подключена ни к одному use case (единственное использование — сам impl, `ai/command/ocr/RecognizeImagesCommandImpl.kt:10`): аналитик фиксирует её как неиспользуемый код, а не как возможность; удаление — вне этого прохода.

## 11. Дерево изменений для стадии implement (файл → действие)

### 11.1 Сборка и CI

| Файл | Действие |
|---|---|
| `build.gradle.kts` | переписать: `base`-агрегатор + `subprojects {}` с Kotlin/JDK21/BOM/detekt/ktlint/jacoco/test; задачи `coverageReport` и `coverageVerification`; `check` зависит от порога; удалить `io.quarkus`, runtime- и test-зависимости корня |
| `src/app/build.gradle.kts` | единственный Quarkus-деплой; сюда переезжают deployable-расширения (info, logging-json, otel, micrometer-otel, observability-devservices(-lgtm), smallrye-health, rest, rest-client-jackson); убрать BOM-строку и явные JUnit-версии |
| `src/ai/build.gradle.kts` | убрать `project(":src:persistence")` и BOM-строку; `jackson-module-kotlin` без `:latest` |
| `src/processor/build.gradle.kts` | убрать `project(":src:ai")` и BOM-строку |
| `src/persistence/build.gradle.kts` | убрать BOM-строку, `quarkus-rest-client-jackson` (клиент удаляется), явные JUnit-версии; оставить jOOQ/Liquibase/rest |
| `src/receiver/build.gradle.kts` | убрать BOM-строку; `jackson-module-kotlin:latest` → версия из BOM |
| `src/vk-facade/build.gradle.kts` | убрать BOM-строку; `jackson-module-kotlin:latest` → BOM |
| `src/infrastructure/build.gradle.kts` | убрать BOM-строку; `jackson-module-kotlin:latest` → BOM |
| `src/share/build.gradle.kts` | убрать BOM-строку |
| `src/testing/arch-tests/build.gradle.kts` | добавить `project(":src:persistence")` и `project(":src:app")`; убрать BOM-строку |
| `src/testing/test-fixtures/build.gradle.kts` | убрать BOM-строку |
| `detekt-baseline.xml` в модулях с находками | создать один раз `./gradlew detektBaseline`, закоммитить |
| `.github/workflows/build-and-test.yml` | 5 job'ов из §7.4 |
| `scripts/check-capabilities.sh` | новый чек §7.1/§10, исполняемый |
| `gradle.properties`, `.editorconfig`, `settings.gradle.kts` | без изменений (версии и источник стиля уже здесь) |

### 11.2 Конфигурация и деплой

| Файл | Действие |
|---|---|
| `src/app/src/main/resources/application.yml` | добавить `aiUseranswerSystemprompt`, `aiSumarizationSystemprompt`, `aiOcrSystemPrompt`, `vk.bot.id`, `vk.bot.mention`, `quarkus.rest-client.vane`; убрать `quarkus.jooq`, `quarkus.rest-client.persistence`, `quarkus.rest-client.vk-facade` |
| `src/infrastructure/src/main/resources/application.yml` | удалить (после переноса ключей) |
| `src/infrastructure/src/main/resources/application-queues.yml` | удалить (идентичен app-копии) |
| `src/infrastructure/src/main/resources/application-local.yml` | удалить |
| `src/docker/Dockerfile.native` | `build/*-runner` → `src/app/build/*-runner` |
| `src/docker/Dockerfile.native-micro` | `build/*-runner` → `src/app/build/*-runner` |
| `src/docker/Dockerfile.legacy-jar` | `build/lib/*`, `build/*-runner.jar` → `src/app/build/...` |
| `src/docker/Dockerfile.jvm` | без изменений (уже `src/app/build`, `:87-90`) |

### 11.3 Код: in-process порты и контракты (§4)

| Файл | Действие |
|---|---|
| `src/ai/.../adapter/output/persistence/PersistenceService.kt` | удалить |
| `src/ai/.../adapter/output/persistence/PersistenceDataRestAdapter.kt` | удалить |
| `src/ai/.../adapter/output/persistence/SummaryPersistenceRestAdapter.kt` | удалить |
| `src/ai/.../port/output/persistence/*.kt` (9 файлов) | удалить (равны share-портам, кроме `package`) |
| `src/ai/.../command/summary/GenerateSummaryCommandImpl.kt` | импорты `ai.port.output.persistence.*` → `share.port.output.persistence.*` |
| `src/ai/.../command/summary/ProcessSummaryCommandImpl.kt` | те же импорты; `io.quarkus.logging.Log` → `java.lang.System.Logger` (файл уже с WIP-правками — строить поверх) |
| `src/persistence/.../adapter/output/client/vkfacade/VkFacadeService.kt` | удалить |
| `src/persistence/.../adapter/output/vkfacade/VkFacadeRestAdapter.kt` | удалить |
| `src/persistence/.../adapter/input/inprocess/PersistenceDataInProcessAdapter.kt` | создать: 9 классов, каждый реализует share-порт через `PersistenceDataInputPort` (маппинг как в `PersistenceDataController.kt:27-126`) |
| `src/vk-facade/.../adapter/input/inprocess/VkProfileInProcessAdapter.kt` | создать: 2 класса через `VkProfileInputPort` (как `VkFacadeController.kt:19-35`) |
| `src/share/.../domain/event/SummaryRequestedPayload.kt` | создать (перенос из `SaveMessageUsecase.kt:52-55` / `SummaryRequestedEventProcessor.kt:28-31`) |
| `src/share/.../domain/event/SummaryReadyPayload.kt` | создать (перенос из `PersistenceDataUsecase.kt:179-184` / `SummaryReadyEventProcessor.kt:57-62`) |
| `src/persistence/.../usecase/SaveMessageUsecase.kt` | импорт payload из share; убрать `ObjectMapper`, инжектить порт сериализации `forwardedMessages` |
| `src/ai/.../adapter/input/event/SummaryRequestedEventProcessor.kt` | импорт payload из share |
| `src/persistence/.../usecase/PersistenceDataUsecase.kt` | импорт payload из share |
| `src/vk-facade/.../adapter/input/event/SummaryReadyEventProcessor.kt` | импорт payload из share |
| `src/persistence/.../port/output/ForwardedMessagesSerializationPort.kt` | создать: `OutputPort<Request, Response>`, `Request(messages: List<Message>)`, `Response(json: String?)` |
| `src/persistence/.../adapter/output/json/JacksonForwardedMessagesAdapter.kt` | создать: реализация порта на `ObjectMapper` |
| `src/persistence/.../domain/entity/Tables.kt` | перенести в `.../adapter/output/persistence/jooq/Tables.kt`, сменить `package` |
| `src/persistence/.../adapter/output/persistence/jooq/{JooqMessageRepository,JooqSummaryRepository,JooqVkGroupProfileRepository,JooqVkUserProfileRepository}.kt` | обновить импорт `Tables` |
| `src/persistence/.../command/fetchuserprofile/FetchUserProfileCommandImpl.kt` | `io.quarkus.logging.Log` → `java.lang.System.Logger` |
| `src/receiver/.../usecase/ReceiveMessageUsecase.kt` | `io.quarkus.logging.Log` → `java.lang.System.Logger` |
| `src/app/src/main/kotlin/com/simarel/vkbot/Application.kt` | перенести в `.../app/Application.kt`, `package com.simarel.vkbot.app` |

### 11.4 Архитектурные тесты и тесты нового кода

| Файл | Действие |
|---|---|
| `src/testing/arch-tests/.../ModuleDependenciesTest.kt` | `APP_PKG` теперь соответствует реальному пакету `com.simarel.vkbot.app` |
| `src/testing/arch-tests/.../GradleDependenciesTest.kt` | переписать: невакуумный обход, allowlist production-рёбер, testing-модули только в тестах |
| `src/testing/arch-tests/.../LayerDependenciesTest.kt` | создать: L1–L5, по одному `@Test` |
| `src/persistence/src/test/.../PersistenceDataInProcessAdapterTest.kt` | создать: fake `PersistenceDataInputPort`, проверка маппинга запрос/ответ всех 9 портов |
| `src/vk-facade/src/test/.../VkProfileInProcessAdapterTest.kt` | создать: fake `VkProfileInputPort`, 2 порта |

### 11.5 Документы

`AGENTS.md`, `README.md`, `docs/README.md`, `docs/architecture/{overview,modules,patterns,data-flow}.md`, `docs/development/{testing,configuration,deployment}.md`, `docs/adr/0001..0006*.md`, `docs/capabilities/index.md` + 8 файлов §10; `docs/architecture/ai-tools.md` — удалить.

## 12. Вне scope этого прохода (названо явно)

- Апгрейд JDK/Quarkus/Kotlin/LangChain4j, version catalog, dependency verification.
- Удаление публичных REST-эндпоинтов `/persistence/*`, `/api/profiles/*`: остаются публичным API; удаление — только с подтверждённым отсутствием внешних потребителей.
- Идемпотентность/дедупликация сообщений, outbox/saga: retry-лестница и DLQ остаются как есть; ограничение фиксируется в `data-flow.md`.
- Типизация `StoredMessage.forwardedMessages: String?`.
- Удаление неиспользуемой OCR-команды, LICENSE/CHANGELOG/CODEOWNERS, docker-compose, Prometheus-registry.
- Native-сборка как CI-гейт (Docker-образы документируются, но не собираются в CI).

## 13. Smoke test «сборка работает end-to-end»

```bash
./gradlew clean build
test -f src/app/build/quarkus-app/quarkus-run.jar      # единственный деплой собран
test ! -e build/quarkus-app                            # корневой раннер больше не собирается
```

`clean build` включает `:src:app:test`, где `VkWebhookIntegrationTest.full flow - message_new event is processed end to end with VK API call` (`src/app/src/test/kotlin/com/simarel/vkbot/integration/VkWebhookIntegrationTest.kt:43-177`) прогоняет путь вебхук → RabbitMQ → persistence (in-process) → processor → ai → vk-facade → WireMock VK API. Это одновременно smoke и доказательство, что in-process порты связаны в живом приложении. Полный прогон требует Docker (DevServices PostgreSQL/RabbitMQ) — фиксируется в `docs/development/testing.md`.

## 14. Команды следующей стадии и критерии приёмки

Стадия implement обязана выполнить и процитировать:

1. `./gradlew clean build` → exit 0; smoke §13.
2. `./gradlew test` → exit 0; число тестов ≥ 41 + новые (port-адаптеры и arch-тесты).
3. `./gradlew detekt` → exit 0 и негативная проверка §7.3.
4. `./gradlew ktlintCheck` → exit 0 и негативная проверка §7.3.
5. `./gradlew coverageReport coverageVerification` → отчёт и порог §7.2; негативная проверка §7.3; `L` и `coverageMin` записаны.
6. `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build-and-test.yml'))"` → exit 0.
7. `./scripts/check-capabilities.sh` → после стадии capabilities exit 0; негативная проверка §7.3.
8. `git status --short` → нет случайных артефактов (`build/`, `.ast-index/`, `.agile/` не коммитятся).
9. Три предсуществующих WIP-файла (`SummarizationAiAdapter.kt`, `ProcessSummaryCommandImpl.kt`, `SaveMessageUsecase.kt`) не откатываются; два из них правятся поверх текущего состояния.

Готово для стадии docs: AGENTS.md/README/architecture/development/ADR переписаны из кода и содержат только существующие команды, пути, классы и версии; ссылки на удалённый `ai-tools.md` нет.

## 15. Риски и допущения (что сломается, если неверно)

| Допущение | Если неверно |
|---|---|
| detekt 1.23.8 и ktlint-плагин 13.1.0 работают с Kotlin 2.3.0 и Gradle 9.3.0 (сейчас проверены только в режиме NO-SOURCE/SKIPPED) | обновить detekt/ktlint до последней стабильной версии, зафиксировать разрешённые версии в `gradle.properties` и ADR 0001; гейт не отключать |
| Стандартный Jacoco-плагин измеряет покрытие `@QuarkusTest` | если агрегат близок к нулю — включить `quarkus-jacoco` и строить отчёт из его данных; формула порога не меняется |
| In-process замена REST сохраняет поведение (те же use case, без JSON-раундтрипа) | полный интеграционный тест §13 падает; откат на вариант A (оставить REST), контракт в `share` всё равно один |
| Prompt'ы и `vane` из infra-копии после переноса в app-конфиг резолвятся так же, как сейчас | `./gradlew build` и полный интеграционный тест падают; значит, infra-копия была источником — ключи уже перенесены, поэтому разбирается до удаления файлов |
| `./gradlew build` исполняет задачи всех подпроектов (Gradle task-name selection) | добавить явные зависимости `build`/`check` от подпроектов в корневом build-файле |
| Docker доступен локально и в CI (DevServices) | тесты app/интеграционные не пройдут; на ubuntu-latest Docker есть |
| Три WIP-правки корректны и должны сохраниться | откат WIP-файлов недопустим; конфликт решается поверх текущего состояния |

**Границы дизайна:** ни один файл проекта не изменён; этот документ — единственный артефакт стадии. Прогоны, на которые он опирается: `audit.md` §5 (build exit 0, test exit 0 / 41 тест, detekt NO-SOURCE, ktlintCheck SKIPPED, `jacocoTestReport` exit 1) и dry-run `:src:app:build` (включает test/check/quarkusBuild).
