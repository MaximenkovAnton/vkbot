# VK Bot Application

A VK community bot: one Quarkus/Kotlin deployable that receives VK events through the Bots Long Poll API,
stores messages, decides whether an AI answer is needed, generates answers with LangChain4j via a vLLM
OpenAI-compatible API, sends them back to the VK API, and summarizes long conversations. The code follows
hexagonal architecture: plain Kotlin behaviour behind ports, the framework at the edge (ADR 2026-10-28-module-boundaries-and-layer-rules).

## Features

- VK Bots Long Poll receiver (`groups.getLongPollServer` + `a_check`, `ts` position in PostgreSQL) with a DMZ mapper for VK JSON
- Event-driven pipeline on RabbitMQ with a retry ladder and a final dead-letter queue
- Message/profile/summary storage in PostgreSQL (Liquibase migrations, handwritten jOOQ tables)
- AI answers via LangChain4j against a vLLM OpenAI-compatible API, with web-search tools from Vane
- Conversation summarization with a configurable threshold and enabled-chat list
- Intent routing: exact `/help`/`/помощь` and the `help` intent get a fixed help text; the `news` intent ("what happened in the chat") gets an LLM digest over a period parsed from the request text, with a fixed refusal for periods longer than a week and a fixed answer when there is nothing for the period
- Daily greetings on a cron schedule (`greetings.cron`, 00:00 by default; New Year, Programmer Day, birthdays) with AI text, template fallback and an at-most-once claim per chat
- In-process ports between modules of the same deployable (no loopback HTTP, ADR 2026-10-28-in-process-ports-instead-of-loopback-rest)
- OpenTelemetry traces/metrics/logs
- Dockerfiles for JVM, legacy-jar, native and native-micro images
- JUnit 5 tests with Fake Objects, ArchUnit architecture tests, JaCoCo coverage threshold

## Technology stack

| Component | Technology |
|---|---|
| Framework | Quarkus 3.40.1.1 |
| Language / runtime | Kotlin 2.3.0 on JDK 21 |
| Build | Gradle 9.3.0 (Kotlin DSL wrapper) |
| Database | PostgreSQL + Liquibase + jOOQ 3.19.10 |
| Message broker | RabbitMQ (SmallRye Reactive Messaging) |
| AI | LangChain4j 1.23.0 (`quarkus-langchain4j-openai` 1.15.0) |
| Observability | OpenTelemetry (OTLP), Micrometer |
| Testing | JUnit 5, RestAssured, WireMock, ArchUnit |
| Lint / format / coverage | detekt, ktlint, JaCoCo |

## Module structure

One deployable (`src/app`) and twelve Gradle modules (`settings.gradle.kts:22-33`):

```
src/
├── app/                    # @QuarkusMain, runtime config, composition root, integration tests
├── share/                  # domain models, value objects, contracts (ports, commands, events)
├── receiver/               # VK Bots Long Poll entry point (DMZ)
├── persistence/            # PostgreSQL storage, REST query API, in-process persistence ports
├── processor/              # "does this message need an AI answer?"
├── ai/                     # AI answer, summarization, OCR command, Vane tools
├── vk-facade/              # VK API output, profile endpoints, in-process vk ports
├── greetings/              # cron-scheduled greeting run: holidays, birthdays, orchestration, span
├── infrastructure/         # RabbitMQ dispatcher, retry/DLQ, logging decorators
├── docker/                 # Dockerfile.jvm | legacy-jar | native | native-micro
└── testing/
    ├── arch-tests/         # ArchUnit: module, layer and build-graph rules
    ├── test-common/        # shared test configuration
    └── test-fixtures/      # Fake Objects and fake-data providers
```

Production dependency direction: every business module depends only on `share`; `app` depends on all of
them; `share` depends on nobody. The build fails on a wrong import (`docs/adr/2026-10-28-module-boundaries-and-layer-rules.md`).

## Prerequisites

- JDK 21 (the build pins a 21 toolchain)
- Docker (DevServices for PostgreSQL and RabbitMQ; also required by `@QuarkusTest` app tests)
- A VK community with the Bots Long Poll API enabled and an API token
- A vLLM OpenAI-compatible endpoint for AI (`AI_BASE_URL`, `AI_CHAT_MODEL`)
- Optional: a Vane (Perplexica) instance for web-search tools

## Configuration

