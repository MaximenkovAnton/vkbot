# Тестирование

Проект использует подход **Fake Objects** для тестирования, что обеспечивает высокую читаемость,
maintainability и надежность тестов.

## 🎯 Философия тестирования

### Fake Objects vs Mocks

**Почему Fake Objects?**

| Аспект | Mocks (Mockito) | Fake Objects |
|--------|----------------|--------------|
| **Настройка** | `when(mock.method()).thenReturn(value)` | `fakeObject.configuredResponse = value` |
| **Верификация** | `verify(mock, times(2)).method()` | `assert(fakeObject.calls.size == 2)` |
| **Читаемость** | Много boilerplate кода | Простые POJO объекты |
| **Отладка** | Сложно понять что происходит | Можно поставить breakpoint и посмотреть |
| **Refactoring** | Ломаются при изменении API | Compile-time проверка |
| **Реалистичность** | Поведение определяется тестом | Поведение близко к реальному |

**Пример проблемы с моками**:
```kotlin
// Сложно понять что происходит
when(mockService.process(any()))
    .thenReturn(response1)
    .thenReturn(response2)
    .thenThrow(RuntimeException())

verify(mockService, times(3)).process(argThat { 
    it.field1 == "expected" && it.field2.isNotEmpty() 
})
```

**То же самое с Fake Object**:
```kotlin
// Понятно и просто
val fakeService = FakeProcessingService()
fakeService.responses = listOf(response1, response2, Exception())

// После выполнения
assertEquals(3, fakeService.processCalls.size)
assertEquals("expected", fakeService.processCalls[0].field1)
assertTrue(fakeService.processCalls[0].field2.isNotEmpty())
```

## 🏗️ Структура тестовых объектов

### Организация fake объектов
```
src/test/kotlin/com/simarel/vkbot/objectProvider/fake/
├── adapter/
│   └── output/
│       ├── ai/FakeAiChatbotAnswerMessageOutputPort.kt
│       ├── mq/FakePublishEventOutputPort.kt
│       └── vk/FakeVkClient.kt
├── command/
│   ├── processor/FakeMessageAnswerTextGenerateCommand.kt
│   └── vkFacade/FakePublishEventCommand.kt
├── domain/
│   ├── FakeMessageProvider.kt        # Фабрики доменных объектов
│   └── FakeVoProvider.kt            # Фабрики Value Objects  
├── port/
│   ├── input/FakeMessageNewInputPort.kt
│   └── output/FakeVkSendMessageOutputProvider.kt
└── service/
    └── FakeUserAnswerAiService.kt
```

## 🔧 Паттерны Fake Objects

### 1. Базовый Fake Object

**Структура**:
```kotlin
class FakeMessageAnswerTextGenerateCommand : MessageAnswerTextGenerateCommand {
    val executeCalls = ConcurrentLinkedQueue<MessageAnswerTextGenerateCommandRequest>()
    
    override fun execute(request: MessageAnswerTextGenerateCommandRequest): MessageAnswerTextGenerateCommandResponse {
        executeCalls.add(request)  // Записываем вызов
        return MessageAnswerTextGenerateCommandResponse(FakeVoProvider.createMessageText())  // Возвращаем реалистичный ответ
    }
}
```

**Принципы**:
- **Записывать все входящие параметры** в коллекцию для проверки
- **Возвращать реалистичные объекты** через Provider'ы
- **Использовать thread-safe коллекции** (ConcurrentLinkedQueue)

### 2. Fake Object с настраиваемым поведением

```kotlin
class FakeVkClient(
    val vkResponseDto: VkResponseDto? = null  // Настраиваемый ответ
): VkClient {
    val sendMessageParameterCalls = ConcurrentLinkedQueue<SendMessageParameter>()

    override fun sendMessage(peerId: Long, message: String, rand: Int): VkResponseDto {
        sendMessageParameterCalls.add(SendMessageParameter(peerId, message, rand))
        return vkResponseDto ?: VkResponseDto(error = null)  // По умолчанию успешный ответ
    }

    data class SendMessageParameter(val peerId: Long, val message: String, val rand: Int)
}
```

**Использование в тесте**:
```kotlin
@Test
fun `send vk message error handling`() {
    // Given
    val errorResponse = VkResponseDto(VkError(error_code = 1, "error happened"))
    val vkClient = FakeVkClient(errorResponse)  // Настраиваем поведение
    val adapter = VkSendMessageOutputAdapter(vkClient)
    
    // When & Then
    assertThrows<VkException> {
        adapter.execute(request)
    }
    
    // Проверяем что вызов был сделан правильно
    assertEquals(1, vkClient.sendMessageParameterCalls.size)
    val call = vkClient.sendMessageParameterCalls.first()
    assertEquals(request.messageText.value, call.message)
}
```

### 3. Provider Pattern для Test Data

