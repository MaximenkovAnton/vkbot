# Деплой и развертывание

Руководство по различным способам развертывания VK Bot приложения в production и development средах.

## 🐳 Docker деплой

### JVM режим (рекомендуется для большинства случаев)

**Сборка образа**:
```bash
# Сборка приложения
./gradlew build

# Сборка Docker образа
docker build -f src/main/docker/Dockerfile.jvm -t vkbot:latest .
```

**Запуск контейнера**:
```bash
docker run -d \
  --name vkbot \
  -p 8080:8080 \
  -e VK_SECRET=your_secret_key \
  -e VK_CONFIRMATION_CODE=123456 \
  -e VK_API_TOKEN=your_vk_token \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/vkbot \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=password \
  -e RABBITMQ_HOST=rabbitmq \
  vkbot:latest
```

### Native режим (для минимального потребления ресурсов)

**Сборка native образа**:
```bash
# Сборка native executable (требует GraalVM)
./gradlew build -Dquarkus.native.enabled=true

# Сборка Docker образа
docker build -f src/main/docker/Dockerfile.native -t vkbot-native:latest .
```

**Преимущества native**:
- Старт за ~0.1 секунды
- Потребление памяти ~50MB
- Размер образа ~100MB

**Недостатки native**:
- Время сборки 5-10 минут
- Ограничения reflection
- Сложность отладки

## ⚡ Оптимизация производительности

### JVM Tuning

**Для контейнера с 512MB памяти**:
```yaml
env:
- name: JAVA_OPTS
  value: >
    -Xmx400m
    -Xms200m
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=100
    -XX:+UseStringDeduplication
    -XX:+UnlockExperimentalVMOptions
    -XX:+UseCGroupMemoryLimitForHeap
```
