# Архитектурные паттерны

Проект использует набор проверенных архитектурных паттернов, обеспечивающих гибкость, тестируемость и maintainability кода.

## Основные паттерны

### Hexagonal Architecture (Ports & Adapters)

**Назначение**: Изоляция бизнес-логики от внешних зависимостей

**Реализация**:
```kotlin
// Port (интерфейс)
interface MessageNewInputPort: Port<MessageNewInputPortRequest, MessageNewInputPortResponse>

// Adapter (реализация)
@ApplicationScoped
open class NewMessageUsecaseInput(
    val publishEventCommand: PublishEventCommand
): MessageNewInputPort {
    override fun execute(request: MessageNewInputPortRequest): MessageNewInputPortResponse {
        // Бизнес-логика
    }
}
```

**Преимущества**:
- Бизнес-логика не зависит от фреймворков
- Легко заменяемые внешние интеграции
- Высокая тестируемость через фейки портов

### Command Pattern

**Назначение**: Инкапсуляция операций в объекты для их повторного использования и логирования

**Базовые интерфейсы**:
```kotlin
fun interface Command<REQ: CommandRequest, RESP: CommandResponse> {
    fun execute(request: REQ): RESP
}

interface CommandRequest
interface CommandResponse
```

**Пример реализации**:
```kotlin
@ApplicationScoped
class MessageAnswerTextGenerateCommandImpl(
    val aiChatbotAnswerMessageOutputPort: AiChatbotAnswerMessageOutputPort,
): MessageAnswerTextGenerateCommand {
    override fun execute(request: MessageAnswerTextGenerateCommandRequest): MessageAnswerTextGenerateCommandResponse {
        val response = aiChatbotAnswerMessageOutputPort.execute(
            AiChatbotAnswerMessageOutputPortRequest(request.message)
        )
        return MessageAnswerTextGenerateCommandResponse(response.responseMessage)
    }
}
```

**Преимущества**:
- Uniform interface для всех операций
- Легкое добавление декораторов (логирование, метрики)
- Возможность queuing и batching команд

### Decorator Pattern

**Назначение**: Динамическое добавление поведения к объектам

**Логирование команд**:
```kotlin
@Decorator
open class CommandLoggingDecorator<REQ: CommandRequest, RESP: CommandResponse>(
    @Delegate val delegate: Command<REQ, RESP>,
    val objectMapper: ObjectMapper
): Command<REQ, RESP> {
    override fun execute(request: REQ): RESP {
        if(Log.isTraceEnabled()) {
            Log.trace("Incoming command request: ${objectMapper.writeValueAsString(request)}")
        }
        val response = delegate.execute(request)
        if(Log.isTraceEnabled()) {
            Log.trace("Outcoming command response: ${objectMapper.writeValueAsString(response)}")
        }
        return response
    }
}
```

**Безопасность VK роутера**:
```kotlin
@Decorator
class VkMediatorRouterSecurityDecorator(): VkMediatorRouter {
    @Inject
    @Delegate
    lateinit var delegate: VkMediatorRouter

    @ConfigProperty(name = "vk.secret")
    lateinit var secret: String

    override fun callback(event: JsonObject): String {
        val secret = event.getString("secret")
        if(secret == this.secret) {
            return delegate.callback(event)
        } else {
            throw AccessDeniedException("Secret code not provided or incorrect")
        }
    }
}
```

### Value Object Pattern

**Назначение**: Типобезопасность и инкапсуляция простых значений

**Реализация**:
```kotlin
@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String) = MessageText(value)
    }
    override fun toString(): String {
        return value
    }
}

@JvmInline
value class PeerId(val value: Long) {
    companion object {
        fun of(value: Long) = PeerId(value)
    }
    override fun toString(): String {
        return value.toString()
    }
}
```

**Преимущества**:
- Compile-time безопасность типов
- Нет overhead благодаря `@JvmInline`
- Семантическая ясность кода
- Централизованная валидация

### Event Sourcing / CQRS Pattern

**Назначение**: Разделение команд и запросов, async обработка через события

**События домена**:
```kotlin
enum class Event {
    MESSAGE_RECEIVED(),
    MESSAGE_REQUIRE_ANSWER(),
    SEND_MESSAGE(),
}
```

**Event Publisher**:
```kotlin
@ApplicationScoped
class PublishEventOutputAdapter(
    @Channel("events-exchange") val emitter: Emitter<String>,
    val objectMapper: ObjectMapper,
): PublishEventOutputPort {
    override fun execute(request: PublishEventOutputPortRequest): PublishEventOutputPortResponse {
        val metadata = OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey(request.event.name)
            .build()

        emitter.send(Message.of(
            objectMapper.writeValueAsString(request.payload.value),
            Metadata.of(metadata)
        ))
        return response
    }
}
```

**Event Consumer**:
```kotlin
@ApplicationScoped
class EventDispatcher {
    @Incoming("all-events-queue")
    @RunOnVirtualThread
    fun dispatch(message: Message<String>): CompletionStage<Void> {
        val routingKey = message.metadata?.get(IncomingRabbitMQMetadata::class.java)?.get()?.routingKey
        val processor = routingKey?.let { processors[it] }
        processor?.process(message.payload)
        return response
    }
}
```
## DMZ Pattern (Demilitarized Zone)

**Назначение**: Изоляция внешних API от внутренней доменной модели

**Реализация в receiver модуле**:
```kotlin
// Внешний формат (VK API)
VkCallbackEvent.MESSAGE_NEW 
    ↓
// DMZ маппинг  
PublishVkEventCommand.mapVkEventToEvent()
    ↓
// Внутренний формат (Domain)
Event.MESSAGE_RECEIVED
```

**Преимущества**:
- Изменения в VK API не влияют на домен
- Централизованная валидация входящих данных
- Security layer на границе системы

## Factory Pattern

**Назначение**: Создание доменных объектов с валидацией

**Message Factory**:
```kotlin
data class Message(/*...*/) {
    companion object {
        fun of(
            date: OffsetDateTime,
            fromId: Long,
            groupId: Long,
            peerId: Long,
            conversationMessageId: Long,
            messageText: String,
        ): Message = Message(
            date = Date.of(date),
            fromId = FromId.of(fromId),
            groupId = GroupId.of(groupId),
            peerId = PeerId.of(peerId),
            conversationMessageId = ConversationMessageId.of(conversationMessageId),
            messageText = MessageText.of(messageText),
        )
    }
}
```

## Observer Pattern (через Events)

**Назначение**: Уведомление заинтересованных компонентов о событиях

**Реализация через RabbitMQ**:
- Каждое событие может иметь множество подписчиков
- Loose coupling между publisher и subscriber
- Легкое добавление новых обработчиков

**Пример**:
```kotlin
// MESSAGE_RECEIVED может обрабатываться:
// 1. MessageNewEventProcessor (основной поток)
// 2. MetricsCollector (метрики)
// 3. AuditLogger (аудит)
// 4. SpamDetector (антиспам)
```

## Преимущества паттернов

### Гибкость
- Strategy для AI провайдеров
- Decorator для cross-cutting concerns
- Repository для разных БД

### Тестируемость
- Ports для изоляции внешних зависимостей
- Command для unit-тестирования бизнес-логики
- Factory для создания test data

### Масштабируемость
- Event Sourcing для async обработки
- Chain of Responsibility для pipeline
- Observer для fan-out паттернов

### Maintainability
- Value Objects для domain clarity
- DMZ для API changes isolation

Эти и другие паттерны работают синергично, создавая гибкую и расширяемую архитектуру,
способную адаптироваться к изменяющимся требованиям бизнеса.
