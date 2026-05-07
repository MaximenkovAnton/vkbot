# AI Tools

Руководство по AI-инструментам бота — как они работают и как их использовать.

## Обзор

Бот использует **LangChain4j Tool Calling** для расширения возможностей AI. Когда AI получает вопрос требующий актуальной информации, он может вызвать один из доступных инструментов.

### Доступные инструменты

| Tool | Описание | Использование |
|------|----------|---------------|
| `searchWeb` | Поиск в интернете через Vane (Perplexica) | Актуальные новости, факты, события |
| `searchAcademic` | Поиск по научным источникам | Исследования, научные статьи |
| `searchDiscussions` | Поиск по форумам и обсуждениям | Мнения, опыт сообщества |

## Архитектура

```
┌─────────────────┐     tools      ┌──────────────────┐
│ UserAnswerAiService │─────────────→│ VaneSearchCommand │
│   @RegisterAiService │             │   @Tool methods   │
└─────────────────┘                 └────────┬─────────┘
                                             │
                              ┌──────────────┘
                              ↓
                     ┌─────────────────┐
                     │ VaneSearchOutputPort │
                     └────────┬────────┘
                              ↓
                     ┌─────────────────┐
                     │ VaneSearchOutputAdapter │
                     └────────┬────────┘
                              ↓
                     ┌─────────────────┐
                     │ VaneSearchClient │
                     │ @RegisterRestClient│
                     └────────┬────────┘
                              ↓
                        ┌──────────┐
                        │ Vane API │
                        │ Perplexica│
                        └──────────┘
```

## Как это работает

1. **Пользователь задаёт вопрос**: "Что произошло наukuсIT-конференции вчера?"

2. **AI анализирует запрос**: Понимает, что нужна актуальная информация

3. **LangChain4j вызывает tool**: Автоматически вызывает `searchWeb(query)`

4. **Tool выполняет поиск**:
   - `VaneSearchCommand` вызывает `VaneSearchOutputPort`
   - Адаптер определяет доступные модели через `/api/providers`
   - Отправляет запрос к Vane API
   - Возвращает результат с источниками

5. **AI формирует ответ**: Использует результаты поиска для ответа

## Пример диалога

```
Пользователь: Какая погода в Москве сейчас?

AI (вызывает tool): searchWeb("погода в Москве сейчас")

Vane возвращает:
- Температура: +15°C
n- Состояние: Переменная облачность
- Источники: [Яндекс.Погода], [Gismeteo]

AI отвечает: В Москве сейчас +15°C, переменная облачность.
Источники: Яндекс.Погода, Gismeteo.
```

## Настройка Vane

### Предварительные требования

- Установленный [Perplexica](https://github.com/ItzCrazyKns/Vane) (Vane)
- Настроенные AI-провайдеры в Vane (OpenAI, Ollama, etc.)

### Конфигурация

```yaml
# application.yml
quarkus:
  rest-client:
    vane:
      url: https://your-vane.com
      scope: jakarta.inject.Singleton
```

Или через environment variable:
```bash
export VANE_URL=https://your-vane.com/api/providers
```

### Как Vane выбирает модели

Адаптер автоматически:
1. Запрашивает `/api/providers` — список доступных провайдеров
2. Выбирает первый провайдер с поддержкой chat и embedding моделей
3. Использует первую доступную модель каждого типа

```
GET /api/providers → [{id: "uuid", name: "OpenAI", chatModels: [...], embeddingModels: [...]}]
```

Если нужно задать конкретную модель — модифицируй `VaneSearchOutputAdapter`.

## Расширение инструментов

### Добавление нового tool

1. **Добавь метод в Command**:
```kotlin
@ApplicationScoped
class VaneSearchCommandImpl(...) : VaneSearchCommand {

    @Tool("Описание что делает tool")
    fun myNewTool(param: String): String {
        // Реализация
    }
}
```

2. **LangChain4j автоматически** подхватит новый tool через `@RegisterAiService(tools = [...])`

### Создание отдельного Command

Для инструментов не связанных с Vane:

```kotlin
// Порт
interface WeatherOutputPort : OutputPort<WeatherRequest, WeatherResponse>

// Адаптер
@ApplicationScoped
class WeatherOutputAdapter(@RestClient private val client: WeatherClient) : WeatherOutputPort

// Command с tool
@ApplicationScoped
class WeatherCommand(private val port: WeatherOutputPort) {

    @Tool("Получает текущую погоду для города")
    fun getWeather(city: String): String {
        val response = port.execute(WeatherRequest(city))
        return format(response)
    }
}

// Регистрация в AI сервисе
@RegisterAiService(tools = [VaneSearchCommandImpl::class, WeatherCommand::class])
interface UserAnswerAiService { ... }
```

## Отладка

### Логирование запросов

```yaml
quarkus:
  langchain4j:
    log-requests: true
    log-responses: true
```

### Проверка провайдеров

Прямой запрос к Vane:
```bash
curl https://your-vane.com/api/providers
```

## Ограничения

1. **Таймауты**: Vane может долго отвечать на сложные запросы (10-30s)
2. **Rate limits**: Зависят от провайдера в Vane
3. **Контекст**: Tool вызывается без истории диалога — только с query

## Безопасность

- URL Vane передаётся через env var, не хардкодится
- Нет аутентификации на Vane по умолчанию — разместите в приватной сети или добавьте конфигурацию например через nginx и хедер с авторизацией, что можно задать через параметры
