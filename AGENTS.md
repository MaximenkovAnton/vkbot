# AGENTS.md — constitution for this repository

This file is the first thing every agent session must read. It describes the code **as built**.
If a statement here contradicts the code, the code wins: fix this file in the same change.

## 1. What this project is

A VK community bot: one Quarkus/Kotlin deployable that receives VK Callback webhooks, stores messages,
decides whether an AI answer is needed, generates answers with LangChain4j/Ollama, sends them back to the
VK API, and summarizes long conversations. Runtime path:
`VK webhook → receiver → RabbitMQ → persistence → processor → ai → vk-facade → VK API`.

## 2. Stack and versions (source of truth)

| Layer | Version | Where it is pinned |
|---|---|---|
| JDK | 21 (Temurin 21.0.5) | `kotlin { jvmToolchain(21) }` in all 11 modules; `.sdkmanrc`; CI `java-version: '21'` |
| Gradle | 9.3.0 wrapper, Kotlin DSL | `gradle/wrapper/gradle-wrapper.properties:3` |
| Kotlin | 2.3.0 | `settings.gradle.kts:14` |
| Quarkus | 3.34.2 (`quarkus-bom`) | `gradle.properties:3-7`; the only `enforcedPlatform` is `build.gradle.kts:44-51` |
| LangChain4j | 1.8.4 | `gradle.properties:8` |
| DB | PostgreSQL + Liquibase + jOOQ 3.19.10 (codegen off, tables handwritten) | `src/persistence/build.gradle.kts:41,47,55,89-125` |
| Broker | RabbitMQ (`quarkus-messaging-rabbitmq`) | `src/infrastructure/build.gradle.kts:18-20` |
| Tests | JUnit 5, ArchUnit 1.3.0, WireMock 3.9.1, Awaitility 4.2.0 | `src/testing/arch-tests/build.gradle.kts:11`, `src/app/build.gradle.kts:45-56` |
| Lint / format | detekt 1.23.8, ktlint plugin 13.1.0 | `gradle.properties:9-10`, `build.gradle.kts:5-6` |
| Coverage | JaCoCo 0.8.14 | aggregated task `coverageReport` (see §6) |

Do not bump a version and leave the ADRs untouched. Version policy: ADR 0001.

## 3. Module map (11 Gradle modules, one deployable)

`settings.gradle.kts:22-32`; production dependency allowlist: `GradleDependenciesTest.kt:35-65`.
The single deployable is `:src:app` (ADR 0002). The root project is an aggregator with conventions only.

| Module | Owns | Public entry points | Depends on (production) |
|---|---|---|---|
| `src:share` | Domain and contracts: `Message`, `StoredMessage`, `Summary`, `VkUserProfile`/`VkGroupProfile`, value objects, `Event`, `Command`, `Port`/`InputPort`/`OutputPort`, `EventProcessor`, 9 persistence ports, 2 vk ports, event payloads | — (contracts only) | — |
| `src:receiver` | VK Callback entry, secret check, DMZ JSON→domain mapping, publishes `MESSAGE_NEW` | `POST /vk/callback` (`VkMediatorRouterImpl.kt:16-40`), `ReceiveMessageInputPort` | share |
| `src:persistence` | Message/profile/summary storage (jOOQ + Liquibase), query REST API, in-process implementation of share persistence ports, profile fetching | `POST /persistence/*` (`PersistenceDataController.kt:21-127`), `MessageNewEventProcessor.kt:12-22`, `PersistenceDataInProcessAdapter.kt:17-144` | share |
| `src:processor` | Answer-decision logic, publishes `MESSAGE_REQUIRE_ANSWER` | `MessageReceivedEventProcessor.kt:12-22` | share |
| `src:ai` | AI answers, summarization, OCR command, Vane tools; consumes `MESSAGE_REQUIRE_ANSWER`/`SUMMARY_REQUESTED`, publishes `ANSWER_MESSAGE`/`SUMMARY_READY` | `MessageRequireAnswerEventProcessor.kt:12-22`, `SummaryRequestedEventProcessor.kt:12-28` | share |
| `src:vk-facade` | VK API output, profile endpoints, in-process implementation of share vk ports; consumes `ANSWER_MESSAGE`/`SUMMARY_READY` | `GET /api/profiles/*` (`VkFacadeController.kt:14-32`), `SendMessageEventProcessor.kt:12-22`, `SummaryReadyEventProcessor.kt:18-57`, `VkProfileInProcessAdapter.kt:8-36` | share |
| `src:infrastructure` | RabbitMQ dispatcher, retry ladder + final DLQ, event publishing, logging decorators, Jackson config | `@Incoming("all-events-queue")` (`EventDispatcher.kt:63-84`), `PublishEventOutputAdapter.kt:15-33` | share |
| `src:app` | Deployable, `@QuarkusMain`, runtime config, integration tests, composition root | `Application.kt:6-12` | receiver, persistence, processor, ai, vk-facade, infrastructure, share |
| `src:testing:test-fixtures` | Shared fakes and fake-data providers | — | share, vk-facade (tests only) |
| `src:testing:test-common` | Shared test `application.yaml` | — | — |
| `src:testing:arch-tests` | ArchUnit: module rules, layer rules, build-graph allowlist | `ModuleDependenciesTest`, `LayerDependenciesTest`, `GradleDependenciesTest` | all modules + app (tests only) |

