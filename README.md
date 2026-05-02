# VK Bot Application

A Quarkus-based VK bot that processes incoming messages and provides AI-powered responses. The project follows Clean Architecture principles with clear separation of concerns, ensuring maintainability and scalability.

## Features

- Clean Architecture with domain-driven design and Ports & Adapters pattern
- VK API integration with webhook callbacks
- AI-powered responses using LangChain4j and Ollama
- Event-driven architecture with RabbitMQ
- PostgreSQL persistence with Liquibase migrations and jOOQ type-safe queries
- Chat summarization feature with configurable thresholds
- OpenTelemetry observability (traces, metrics, logs)
- Retry strategies with dead letter queues (DLQ)
- Docker support for multiple deployment options (JVM, native)
- RESTful API endpoints for VK webhook integration
- Comprehensive testing with Fake Objects pattern
- ArchUnit architecture tests

## Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Quarkus 3.34.2 (JDK 21) |
| Language | Kotlin 2.3.0 |
| Database | PostgreSQL + Liquibase + jOOQ |
| Message Broker | RabbitMQ |
| AI Integration | LangChain4j + Ollama |
| Observability | OpenTelemetry, Prometheus, Micrometer |
| Testing | JUnit 5, RestAssured, WireMock, ArchUnit |
| Build Tool | Gradle with Kotlin DSL |

## Module Structure

The project follows a multi-module Gradle structure:

```
src/
├── ai/                    # AI integration with LangChain4j
├── app/                   # Main application entry point
├── infrastructure/        # Cross-cutting infrastructure (logging, events, config)
├── persistence/           # Database persistence layer
├── processor/             # Business logic and message processing
├── receiver/              # VK webhook receiver (DMZ pattern)
├── share/                 # Shared domain models and value objects
├── vk-facade/             # VK API client and outgoing messages
├── docker/                # Docker configurations
└── testing/               # Testing modules
    ├── arch-tests/        # Architecture tests (ArchUnit)
    ├── test-common/       # Common test utilities
    └── test-fixtures/     # Fake Objects for testing
```

## Prerequisites

- Java 21 (OpenJDK or GraalVM for native builds)
- Docker (for containerized deployment)
- VK developer account and group
- PostgreSQL (or use DevServices for development)
- RabbitMQ (or use DevServices for development)
- Ollama (for AI responses, or use DevServices)

## Setup and Configuration

### 1. Create VK Group and Configure Callback

1. Create a VK group (if not already created)
2. Go to **Community Management → API**
3. Set up a callback server:
   - **URL**: `https://your-domain.com/vk/callback`
   - **Secret**: Choose a secure secret key
   - **Confirmation code**: VK generates this automatically
4. Get the group's API token from the API section

### 2. Configure Application

The application uses **environment variables** for configuration:

| Environment Variable | Required | Description |
|----------------------|----------|-------------|
| `VK_SECRET` | Yes | Secret key from VK callback setup |
| `VK_CONFIRMATION_CODE` | Yes | Confirmation code from VK (auto-generated) |
| `VK_API_TOKEN` | Yes | VK group API token |
| `DATABASE_URL` | No | JDBC URL (default: DevServices) |
| `DB_USERNAME` | No | Database username (default: postgres) |
| `DB_PASSWORD` | No | Database password (default: password) |
| `SUMMARY_ENABLED_CHATS` | No | Comma-separated list of chat IDs for summary feature |
| `SUMMARY_THRESHOLD` | No | Minimum messages before generating summary (default: 100) |
| `SUMMARY_BATCH_SIZE` | No | Max messages to include in summary (default: 100) |
| `SUMMARY_SYSTEM_PROMPT` | No | Custom prompt for summary generation |

Alternatively, you can configure via `application.yml` in the `app` module.

> **Security Note**: Never commit credentials to version control. Use environment variables or secret management solutions.

## Building and Running

### Local Development

```bash
# Run with hot reloading (uses DevServices for PostgreSQL, RabbitMQ)
./gradlew :src:app:quarkusDev
```

DevServices automatically starts required services in Docker containers during development.

### Build for Production

```bash
# Build JVM application
./gradlew build

# Build native executable (requires GraalVM)
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false
```

### Docker Deployment

#### JVM Mode (recommended for most deployments)

```bash
# Build Docker image
docker build -f src/docker/Dockerfile.jvm -t vkbot:jvm .

# Run container
docker run -p 8080:8080 \
  -e VK_SECRET=your_secret \
  -e VK_CONFIRMATION_CODE=your_code \
  -e VK_API_TOKEN=your_token \
  -e DATABASE_URL=jdbc:postgresql://host:5432/vkbot \
  vkbot:jvm
```

#### Native Mode (optimized for minimal resource usage)

```bash
# Build native image
docker build -f src/docker/Dockerfile.native -t vkbot:native .

# Run container
docker run -p 8080:8080 \
  -e VK_SECRET=your_secret \
  -e VK_CONFIRMATION_CODE=your_code \
  -e VK_API_TOKEN=your_token \
  vkbot:native
```

**Native mode advantages**:
- Startup time: ~0.1s (vs ~2s JVM)
- Memory usage: ~50MB (vs ~200MB JVM)
- Image size: ~100MB (vs ~300MB JVM)

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/vk/callback` | POST | Handles incoming VK events |
| `/health` | GET | Health check endpoint |
| `/health/ready` | GET | Readiness probe |
| `/health/live` | GET | Liveness probe |
| `/metrics` | GET | Prometheus metrics |

### Example VK Callback Request

```json
{
  "type": "message_new",
  "object": {
    "message": {
      "text": "Привет!",
      "from_id": 123456,
      "peer_id": 789012,
      "conversation_message_id": 1,
      "date": 1756381457
    }
  },
  "group_id": 123456,
  "secret": "your_secret"
}
```

## Message Flow

```
VK Webhook → receiver → RabbitMQ → processor → RabbitMQ → ai
                                                            ↓
VK API ← vk-facade ← RabbitMQ ← processor ← persistence ←─┘
```

1. **receiver**: Validates and receives VK webhooks
2. **processor**: Routes messages and coordinates processing
3. **ai**: Generates AI responses using Ollama and chat summaries
4. **persistence**: Stores message history with jOOQ type-safe queries
5. **vk-facade**: Sends responses and summaries back to VK

## Testing

The project uses **Fake Objects** pattern for testing instead of mocks:

```bash
# Run all tests
./gradlew test

# Run architecture tests
./gradlew :src:testing:arch-tests:test

# Run with coverage
./gradlew jacocoTestReport
```

See [docs/development/testing.md](docs/development/testing.md) for detailed testing philosophy.

## Observability

### OpenTelemetry Traces

Distributed tracing is enabled across all message flows. Traces include:
- VK webhook reception
- Message processing steps
- AI generation
- Database operations
- VK API calls

### Metrics (Prometheus)

Available at `/metrics`:
- JVM metrics (memory, GC, threads)
- HTTP request metrics
- RabbitMQ message rates
- Custom business metrics

### Logging

Structured JSON logging in production, readable format in development.

## Documentation

Detailed documentation is available in the `docs/` directory:

- [Architecture Overview](docs/architecture/overview.md)
- [Module Structure](docs/architecture/modules.md)
- [Data Flow](docs/architecture/data-flow.md)
- [Architecture Patterns](docs/architecture/patterns.md)
- [Testing Guide](docs/development/testing.md)
- [Configuration](docs/development/configuration.md)
- [Deployment](docs/development/deployment.md)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Follow conventional commits format and ensure all tests pass.

## License

[Specify your license here]
