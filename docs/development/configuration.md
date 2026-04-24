# Конфигурация

Подробное руководство по настройке и конфигурированию VK Bot приложения.

## Основные параметры

### VK API конфигурация

```yaml
vk:
  secret: ${VK_SECRET:<secret>}                    # Секретный ключ для webhook'ов
  confirmation-code: ${VK_CONFIRMATION_CODE:123456} # Код подтверждения группы
  api:
    version: 5.131                                 # Версия VK API
    token: ${VK_API_TOKEN}                        # Токен доступа группы
```

**Получение параметров VK**:

1. **Создание группы**:
   - Перейдите в раздел "Сообщества" → "Создать сообщество"
   - Выберите тип сообщества и заполните информацию

2. **Настройка API**:
   - Управление сообществом → API → Ключи доступа
   - Создайте ключ с правами на сообщения

3. **Настройка Callback API**:
   - Управление сообществом → API → Callback API
   - **URL сервера**: `https://your-domain.com/vk/callback`
   - **Секретный ключ**: генерируется автоматически VK - скопируйте его
   - **Строка подтверждения**: генерируется автоматически VK - скопируйте её
   - **API токен**: используйте токен, созданный в п.2

**Важно**: Секретный ключ, строка подтверждения и токен генерируются ВКонтакте автоматически. Пользователь должен только подставить эти значения в конфигурацию приложения.

### Environment Variables

| Переменная | Обязательная | Описание | Пример |
|------------|--------------|----------|---------|
| `VK_SECRET` | Да           | Секретный ключ VK callback | `my_super_secret_key` |
| `VK_CONFIRMATION_CODE` | Да            | Код подтверждения VK | `123456` |
| `VK_API_TOKEN` | Да            | API токен VK группы | `vk1.a.abc123...` |
| `DATABASE_URL` | Нет            | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/vkbot` |
| `DB_USERNAME` | Нет            | Имя пользователя БД | `postgres` |
| `DB_PASSWORD` | Нет            | Пароль БД | `password` |
| `RABBITMQ_HOST` | Нет            | Хост RabbitMQ | `localhost` |

## Quarkus конфигурация

### База данных
```yaml
quarkus:
  datasource:
    db-kind: postgresql
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}
    jdbc:
      url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/vkbot}

  liquibase:
    migrate-at-start: true
    change-log: db/changelog/db.changelog-master.yaml

  hibernate-orm:
    schema-management:
      strategy: validate  # Liquibase управляет схемой
```

### RabbitMQ конфигурация
```yaml
quarkus:
  rabbitmq:
    devservices:
      enabled: true                    # Автозапуск для разработки
      image-name: rabbitmq:latest
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}

mp:
  messaging:
    incoming:
      all-events-queue:
        connector: smallrye-rabbitmq
        exchange:
          name: all-events
          type: topic                  # Поддержка routing по event name
        queue:
          name: queue
        dead-letter-exchange: retry-5s-exchange  # DLX для ошибок
      retry-5s-queue:
        connector: smallrye-rabbitmq
        exchange:
          name: retry-5s-exchange
        queue:
          name: retry-5s
          arguments:
            x-message-ttl: 5000                     # 5 секунд
            x-dead-letter-exchange: all-events
            x-dead-letter-routing-key: queue
      # ... retry-30s-queue, retry-1m-queue, retry-15m-queue, retry-1h-queue
      dlq-final-queue:
        connector: smallrye-rabbitmq
        exchange:
          name: dlq-final-exchange
        queue:
          name: dlq-final
    outgoing:
      events-exchange:
        connector: smallrye-rabbitmq
        exchange:
          name: all-events
```

### REST Client (VK API)
```yaml
quarkus:
  rest-client:
    scope: jakarta.inject.Singleton    # Переиспользование клиента
    logging:
      scope: request-response         # Логирование HTTP запросов
    vk:
      url: https://api.vk.ru/method   # Base URL VK API
      headers:
        Authorization: Bearer ${vk.api.token}
      connect-timeout: 5000           # Таймаут подключения (мс)
      read-timeout: 30000            # Таймаут чтения (мс)
```

## Логирование и мониторинг

### Structured JSON логи
```yaml
quarkus:
  logging:
    json: 
      enabled: true                   # JSON формат для production
      exclude-keys: timestamp,level   # Исключить дублирующие поля
  
  # Log levels
  log:
    level: INFO
    category:
      "com.simarel.vkbot": DEBUG     # Подробное логирование приложения
      "io.smallrye.reactive.messaging": INFO
      "org.hibernate": WARN