Layers inside every business module (ADR 0003):

```
<module>/domain/        entities, VOs, errors — no framework, no other layer
<module>/port/input/    use-case contracts called by the outside
<module>/port/output/   what the logic needs from outside (no implementations)
<module>/usecase/       port/input implementations over domain + port/output
<module>/command/       atomic Command<REQ,RESP> operations
<module>/adapter/input/ REST, @Incoming consumers, in-process adapters
<module>/adapter/output/ DB (jOOQ), VK API, AI, Vane, JSON
<module>/config/        CDI producers / module configuration
```

`share` has no `adapter` layer. Composition root is CDI + `:src:app`; explicit producers only in
`receiver/config/UsecaseConfiguration.kt:12-25`, `receiver/config/ConstantConfiguration.kt:9-15`,
`persistence/config/JooqConfig.kt:11-20`, `infrastructure/config/MapperConfig.kt:9-18`.

## 4. Dependency direction and forbidden imports (enforced by the build)

```
share -> nobody
receiver|processor|ai|persistence|vk-facade|infrastructure -> share
app -> all six business modules + share
```
Enforced by `LayerDependenciesTest` (L1–L5, one `@Test` per rule), `ModuleDependenciesTest` (L6 and the
module rules) and the `GradleDependenciesTest` allowlist. A violation fails `./gradlew build`, not a review.

Forbidden — each rule names the test that catches it:

| Violation | Caught by |
|---|---|
| `domain` imports a framework (`io.quarkus`, `jakarta`, `org.jooq`, `com.fasterxml`, `io.smallrye`, `org.eclipse`, `dev.langchain4j`) or another layer | L1 |
| `port` imports an adapter/usecase/command/config | L2 |
| `usecase`/`command` imports an adapter, jOOQ, Jackson, JAX-RS, `jakarta.json`, LangChain4j, SmallRye, `io.quarkus` (DI annotations `jakarta.inject`/`jakarta.enterprise`/`jakarta.transaction`/`org.eclipse.microprofile.config.inject` are allowed) | L3 |
| input adapter goes to `command`/`usecase`/`adapter.output` instead of `port.input` | L4 |
| output adapter goes to `adapter.input`/`usecase` (calling `command` is allowed: `VaneSearchToolAdapter`) | L5 |
| a module imports another module outside the allowlist | `ModuleDependenciesTest`, `GradleDependenciesTest` |
| testing modules used in a production configuration | `GradleDependenciesTest` |

Mandatory flow (non-negotiable):

```
adapter input -> port input -> usecase -> command -> command impl -> port output -> adapter output
```

Contracts that must keep their shape:

- Every output port is a `fun interface OutputPort<REQ, RESP>` with one `execute` (`share/port/output/OutputPort.kt:7-13`);
  one operation per port. Raw multi-method repository interfaces are forbidden.
- Every command is `Command<REQ, RESP>` (`share/command/Command.kt:3-8`); loose `execute()` classes are forbidden.
- Every event consumer implements `EventProcessor` (`share/port/input/EventProcessor.kt:5-8`).
- `@Tool` (LangChain4j) lives on adapter classes only: `VaneSearchToolAdapter.kt:10-32` delegates to
  `VaneSearchCommand`. Never annotate a command with `@Tool`.
- Cross-module synchronous calls use `share` ports implemented in-process (ADR 0004); never a loopback HTTP
  call, never another module's use case directly.

