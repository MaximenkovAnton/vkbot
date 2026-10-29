# VK Bot Application

A VK community bot: one Quarkus/Kotlin deployable that receives VK Callback webhooks, stores messages,
decides whether an AI answer is needed, generates answers with LangChain4j/Ollama, sends them back to the
VK API, and summarizes long conversations. The code follows hexagonal architecture: plain Kotlin behaviour
behind ports, the framework at the edge (ADR 0003).

## Features

- VK Callback API webhook with secret validation and a DMZ mapper for VK JSON
- Event-driven pipeline on RabbitMQ with a retry ladder and a final dead-letter queue
- Message/profile/summary storage in PostgreSQL (Liquibase migrations, handwritten jOOQ tables)
- AI answers via LangChain4j + Ollama, with web-search tools from Vane
- Conversation summarization with a configurable threshold and enabled-chat list
- In-process ports between modules of the same deployable (no loopback HTTP, ADR 0004)
- OpenTelemetry traces/metrics/logs
- Dockerfiles for JVM, legacy-jar, native and native-micro images
- JUnit 5 tests with Fake Objects, ArchUnit architecture tests, JaCoCo coverage threshold

## Technology stack

| Component | Technology |
|---|---|
| Framework | Quarkus 3.34.2 |
| Language / runtime | Kotlin 2.3.0 on JDK 21 |
| Build | Gradle 9.3.0 (Kotlin DSL wrapper) |
| Database | PostgreSQL + Liquibase + jOOQ 3.19.10 |
| Message broker | RabbitMQ (SmallRye Reactive Messaging) |
| AI | LangChain4j 1.8.4 (`quarkus-langchain4j-ollama`) |
| Observability | OpenTelemetry (OTLP), Micrometer |
| Testing | JUnit 5, RestAssured, WireMock, ArchUnit |
| Lint / format / coverage | detekt, ktlint, JaCoCo |

## Module structure

One deployable (`src/app`) and eleven Gradle modules (`settings.gradle.kts:22-32`):

```
src/
├── app/                    # @QuarkusMain, runtime config, composition root, integration tests
├── share/                  # domain models, value objects, contracts (ports, commands, events)
├── receiver/               # VK webhook entry point (DMZ)
├── persistence/            # PostgreSQL storage, REST query API, in-process persistence ports
├── processor/              # "does this message need an AI answer?"
├── ai/                     # AI answer, summarization, OCR command, Vane tools
├── vk-facade/              # VK API output, profile endpoints, in-process vk ports
├── infrastructure/         # RabbitMQ dispatcher, retry/DLQ, logging decorators
├── docker/                 # Dockerfile.jvm | legacy-jar | native | native-micro
└── testing/
    ├── arch-tests/         # ArchUnit: module, layer and build-graph rules
    ├── test-common/        # shared test configuration
    └── test-fixtures/      # Fake Objects and fake-data providers
```

Production dependency direction: every business module depends only on `share`; `app` depends on all of
them; `share` depends on nobody. The build fails on a wrong import (`docs/adr/0003-module-boundaries-and-layer-rules.md`).

## Prerequisites

- JDK 21 (the build pins a 21 toolchain)
- Docker (DevServices for PostgreSQL, RabbitMQ and Ollama; also required by `@QuarkusTest` app tests)
- A VK community with a callback server and an API token
- Optional: a Vane (Perplexica) instance for web-search tools; Ollama for AI (DevServices can start it)

## Configuration

The production configuration is `src/app/src/main/resources/application.yml` (+ `application-queues.yml`).
Values come from environment variables — never commit secrets (`.env` is git-ignored).

| Environment variable | Required | Description |
|---|---|---|
| `VK_SECRET` | yes | Callback secret from the VK community settings |
| `VK_CONFIRMATION_CODE` | yes | Confirmation code from VK |
| `VK_API_TOKEN` | yes | VK community API token |
| `VK_BOT_ID` | yes | Bot user id used by the answer-decision rule |
| `VANE_URL` | yes | Base URL of the Vane search API |
| `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` | in prod | PostgreSQL connection |
| `RABBITMQ_HOST` (and `RABBITMQ_PORT/USERNAME/PASSWORD` if needed) | in prod | RabbitMQ connection |
| `VK_BOT_MENTION` | no | Mention that triggers an answer (default `@simarel`) |
| `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT` | no | System prompts (defaults in `application.yml`) |
| `SUMMARY_ENABLED_CHATS` | yes | Non-empty comma-separated peer ids whose chats get summaries. An unset or empty value fails startup (`SRCFG00040`); the tests use `1,2,3` |
| `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE` | no | Summarization threshold and batch size (defaults `100`, `100`) |

Every key, its default and its consumer: `docs/development/configuration.md`.

## Building and running

