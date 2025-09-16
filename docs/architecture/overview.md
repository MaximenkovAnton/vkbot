# Обзор архитектуры

VK Bot построен на основе **Clean Architecture** (Чистая архитектура) с применением **Event-driven** подхода, обеспечивающего высокую степень развязки, тестируемости и гибкости системы.

## 🏗️ Архитектурные принципы

### Clean Architecture
Проект строго следует принципам чистой архитектуры Роберта Мартина:

1. **Независимость от фреймворков** - бизнес-логика не зависит от Quarkus
2. **Тестируемость** - можно тестировать без внешних зависимостей
3. **Независимость от UI** - VK API можно заменить на Telegram или другой
4. **Независимость от базы данных** - PostgreSQL можно заменить на MongoDB
5. **Независимость от внешних сервисов** - AI провайдера можно поменять без изменения бизнес-логики

### Слои архитектуры

```
┌─────────────────────────────────────────┐
│           Infrastructure                │  ← Frameworks, Drivers
├─────────────────────────────────────────┤
│          Interface Adapters             │  ← Controllers, Gateways
├─────────────────────────────────────────┤
│          Application Business           │  ← Use Cases
├─────────────────────────────────────────┤
│         Enterprise Business             │  ← Entities
└─────────────────────────────────────────┘
```

### Event-Driven Architecture

Система построена на событиях, что обеспечивает:
- **Асинхронную обработку** - каждый этап может выполняться независимо
- **Масштабируемость** - легко добавлять новые обработчики событий
- **Отказоустойчивость** - события можно переобрабатывать при сбоях
- **Наблюдаемость** - полный audit trail всех операций

## 🎯 Ключевые компоненты

### Domain Layer (Домен)
- **Entities**: `Message` - основная бизнес-сущность
- **Value Objects**: `MessageText`, `PeerId`, `FromId` и др. - типобезопасные примитивы
- **Events**: `MESSAGE_RECEIVED`, `MESSAGE_REQUIRE_ANSWER`, `SEND_MESSAGE`

### Application Layer (Приложение)
- **Use Cases**: координируют бизнес-процессы
- **Ports**: интерфейсы для входящих и исходящих операций
- **Commands**: инкапсулируют операции изменения данных

### Interface Adapters (Адаптеры интерфейсов)
- **Input Adapters**: REST контроллеры для VK webhook'ов
- **Output Adapters**: клиенты для VK API, AI сервисов
- **Event Processors**: обработчики доменных событий

### Infrastructure (Инфраструктура)
- **Message Broker**: RabbitMQ для событий
- **Web Framework**: Quarkus
- **Configuration**: YAML конфигурация

## 🔄 Основной поток обработки

```mermaid
sequenceDiagram
    participant VK as VK API
    participant Receiver as Receiver Module
    participant MQ as RabbitMQ
    participant Processor as Processor Module
    participant AI as AI Service
    participant Facade as VK Facade

    VK->>Receiver: POST /vk/callback
    Receiver->>Receiver: Security check
    Receiver->>Receiver: Map VK → Domain
    Receiver->>MQ: Publish MESSAGE_RECEIVED
    MQ->>Processor: Consume MESSAGE_RECEIVED
    Processor->>MQ: Publish MESSAGE_REQUIRE_ANSWER
    MQ->>Processor: Consume MESSAGE_REQUIRE_ANSWER
    Processor->>AI: Generate response
    AI-->>Processor: AI response
    Processor->>MQ: Publish SEND_MESSAGE
    MQ->>Facade: Consume SEND_MESSAGE
    Facade->>VK: Send message
```

## 🛡️ Безопасность и валидация

- **Security Decorator** - проверка секретного ключа VK
- **Value Objects** - валидация на уровне типов
- **Input Validation** - проверка входящих данных

## 🎨 Преимущества архитектуры

### Гибкость
- Легко заменить VK на другую платформу
- Можно поменять AI провайдера без изменения бизнес-логики
- Простое добавление новых типов событий

### Тестируемость  
- Каждый слой тестируется изолированно
- Fake objects вместо сложных моков
- Быстрые unit-тесты без внешних зависимостей

### Масштабируемость
- Асинхронная обработка через события
- Горизонтальное масштабирование обработчиков
- Независимое развертывание компонентов

### Maintainability
- Четкое разделение ответственности
- Слабая связанность между модулями
- Явные интерфейсы между слоями