## 5. Configuration

One production config: `src/app/src/main/resources/application.yml` + `application-queues.yml`.
Required env: `VK_SECRET`, `VK_CONFIRMATION_CODE`, `VK_API_TOKEN`, `VK_BOT_ID`, `VANE_URL`,
`SUMMARY_ENABLED_CHATS` (non-empty comma-separated peer ids), `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`, `RABBITMQ_HOST`
(and `RABBITMQ_PORT/USERNAME/PASSWORD` if needed). `summary.enabled-chats: ${SUMMARY_ENABLED_CHATS:}`
(`application.yml:14`) expands to the empty String when the variable is unset, and Quarkus rejects that value
(`SRCFG00040`), so the variable is required and must not be empty. Details and every key:
`docs/development/configuration.md` and ADR 0006. Never commit secrets; `.env` is git-ignored.

## 6. Commands that exist and were run (this pass)

| Command | What it does | Result in this pass |
|---|---|---|
| `./gradlew build` | compiles, tests, detekt, ktlintCheck, coverage threshold, capability map, `:src:app:quarkusBuild` | `BUILD SUCCESSFUL`, exit 0 |
| `./gradlew clean build` | same from scratch | `BUILD SUCCESSFUL in 2m 4s`, 168 tasks, exit 0 |
| `./gradlew test` | JUnit 5 tests (needs Docker for DevServices in `app` tests) | 130 tests, 0 failures, exit 0 |
| `./gradlew :src:<module>:test --tests "*ClassName*"` | one test class, module-scoped (a root-level `test --tests` fails on a module with no match) | works; used for single-class runs (this pass: `:src:processor:test --tests "*MessageReceivedUsecaseInputTest*"`) |
| `./gradlew detekt` | detekt over all 11 modules, `maxIssues: 0` | `BUILD SUCCESSFUL`, exit 0 |
| `./gradlew ktlintCheck` | ktlint format check over all modules | `BUILD SUCCESSFUL`, exit 0 |
| `./gradlew ktlintFormat` | formatter (not a gate) | task exists (`--dry-run` lists it); not applied, code is already formatted |
| `./gradlew coverageReport` | aggregated JaCoCo HTML+XML in `build/reports/jacoco/coverageReport/` | exit 0 |
| `./gradlew coverageVerification` | fails below `coverageMin = 0.40` (measured line ratio 0.5043) | exit 0 |
| `./gradlew :src:testing:arch-tests:test` | module, layer and build-graph rules plus the `TestDataFactoriesTest` guard (18 tests) | `BUILD SUCCESSFUL`, exit 0 |
| `./gradlew :src:app:quarkusBuild` | builds the single artifact `src/app/build/quarkus-app/quarkus-run.jar` | exit 0 |
| `./gradlew :src:app:quarkusDev` | local dev with hot reload + DevServices | started in 17.6 s with the env of §7; webhook smoke green (`test` 200, wrong secret 403); `/q/health` returns 503 (7 channels without downstream); needs Docker |
| `./gradlew clean` | removes build outputs | works |
| `./scripts/check-capabilities.sh` | capability-map check: entry-point and test paths, statuses, ADR ids (`docs/capabilities/`) | `OK: capability map paths, test paths, statuses and ADR ids resolve`, exit 0 |
| `./gradlew capabilitiesCheck` | runs the capability-map check as part of `check` (and therefore `build`) | `BUILD SUCCESSFUL`, exit 0 |

Not supported: `./gradlew jacocoTestReport` — the per-module task in `:src:app` fails a Gradle validation
(`:src:app:jandex` output is not declared as its input). Use `coverageReport`/`coverageVerification`.
There is no root `quarkusDev`/`quarkusBuild`: the deployable lives in `:src:app` only.

## 7. How to run locally

```bash
export VK_SECRET=... VK_CONFIRMATION_CODE=... VK_API_TOKEN=... VK_BOT_ID=... VANE_URL=http://localhost:3000 \
       SUMMARY_ENABLED_CHATS=1,2,3
./gradlew :src:app:quarkusDev
```