**FakeVoProvider** - фабрика Value Objects:
```kotlin
object FakeVoProvider {
    fun createDate(value: OffsetDateTime? = null) = Date(value ?: OffsetDateTime.now())
    fun createFromId(value: Long? = null) = FromId.of(value ?: Random.nextLong())
    fun createMessageText(value: String? = null) = MessageText.of(value ?: "Тестовое сообщение")
    // ...
}
```

**FakeMessageProvider** - фабрика доменных объектов:
```kotlin
object FakeMessageProvider {
    fun createMessage(
        date: Date = FakeVoProvider.createDate(),
        fromId: FromId = FakeVoProvider.createFromId(),
        // ... другие параметры с дефолтными значениями
    ): Message = Message(date, fromId, groupId, peerId, conversationMessageId, messageText)
}
```

**Преимущества Provider'ов**:
- **Reasonable defaults** - тест фокусируется только на важных полях
- **Переиспользование** - один провайдер для всех тестов
- **Type safety** - помогает при рефакторинге
- **Читаемость** - `FakeMessageProvider.createMessage(messageText = "test")` понятнее чем длинный конструктор

## 📝 Примеры тестов

### Unit Test с Fake Objects

```kotlin
class MessageAnswerTextGenerateCommandImplTest {
    @Test
    fun `execute command successfully`() {
        // Given
        val aiChatbotPort = FakeAiChatbotAnswerMessageOutputPort()
        val command = MessageAnswerTextGenerateCommandImpl(aiChatbotPort)
        val request = FakeMessageAnswerTextGenerateCommandProvider.createRequest()

        // When
        val result = command.execute(request)

        // Then
        assertEquals(1, aiChatbotPort.executeCalls.size)
        assertEquals(request.message, aiChatbotPort.executeCalls.first().message)
        assertNotNull(result.messageText)
    }
}
```

### Integration Test с множественными Fake Objects

```kotlin
class SendVkMessageCommandImplTest {
    @Test
    fun `execute command successfully`() {
        // Given
        val vkClient = FakeVkClient()  // Fake внешнего API
        val vkOutputAdapter = VkSendMessageOutputAdapter(vkClient)  // Реальный адаптер
        val command = SendVkMessageCommandImpl(vkOutputAdapter)  // Реальная команда
        val request = FakeSendVkMessageCommandProvider.createRequest()

        // When
        command.execute(request)

        // Then - проверяем что правильные данные дошли до VK API
        assertEquals(1, vkClient.sendMessageParameterCalls.size)
        val call = vkClient.sendMessageParameterCalls.first()
        assertEquals(request.message.value, call.message)
        assertEquals(request.peerId.value, call.peerId)
    }
}
```

## 🚀 Best Practices

### Именование
- **Fake** + оригинальное имя: `FakeVkClient`, `FakeMessageRepository`
- **Provider** для фабрик: `FakeMessageProvider`, `FakeVoProvider`  
- **Calls коллекции**: `executeCalls`, `sendMessageCalls`

### Thread Safety
```kotlin
// ✅ Правильно - thread-safe
val executeCalls = ConcurrentLinkedQueue<Request>()

// ❌ Неправильно - может быть race condition в параллельных тестах
val executeCalls = mutableListOf<Request>()
```

### Реалистичные данные
```kotlin
// ✅ Правильно - возвращаем осмысленные объекты
return MessageAnswerTextGenerateCommandResponse(FakeVoProvider.createMessageText())

// ❌ Неправильно - возвращаем заглушки
return MessageAnswerTextGenerateCommandResponse(MessageText.of(""))
```

### Настраиваемость
```kotlin
// ✅ Правильно - можно настроить поведение
class FakeAiService(val response: String = "default response")

// ❌ Неправильно - жестко зашитое поведение
class FakeAiService {
    override fun generateResponse() = "always the same"
}
```

## 🔍 Отладка тестов

### Простая отладка Fake Objects
1. **Breakpoint в fake object** - можно посмотреть все параметры
2. **Логирование вызовов**:
```kotlin
override fun execute(request: Request): Response {
    println("FakeService called with: $request")  // Временное логирование
    executeCalls.add(request)
    return response
}
```

3. **Inspection в IDE** - все данные видны в debugger'е

### Проблемы с моками
- Трудно понять почему тест упал
- Сложно отладить взаимодействие между объектами  
- Mock framework скрывает фактическое поведение

## 📊 Сравнение подходов

### Время разработки
- **Fake Objects**: Больше времени на создание, меньше на поддержку
- **Mocks**: Меньше времени на создание, больше на поддержку и отладку

### Надежность тестов
- **Fake Objects**: Реже ломаются при рефакторинге
- **Mocks**: Часто требуют обновления при изменениях

### Понятность для новых разработчиков  
- **Fake Objects**: Интуитивно понятны, код как книга
- **Mocks**: Требуют знание framework'а, его особенностей, и требуют много времени на исправление нестандартной ошибки

**Вывод**: Fake Objects требуют больше инвестиций вначале, но окупаются простотой поддержки и отладки в долгосрочной перспективе.