```bash
# Build everything: compile, tests, detekt, ktlintCheck, coverage threshold, deployable
./gradlew clean build

# Local development with hot reload + DevServices (PostgreSQL, RabbitMQ, Ollama)
export VK_SECRET=... VK_CONFIRMATION_CODE=... VK_API_TOKEN=... VK_BOT_ID=... VANE_URL=http://localhost:3000 \
       SUMMARY_ENABLED_CHATS=1,2,3
./gradlew :src:app:quarkusDev
```

The deployable artifact is `src/app/build/quarkus-app/quarkus-run.jar`; `./gradlew build` also checks that no
second runner is produced.

### Docker

```bash
# JVM image (the confirmed path: Dockerfile.jvm copies src/app/build/quarkus-app)
./gradlew :src:app:quarkusBuild
docker build -f src/docker/Dockerfile.jvm -t vkbot:jvm .
docker run -p 8080:8080 \
  -e VK_SECRET=... -e VK_CONFIRMATION_CODE=... -e VK_API_TOKEN=... -e VK_BOT_ID=... \
  -e VANE_URL=... -e SUMMARY_ENABLED_CHATS=1,2,3 \
  -e QUARKUS_DATASOURCE_JDBC_URL=... -e QUARKUS_DATASOURCE_USERNAME=... \
  -e QUARKUS_DATASOURCE_PASSWORD=... -e RABBITMQ_HOST=... \
  vkbot:jvm
```

Native and legacy-jar images read from `src/app/build` too, but `.dockerignore` currently whitelists only
`src/app/build/quarkus-app/*` — see `docs/development/deployment.md` before building them.

## API endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/vk/callback` | POST | VK Callback webhook (confirmation, `message_new`, …) |
| `/persistence/messages/*`, `/persistence/summaries/*`, `/persistence/profiles/*` | GET/POST | Query/storage API (`PersistenceDataController`) |
| `/api/profiles/users/batch`, `/api/profiles/groups/batch` | GET | VK profile lookup (`VkFacadeController`) |
| `/q/health`, `/q/health/ready`, `/q/health/live` | GET | Quarkus health checks |

There is no `/metrics` endpoint and no `/health` (Quarkus uses the `/q` prefix). The health endpoints exist but
currently report **DOWN** (503): seven retry/DLQ channels are declared without a downstream consumer and the
messaging health check marks them `[KO]` (`SRMSG00208`); the webhook itself works. See
`docs/development/deployment.md`. Example webhook body:

```json
{
  "type": "message_new",
  "group_id": 123456,
  "secret": "your_secret",
  "object": {
    "message": {
      "date": 1756381457,
      "from_id": 123456,
      "peer_id": 2000000001,
      "conversation_message_id": 2670,
      "text": "@simarel Привет!"
    }
  }
}
```

## Message flow

```
VK webhook → receiver → MESSAGE_NEW → persistence (save + fetch profiles)
           → MESSAGE_RECEIVED → processor → MESSAGE_REQUIRE_ANSWER
           → ai → ANSWER_MESSAGE → vk-facade → VK API

Summary flow:
  persistence → SUMMARY_REQUESTED → ai → SUMMARY_READY → vk-facade → VK API
```

Events run on RabbitMQ with a five-step retry ladder and a final DLQ (`application-queues.yml`); synchronous
calls inside the deployable go through `share` ports (ADR 0004). Details: `docs/architecture/data-flow.md`.

## Testing

```bash
./gradlew test                              # all tests, incl. ai unit tests (Docker needed for app integration tests)
./gradlew :src:processor:test --tests "*MessageReceivedUsecaseInputTest*"   # one class (module-scoped)
./gradlew :src:testing:arch-tests:test      # architecture rules
./gradlew coverageReport coverageVerification   # aggregated report + threshold
```

Fake Objects (not mocks) live in `src/testing/test-fixtures` and module-local `testFixtures`; the coverage
threshold is `0.40` (measured `0.5043`, ratchet target `0.60`). Philosophy and examples:
`docs/development/testing.md`.

## Observability

OpenTelemetry is enabled for traces, metrics and logs (`quarkus.otel.*` in `application.yml`); Quarkus exports
via OTLP (default endpoint `localhost:4317`, so logs during tests show export errors when no collector runs).
Logging decorators wrap every command and port at TRACE level. No Prometheus scrape endpoint is configured.

## Documentation

- [Architecture overview](docs/architecture/overview.md)
- [Module structure](docs/architecture/modules.md)
- [Data flow](docs/architecture/data-flow.md)
- [Architecture patterns](docs/architecture/patterns.md)
- [Testing guide](docs/development/testing.md)
- [Configuration](docs/development/configuration.md)
- [Deployment](docs/development/deployment.md)
- [Architecture decision records](docs/adr/)
- [Documentation index](docs/README.md)
- [Agent constitution](AGENTS.md)

## Contributing

1. Fork the repository, create a feature branch.
2. Keep the module/layer rules (`./gradlew build` enforces them).
3. Run `./gradlew build` before opening a pull request; follow conventional commits.

## License

The repository does not declare a license yet. Add a `LICENSE` file and state the license here before
distributing the project.
