# AGENTS.md - Coding Guidelines for AI Agents

Essential information for AI coding agents working with this Kotlin/Quarkus codebase.

## Key Facts for Agents

- **Stack**: Quarkus, Kotlin, RabbitMQ, LangChain4j, Java 21
- **Testing**: Fake Objects (not mocks/Mockito), located in `objectProvider/fake/`
- **Main modules**: receiver, persistence, processor, ai, vkFacade, infrastructure, share

## Architecture

### Modules
- **receiver** — VK webhook entry point (`POST /vk/callback`), DMZ layer for VK API JSON parsing
- **persistence** — message/profile/summary storage, REST API for data queries
- **processor** — message filtering: determines if message requires AI answer
- **ai** — AI answer generation + conversation summarization (LangChain4j)
- **vkFacade** — VK API exit point: send messages, fetch profiles
- **share** — shared domain: `Message`, `Event`, `Port`, `Command`, Value Objects, output port interfaces
- **infrastructure** — RabbitMQ, logging decorators

### Data Flow
```
VK webhook → receiver → MESSAGE_NEW (RabbitMQ)
           → persistence → MESSAGE_RECEIVED (save + fetch profiles)
           → persistence → SUMMARY_REQUESTED (RabbitMQ)
           → processor → MESSAGE_REQUIRE_ANSWER (RabbitMQ)
           → ai → ANSWER_MESSAGE (RabbitMQ)
           → vkFacade → VK API (sendMessage)

Summary flow:
  MESSAGE_RECEIVED → persistence → SUMMARY_REQUESTED (RabbitMQ)
  → ai → SUMMARY_READY (RabbitMQ)
  → vkFacade → VK API
```

### Key Patterns

| Pattern | Application |
|---------|-------------|
| **Ports & Adapters** | Interfaces in `port/`, implementations in `adapter/` |
| **Command** | `Command<REQ, RESP>` — atomic reusable operations |
| **UseCase** | Business process coordination, implements InputPort |
| **Decorator** | `@Decorator` — Security, Logging |
| **Value Objects** | `@JvmInline value class` — `MessageText`, `PeerId`, `FromId` |
| **DMZ** | Isolation of external VK API from domain model in `receiver` |

### Architecture Patterns

#### Hexagonal Architecture (Ports & Adapters)
- Strict separation between domain logic and external integrations
- Domain models and interfaces in `share` module
- Implementation in adapter modules (`receiver`, `vkFacade`, etc.)

#### Mandatory Flow (NON-NEGOTIABLE)
Every request must follow this exact path. No shortcuts, no bypasses:

```
adapter input → port input → usecase → command → command impl → port output → adapter output
```

**EXPLICITLY FORBIDDEN:**

| Violation | Example | Why |
|---|---|---|
| EventProcessor → Command directly | `SummaryReadyEventProcessor` injecting `SendVkMessageCommand` | Bypasses usecase business logic |
| Input adapter → OutputPort | `VkFacadeController` injecting `VkProfileOutputPort` | Reverses dependency direction |
| Adapter → jOOQ/concrete repo directly | `PersistenceDataController` injecting `JooqMessageRepository` | No abstraction layer |
| Usecase → Usecase via InputPort | `ProcessSummaryUsecase` calling `GenerateSummaryInputPort` | Usecase should call Command, not another InputPort |
| Usecase → OutputPort directly | Usecase calling persistence port without Command layer | Missing command abstraction |
| Domain VO wrapping infra type | `VkEvent` wrapping `jakarta.json.JsonObject` | Infrastructure leaks into domain |
| Command layer importing adapter DTO | `MessageMapper` in `command/` importing `adapter.input.dto.*` | Violates Dependency Inversion |
| `@Tool` on Command class | `VaneSearchCommandImpl` with `@Tool` methods | Adapter→Command reverse dependency |

#### OutputPort Contract (NON-NEGOTIABLE)
Every output port MUST extend `OutputPort<REQ, RESP>` with a single `execute(request: REQ): RESP` method. Raw multi-method interfaces are forbidden — split each method into its own port:

```kotlin
// WRONG — raw interface, multiple methods
interface PersistenceDataOutputPort {
    fun findMessagesBefore(peerId: Long, ...): List<StoredMessage>
    fun findLastSummary(peerId: Long): Summary?
}

// CORRECT — one port per operation
interface FindStoredMessagesBeforePort : OutputPort<FindStoredMessagesBeforePort.Request, FindStoredMessagesBeforePort.Response> {
    data class Request(val peerId: Long, val beforeConversationMessageId: Long, val limit: Int) : OutputPortRequest
    data class Response(val messages: List<StoredMessage>) : OutputPortResponse
}
```

#### Command Contract (NON-NEGOTIABLE)
Every command MUST implement `Command<REQ, RESP>`. Loose classes with arbitrary `execute()` signatures are forbidden:

```kotlin
// WRONG
class IsRequireAnswerCommand(...) {
    fun execute(message: Message): Boolean  // no Command<REQ,RESP>
}

// CORRECT
interface IsRequireAnswerCommand : Command<IsRequireAnswerRequest, IsRequireAnswerResponse> {
    data class IsRequireAnswerRequest(val message: Message) : CommandRequest
    data class IsRequireAnswerResponse(val requiresAnswer: Boolean) : CommandResponse
}
```

#### @Tool Annotations on Adapters, Not Commands
LangChain4j `@Tool` methods must live in adapter-layer classes (e.g., `VaneSearchToolAdapter`), not in command implementations. The adapter delegates to `Command.execute()`. This keeps the single entry point into the command and avoids reverse dependencies from output adapters into the command layer.