```

**Пример структурированного лога**:
```json
{
  "timestamp": "2025-09-16T09:47:18.123Z",
  "level": "INFO",
  "logger": "com.simarel.vkbot.receiver.adapter.input.vk.VkMediatorRouterImpl",
  "message": "Incoming VK callback",
  "mdc": {
    "traceId": "abc123",
    "userId": "173308266",
    "peerId": "2000000001"
  }
}
```

### OpenTelemetry трассировка
```yaml
quarkus:
  opentelemetry:
    enabled: true
    tracer:
      exporter:
        otlp:
          endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
    propagators: tracecontext,baggage # Передача trace между сервисами
```

### Метрики Prometheus
```yaml
quarkus:
  micrometer:
    registry-enabled-default: false
    export:
      prometheus:
        enabled: true
        path: /metrics                # Endpoint для Prometheus
```

## Профили конфигурации

### Development Profile
```yaml
"%dev":
  quarkus:
    hibernate-orm:
      schema-management:
        strategy: drop-and-create     # Пересоздание БД каждый раз (исключительно для локальной разработки!)
    log:
      level: DEBUG                    # Подробное логирование
      console:
        json: false                   # Читаемые логи в консоли
    rabbitmq:
      devservices:
        enabled: true                 # Автозапуск RabbitMQ
```

### Test Profile  
```yaml
"%test":
  vk:
    api:
      token: test_token              # Заглушка для тестов
```

### Production Profile
```yaml
"%prod":
  quarkus:
    log:
      level: INFO
      console:
        json: true                    # JSON логи для централизованной обработки
    datasource:
      jdbc:
        max-size: 20                  # Pool размер для production
        min-size: 5
    liquibase:
      migrate-at-start: true
    hibernate-orm:
      schema-management:
        strategy: validate            # Только валидация, миграции через Liquibase
```

## Health Checks

```yaml
quarkus:
  health:
    enabled: true
  smallrye-health:
    check:
      "database": 
        enabled: true             # Проверка подключения к БД
      "rabbitmq":
        enabled: true             # Проверка RabbitMQ
```

**Endpoints**:
- `/health` - общий статус
- `/health/ready` - готовность к трафику  
- `/health/live` - проверка живости

## Docker конфигурация

### Multi-stage Dockerfile
```dockerfile
# Build stage
FROM gradle:8-jdk21 AS build
COPY . /app
WORKDIR /app
RUN ./gradlew build -Dquarkus.package.jar.type=uber-jar

# Runtime stage
FROM registry.access.redhat.com/ubi9/openjdk-21:1.23
COPY --from=build /app/build/lib/* /deployments/lib/
COPY --from=build /app/build/*-runner.jar /deployments/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

## Производительность

### JVM оптимизация
```yaml
quarkus:
  package:
    jar:
      type: uber-jar              # Один JAR со всеми зависимостями
      
# Environment variables для JVM
JAVA_OPTS: >
  -Xmx512m 
  -Xms256m 
  -XX:+UseG1GC
  -XX:+UseStringDeduplication
  -Dquarkus.http.host=0.0.0.0
```

### Native compilation (GraalVM)
```bash
# Сборка native образа
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false

# Docker native образ
docker build -f src/docker/Dockerfile.native -t vkbot-native .
```

**Преимущества native**:
- Мгновенный старт (~0.1s vs ~2s)
- Меньшее потребление памяти (~50MB vs ~200MB)
- Меньший размер образа (~100MB vs ~300MB)

**Недостатки native**:
- Долгая сборка (5-10 минут)  
- Ограничения reflection и динамического кода
- Сложность отладки

## Настройка IDE

### IntelliJ IDEA
1. Установите Quarkus плагин
2. Настройте Environment Variables для запуска:
   ```
   VK_SECRET=dev_secret;VK_CONFIRMATION_CODE=123456;VK_API_TOKEN=your_token
   ```
3. Включите Gradle auto-import

### VS Code
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.debug.settings.enableRunDebugCodeLens": true,
  "quarkus.tools.enabledForWorkspace": true
}
```

Конфигурация спроектирована для простоты разработки и гибкости production развертывания.