`SUMMARY_ENABLED_CHATS` is the non-empty comma-separated list of peer ids whose chats are summarized
(`summary.enabled-chats`, `ai/command/summary/ProcessSummaryCommandImpl.kt:18-34`). It is required: an unset or
empty value expands `${SUMMARY_ENABLED_CHATS:}` (`application.yml:14`) to the empty String, and Quarkus'
converter rejects it (`SRCFG00040`), so the application does not start. This exact command was run in the setup
pass with `SUMMARY_ENABLED_CHATS=1,2,3`: the app started in 17.6 s, `POST /vk/callback` with the configured
secret returned `test` (200) and a wrong secret returned 403.

Docker is needed for DevServices (PostgreSQL, RabbitMQ, Ollama; LGTM for observability). The webhook is
`POST http://localhost:8080/vk/callback` with a JSON body carrying `secret` and `type`.
Health: `/q/health`, `/q/health/ready`, `/q/health/live` exist, but they currently report **DOWN**: the incoming
channels `retry-*-queue`, `dlq-final-queue` and `summary-ready-queue` have no downstream consumer
(`SRMSG00208: has no downstreams`), so the messaging checks list them as `[KO]`. Treat health as informational
until those channels are excluded from health checks. There is no `/health` and no `/metrics` endpoint.
Tests: `./gradlew test` starts DevServices itself; `@QuarkusTest` app tests also need Docker.

## 8. Testing: fakes, not mocks

- No mocking framework in the project. Fakes live in `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/`
  and in module-local test fixtures under `<module>/src/testFixtures` (`ai`, `receiver`, `processor`, `persistence`).
- A fake records calls in a `ConcurrentLinkedQueue` and returns a realistic response:
  `FakeVkClient.kt:9-41`, `FakePublishEventCommand.kt:8-16`, `FakePublishEventOutputPort.kt:8-16`.
- Test data comes only from `*Provider` factories (naming convention `Fake*Provider`; persistence factories
  are `PersistenceData*Provider`): common types (`share`, `vk-facade`) in
  `src/testing/test-fixtures/.../testfixtures/` (`FakeVoProvider.kt:18-69`, `FakeModelProvider.kt`,
  `FakeVkFacadeProvider.kt`), `ai`/`persistence`/`receiver`/`processor` types in `<module>/src/testFixtures`
  (`FakeAnswerProvider.kt`, `FakeSummaryProvider.kt`, `FakeVaneProvider.kt`,
  `PersistenceDataRequestProvider.kt`, `PersistenceDataResponseProvider.kt`,
  `FakePublishVkEventCommandProvider.kt`).
- Direct `Type(...)`/`Type.of(...)` in `src/*/src/test/**` is forbidden for `share` domain models, VOs and
  events, adapter DTOs under `**/adapter/output/**/dto/**`, and port/command request/response types.
  The exceptions are the type's own test (`<Type>Test.kt`; for a nested contract, the owner's test
  `<Owner>Test.kt`), error exceptions and raw JSON bodies of integration tests.
- Every parameter of a `create*` factory method has a realistic default; a test passes only the fields it
  asserts on. Adding a field to a type means adding a default parameter to its factory — the tests do not
  change. The guard is `TestDataFactoriesTest` in `:src:testing:arch-tests`.
- Test names are backtick sentences describing the check (`ReceiveMessageUsecaseTest.kt:14-29`).
  Unit tests are plain JUnit 5 classes without Quarkus; integration tests use `@QuarkusTest`
  (`VkWebhookIntegrationTest.kt:27-30`) plus `@QuarkusTestResource(WireMockTestResource::class)`.
- In-process adapter tests are the template for new adapters: record the `port.input` request and assert the
  mapping (`PersistenceDataInProcessAdapterTest.kt:24-41`).
- Coverage: aggregate line ratio over 8 business modules, `src/testing/*` excluded; threshold 0.40 today,
  ratchet target 0.60 (ADR 0005). A test that "passes" without its command output is not evidence.

## 9. Errors and logging

- Errors: the base type is `VkBotAppException` with `status(): ExceptionStatus`
  (`share/domain/exception/VkBotAppException.kt:3-11`, `ExceptionStatus.kt:3-8`). Concrete mapped exceptions
  live next to their module's entry point: `receiver/domain/exception/ValidationException.kt:6-12`
  → `VALIDATION_FAILED`, `receiver/domain/exception/AccessDeniedException.kt:6-8` → `ACCESS_DENIED`,
  `vk-facade/.../adapter/output/vk/exception/VkException.kt:6-8` → `EXTERNAL_FAILURE`.