#### Port Granularity
- **Single-method ports**: Every output port exposes exactly one operation via `execute(Request): Response`
- Multi-method repository-style interfaces are split into individual ports
- Naming: `{Verb}{Noun}Port` — e.g., `FindStoredMessagesBeforePort`, `SaveMessagePort`

#### Command Pattern
```kotlin
interface Command<REQ: CommandRequest, RESP: CommandResponse> {
    fun execute(request: REQ): RESP
}
```

#### Module Structure
```
module/
├── adapter/
│   ├── input/     # Entry points (REST controllers, event listeners)
│   └── output/    # Integrations (databases, external APIs)
├── command/       # Reusable atomic actions
├── domain/        # Business logic and domain models
├── port/          
│   ├── input/     # Inbound interfaces
│   └── output/    # Outbound interfaces
├── usecase/       # Coordination of business processes
└── config/        # Module configuration
```

### Testing Approach

#### Fake Objects Over Mocks
We use Fake Objects instead of mocking frameworks for better readability and maintainability:

```kotlin
// Preferred: Fake Object
class FakeVkClient(
    val response: VkResponseDto? = null
): VkClient {
    val calls = ConcurrentLinkedQueue<SendMessageParams>()
    
    override fun sendMessage(peerId: Long, message: String, rand: Int): VkResponseDto {
        calls.add(SendMessageParams(peerId, message, rand))
        return response ?: VkResponseDto(null)
    }
}
```

### Error Handling

1. **Custom Exceptions**:
   ```kotlin
   class ValidationException(message: String) : VkBotAppException(message)
   ```

### Dependency Injection

Quarkus CDI is used for dependency injection:
- `@ApplicationScoped` for singleton services
- `@Singleton` for stateless services
- Constructor injection preferred over field injection

### Code Quality Rules

1. **Detekt Configuration**:
   - Located in `detekt-config.yml`
   - Zero tolerance for issues (`build.maxIssues = 0`)
   - Focus on complexity, naming, and style rules

2. **KtLint Formatting**:
   - IntelliJ IDEA code style defaults
   - No trailing commas on call sites
   - Final newlines required

3. **Complexity Limits**:
   - Maximum function length: 60 lines
   - Maximum class length: 600 lines
   - Maximum parameter count: 8 parameters
   - Maximum cyclomatic complexity: 15

## Build/lint/test commands

```bash
./gradlew quarkusDev     # dev with hot-reload + DevServices (RabbitMQ)
./gradlew test           # tests with Fake Objects
./gradlew test --tests "*ClassName*"  # run specific test class
./gradlew build          # build the application
./gradlew check          # run all checks (tests + linting)
./gradlew detekt         # run linting checks
./gradlew ktlintFormat   # run code formatting
./gradlew clean          # clean build artifacts

# Docker builds
docker build -f src/docker/Dockerfile.jvm -t vkbot .      # JVM Docker
docker build -f src/docker/Dockerfile.native -t vkbot .   # Native Docker
```

## Code Style Guidelines

### Kotlin Conventions
- Use Kotlin official coding conventions
- Apply IntelliJ IDEA Kotlin style defaults
- No wildcard imports (explicit imports only)
- Maximum line length: 120 characters

### Naming Conventions
- Classes: PascalCase (`MessageProcessor`)
- Functions/Variables: camelCase (`processMessage()`, `userId`)
- Constants: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- Packages: lowercase with dots (`com.simarel.vkbot.receiver`)

### Value Objects
Use inline value classes for type safety:
```kotlin
@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String) = MessageText(value)
    }
}
```

### Null Safety
- Prefer non-nullable types
- Use `?.`, `?:`, and `let` for safe calls
- Avoid `!!` operator unless absolutely necessary

## Testing with Fake Objects

Instead of mocks, we use Fake Objects located in `src/testing/test-fixtures`:

```kotlin
// Fake Object records calls and returns realistic responses
class FakeVkClient : VkClient {
    val calls = ConcurrentLinkedQueue<SendMessageParameter>()
    override fun sendMessage(peerId: Long, message: String, rand: Int): VkResponseDto {
        calls.add(SendMessageParameter(peerId, message, rand))
        return VkResponseDto(error = null)  // Realistic response
    }
}
// Provider for test data 
object FakeVoProvider {
    fun createMessageText(value: String? = null) = MessageText.of(value ?: "Test")
    fun createPeerId(value: Long? = null) = PeerId.of(value ?: Random.nextLong())
}
```

## Configuration

| Variable | Description |
|----------|-------------|
| `VK_SECRET` | VK callback secret |
| `VK_CONFIRMATION_CODE` | Group confirmation code |
| `VK_API_TOKEN` | Group API token |

## AI Integration

LangChain4j with Russian responses. Interface: `UserAnswerAiService` with `@SystemMessage`.

## Documentation References

For deeper understanding of the architecture and patterns, refer to the detailed documentation:

- `docs/architecture/overview.md` - General architectural concepts
- `docs/architecture/modules.md` - Detailed module descriptions and interactions
- `docs/architecture/patterns.md` - In-depth explanation of architectural patterns
- `docs/architecture/data-flow.md` - Detailed data flow diagrams and explanations
- `docs/development/testing.md` - Comprehensive guide to the Fake Objects testing approach
- `docs/development/configuration.md` - Detailed configuration options
- `docs/development/deployment.md` - Deployment strategies and options

This guide provides essential information for AI agents to effectively contribute to this codebase while maintaining consistency with existing patterns and practices.