The production configuration is `src/app/src/main/resources/application.yml` (+ `application-queues.yml`).
Values come from environment variables — never commit secrets (`.env` is git-ignored).

| Environment variable | Required | Description |
|---|---|---|
| `VK_GROUP_ID` | yes | Positive community id used by `groups.getLongPollServer` |
| `VK_API_TOKEN` | yes | VK community API token |
| `VK_BOT_ID` | yes | Bot user id used by the answer-decision rule |
| `VK_LONGPOLL_ENABLED` | no | Enables Bots Long Poll polling at startup (default `true`) |
| `VANE_URL` | yes | Base URL of the Vane search API |
| `DECISION_URL` | yes | Root of the System One decision server (no `/v1`, no `/v1/systemone`) |
| `DECISION_SPAM_THRESHOLD` | no | Spam probability threshold for the notice (default `0.9`) |
| `DECISION_SPAM_REPLY_ENABLED` | no | Spam-reply kill-switch for the decision stage; `false` disables only the spam notice (default `true`). The intent-stage decision call after `requiredAnswer` is not governed by it |
| `DECISION_JAILBREAK_THRESHOLD` | no | Prompt-injection-attempt probability threshold for the jailbreak notice (default `0.85`) |
| `DECISION_JAILBREAK_REPLY_ENABLED` | no | Jailbreak-reply kill-switch for the decision stage; `false` disables only the jailbreak notice (default `true`). With both decision-stage kill-switches `false` the model is not called for the spam/jailbreak stage |
| `AI_BASE_URL` | yes | Root of the OpenAI-compatible LLM API, including `/v1` |
| `AI_CHAT_MODEL` | yes | Chat model name; one model serves answers, summaries, OCR, greetings and the "what's new" digest |
| `AI_API_KEY` | no | LLM API key (default `dummy`; needed only when the server enables authorization) |
| `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD` | in prod | PostgreSQL connection |
| `RABBITMQ_HOST` (and `RABBITMQ_PORT/USERNAME/PASSWORD` if needed) | in prod | RabbitMQ connection |
| `VK_BOT_MENTION` | no | Mention that triggers an answer (default `@simarel`) |
| `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`, `AI_GREETING_SYSTEM_PROMPT`, `NEWS_DIGEST_SYSTEM_PROMPT` | no | System prompts (defaults in `application.yml`) |
| `SUMMARY_ENABLED_CHATS` | no | Comma-separated peer ids whose chats get summaries. An unset or empty value disables summarization and the app still starts; the tests use `1,2,3` |
| `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE` | no | Summarization threshold and batch size (defaults `100`, `100`) |
| `GREETINGS_CRON` | no | Cron of the greeting run (default `0 0 0 * * ?`, i.e. 00:00 in `GREETINGS_TIMEZONE`); `off`/`disabled` disables the schedule |
| `GREETINGS_TIMEZONE` | no | Time zone of the greeting schedule (default `Europe/Minsk`) |

Every key, its default and its consumer: `docs/development/configuration.md`.

## Building and running

```bash
# Build everything: compile, tests, detekt, ktlintCheck, coverage threshold, deployable
./gradlew clean build

# Local development with hot reload + DevServices (PostgreSQL, RabbitMQ)
export VK_GROUP_ID=... VK_API_TOKEN=... VK_BOT_ID=... VANE_URL=http://localhost:3000 \
       AI_BASE_URL=http://localhost:8000/v1 AI_CHAT_MODEL=... \
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
  -e VK_GROUP_ID=... -e VK_API_TOKEN=... -e VK_BOT_ID=... \
  -e VANE_URL=... -e AI_BASE_URL=... -e AI_CHAT_MODEL=... -e SUMMARY_ENABLED_CHATS=1,2,3 \
  -e QUARKUS_DATASOURCE_JDBC_URL=... -e QUARKUS_DATASOURCE_USERNAME=... \
  -e QUARKUS_DATASOURCE_PASSWORD=... -e RABBITMQ_HOST=... \
  vkbot:jvm
```

Native and legacy-jar images read from `src/app/build` too, but `.dockerignore` currently whitelists only
`src/app/build/quarkus-app/*` — see `docs/development/deployment.md` before building them.

## API endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/persistence/messages/*`, `/persistence/summaries/*`, `/persistence/profiles/*` | GET/POST | Query/storage API (`PersistenceDataController`) |
| `/api/profiles/users/batch`, `/api/profiles/groups/batch` | GET | VK profile lookup (`VkFacadeController`) |
| `/q/health`, `/q/health/ready`, `/q/health/live` | GET | Quarkus health checks |

