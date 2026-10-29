# ai-tools-vane — веб-поиск для LLM

- **id:** `ai-tools-vane`
- **Статус:** delivered

## Что делает

Даёт модели три инструмента на время генерации ответа: `search_web`, `search_academic`, `search_discussions`.
Каждый — `@Tool`-метод адаптера, который делегирует в `VaneSearchCommand`; команда выбирает источники,
режим оптимизации и подпись источников, вызывает Vane (`/api/search` после выбора провайдера через
`/api/providers`) и форматирует ответ с нумерованными источниками. Ошибка поиска не валит ответ бота:
команда возвращает строку `Search failed: …`.

Вызывает инструменты сама модель; детерминированного пользовательского входа у возможности нет — она
работает внутри шага `ai-answer`.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `@Tool search_web` / `search_academic` / `search_discussions` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapter.kt:10-32` |
| регистрация инструментов в AI-сервисе ответа | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/UserAnswerAiService.kt:13-16` |
| команда: источники, режим, формат ответа | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommand.kt:31-76` |
| выходной адаптер: выбор провайдера, маппинг источников | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchOutputAdapter.kt:15-58` |
| REST-клиент Vane: `POST /api/search`, `GET /api/providers` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchClient.kt:11-25` |

Конфигурация: `VANE_URL` (`quarkus.rest-client.vane.url`), таймауты Vane в
`src/app/src/main/resources/application.yml`.

## Данные

Данных не владеет: ничего не сохраняет, ответ поиска живёт в пределах одного вызова LLM.

## Journey

`journey-answer`, внутри шага `ai-answer`: модель может позвать инструмент, решая, нужен ли свежий факт.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| слот инструментов `UserAnswerAiService.tools` | `ai-answer` | `ai-answer` |
| контракт `VaneSearchOutputPort` (`src/ai/src/main/kotlin/com/simarel/vkbot/ai/port/output/vane/VaneSearchOutputPort.kt`) | только этот шаг | `ai-tools-vane` |
| `VANE_URL` и таймауты клиента | конфигурация деплоя (ADR 0006) | `ai-tools-vane` |

## Зависит от

- Ни от одной возможности: внешний сервис Vane и REST-клиент.

## ADR

- ADR 0003 — `@Tool` живёт только на адаптере, команда не знает LangChain4j.
- ADR 0006 — URL Vane — обязательная конфигурация (`VANE_URL`), без неё приложение не стартует.

## Acceptance criteria

Нет AC с тестом: инструменты не вызываются ни в одном тесте (в интеграционном тесте LLM подменена
WireMock-заглушкой, которая инструменты не зовёт), в модуле `ai` тестов нет. Пробел зафиксирован в
`capabilities.md`.

## Пробелы приёмки

- `VaneSearchCommand` (выбор источника и режима, формат ответа, обработка ошибки) не покрыт тестом.
- `VaneSearchOutputAdapter` (выбор первого провайдера с chat- и embedding-моделью, маппинг источников)
  не покрыт тестом.
- Интеграция `@Tool` → LLM не проверена: единственная проверка — регистрация класса в
  `UserAnswerAiService.tools`.