- HTTP mapping is one place: `receiver/adapter/input/vk/ExceptionHandler.kt:10-39` (`@ServerExceptionMapper`):
  `VkBotAppException` with validation → 400, access denied → 403, external/unknown → 503.
- `share/domain/exception/ValidationException.kt:3` is a plain `RuntimeException` thrown by value objects
  (`PeerId.kt:22`); it is **not** a `VkBotAppException`, so it falls to the generic mapper
  (`ExceptionHandler.kt:31-38`) → 503, not 400. That is the current behaviour; changing it is a code task.
- Async failures are not swallowed: `EventDispatcher.dispatch` catches processing exceptions and moves the
  message along the retry ladder (`EventDispatcher.kt:76-82,90-111`); final stop is `dlq-final` with no TTL.
  Retry levels and TTLs: `src/app/src/main/resources/application-queues.yml:1-32`.
- Logging: `java.lang.System.Logger` in `usecase`/`command` (framework-free business layer); Quarkus `Log` in
  adapters and infrastructure. Logging decorators wrap every `Command` and `Port`:
  `CommandLoggingDecorator.kt:11-28`, `UsecaseLoggingDecorator.kt:11-28`; they log only at TRACE.
- Do not log secrets, tokens or full webhook payloads.

## 10. Code style (what the gates check)

- Kotlin official style + IntelliJ defaults; ktlint config is `.editorconfig` (trailing commas on call sites
  are **allowed**: `ij_kotlin_allow_trailing_comma_on_call_site = true`), max line length 120, explicit imports.
- detekt limits: `maxIssues: 0` (`detekt-config.yml:2`), cyclomatic complexity 15, long method 60, large class 600,
  long parameter list 8 (function and constructor), too many functions 11 per file/class (`detekt-config.yml:108-172`).
  A baseline exists per module; do not add new findings to it. `excludes` in `detekt-config.yml` are anchored
  on source sets (`**/src/test/**`); a broad glob on the checkout directory name (for example `**/test/**`) is
  forbidden — it silently disables the rules locally. The guard is `DetektExcludesPathTest`.
- Kotlin conventions: `data class` for value types, `@JvmInline value class` for domain VOs
  (`MessageText.kt:3-12`), sealed interfaces for closed request/response sets
  (`PersistenceDataInputPort.kt:12-55`), non-null by default, avoid `!!`.
- No comments unless the neighbouring code already documents itself that way; the ADR/plan carries decisions.

## 11. What never to do

1. Never add a second Quarkus runner or another deployable; the artifact is `:src:app`.
2. Never let a domain/usecase/command import an adapter, jOOQ, Jackson, JAX-RS, LangChain4j or `io.quarkus`.
3. Never let an input adapter query storage or call a repository directly; go through `port.input`.
4. Never call another module's use case or repository directly instead of a `share` port.
5. Never add a loopback HTTP call between modules of this deployable.
6. Never add a module outside the allowlist without updating `GradleDependenciesTest`, `ModuleDependenciesTest`
   and ADR 0003.
7. Never annotate a command with `@Tool`; tools live in adapters.
8. Never commit secrets, `.env`, build outputs, or edit an applied Liquibase changeset (add the next one).
9. Never delete an accepted ADR: add a successor with `supersedes` and keep `docs/README.md` current.
10. Never claim a gate exists without its passing (and, for a gate, failing) command output.
11. Do not document as existing: `/metrics`, `/health`, `DATABASE_URL`/`DB_USERNAME`/`DB_PASSWORD`,
    `jacocoTestReport` or `SEND_MESSAGE`. None of them exist in this codebase (the coverage gate is
    `coverageReport`/`coverageVerification`).

## 12. Documents

- `docs/architecture/overview.md` — style, layers, ADR ids, runtime topology.
- `docs/architecture/modules.md` — every module: responsibility, public API, dependencies.
- `docs/architecture/patterns.md` — patterns in use and where they live in code.
- `docs/architecture/data-flow.md` — real event and call paths, failure modes.
- `docs/development/testing.md` — test strategy, fakes, coverage policy.
- `docs/development/configuration.md` — every config key, default and effect.
- `docs/development/deployment.md` — artifact, images, env, rollback.
- `docs/adr/` — accepted decisions 0001–0006.
- `docs/capabilities/` — product record: `index.md` with one row per capability plus one file per
  capability (status, entry points, owned data, journey, shared ownership, ADR and AC ids, test paths),
  checked by `./scripts/check-capabilities.sh` inside `check`.