`receiver` has no HTTP endpoint: VK events arrive through Bots Long Poll polling, and `POST /vk/callback`
returns 404 (ADR 2026-11-05-bots-long-poll-transport). There is no `/metrics` endpoint and no `/health` (Quarkus uses the `/q` prefix). The
health endpoints exist but currently report **DOWN** (503): seven retry/DLQ channels are declared without a
downstream consumer and the messaging health check marks them `[KO]` (`SRMSG00208`); the polling loop itself
runs independently of HTTP health probes. See `docs/development/deployment.md`.

## Message flow

```
VK long poll → receiver → MESSAGE_NEW → persistence (save + fetch profiles)
            → MESSAGE_RECEIVED → processor
            → processor: human in a group chat, at least one decision-stage kill-switch on
              → one decision call with four questions (spam, class, intent, jailbreak)
              → spamProbability ≥ DECISION_SPAM_THRESHOLD
                → ANSWER_MESSAGE (spam notice) → vk-facade → VK API
              → otherwise jailbreakProbability ≥ DECISION_JAILBREAK_THRESHOLD
                → ANSWER_MESSAGE (prompt-injection attempt notice) → vk-facade → VK API
            → otherwise, after requiredAnswer, by intent:
              → help: ANSWER_MESSAGE (fixed help text) → vk-facade → VK API
              → news: NEWS_DIGEST_REQUESTED → ai (period from the text → stored summaries/messages → LLM digest)
                → ANSWER_MESSAGE → vk-facade → VK API
              → general: MESSAGE_REQUIRE_ANSWER → ai → ANSWER_MESSAGE → vk-facade → VK API
```

The decision stage (spam and jailbreak) runs for a human in a group chat when at least one of its two
kill-switches is on; a kill-switch turns off the corresponding reaction, not the question, so the model
gets all four questions and the stage call stops only when both are `false`. The spam branch wins when both
thresholds are exceeded. The detector analyses only the author's `messageText`; forwarded content and
attachments are not checked (the forward block added by `vk-facade` is a delivery feature, not detection).
The processor computes the message intent (`help`/`news`/`general`) as the third decision-model question
and routes it after `requiredAnswer`; the intent-stage call (lazy, when the stage did not run) is not
governed by the spam/jailbreak kill-switches. Exact `/help` and `/помощь` (no arguments) are recognised
deterministically, without an extra model call. The `news` branch is served by the `chat-news-digest`
capability (`docs/capabilities/chat-news-digest.md`): it reads stored summaries and messages through
in-process ports and never writes to `summaries`, so `SUMMARY_REQUESTED`/`SUMMARY_READY` are not involved.

Summary flow:
  persistence → SUMMARY_REQUESTED → ai → SUMMARY_READY → vk-facade → VK API

Greetings flow (scheduler, not an event):
  greetings.cron → greetings → persistence (plans + claim per chat) → ai (text) → vk-facade (plain send) → VK API
```

Events run on RabbitMQ with a five-step retry ladder and a final DLQ (`application-queues.yml`); synchronous
calls inside the deployable go through `share` ports (ADR 2026-10-28-in-process-ports-instead-of-loopback-rest). Details: `docs/architecture/data-flow.md`.

## Testing

```bash
./gradlew test                              # all tests, incl. ai unit tests (Docker needed for app integration tests)
./gradlew :src:processor:test --tests "*MessageReceivedUsecaseInputTest*"   # one class (module-scoped)
./gradlew :src:testing:arch-tests:test      # architecture rules
./gradlew coverageReport coverageVerification   # aggregated report + threshold
```

Fake Objects (not mocks) live in `src/testing/test-fixtures` and module-local `testFixtures`; the coverage
threshold is `0.40` (measured `0.5687`, ratchet target `0.60`). Philosophy and examples:
`docs/development/testing.md`.

## Observability

OpenTelemetry is enabled for traces, metrics and logs (`quarkus.otel.*` in `application.yml`); Quarkus exports
via OTLP (default endpoint `localhost:4317`, so logs during tests show export errors when no collector runs).
Logging decorators wrap every command and port at TRACE level. The only own trace is the `greetings.daily-run`
span of the daily greeting run. No Prometheus scrape endpoint is configured.

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
