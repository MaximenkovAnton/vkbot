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
| команда: источники, режим, формат ответа | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommand.kt:31-77` |
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

- **AC-ai-tools-vane-1** — КОГДА выполняется поиск Vane, СИСТЕМА ДОЛЖНА отобразить инструмент в
  источники/режим/подпись: Web → `["web"]`/`balanced`/`Sources`, Academic → `["academic"]`/`quality`/
  `Academic Sources`, Discussion → `["discussions"]`/`balanced`/`Discussion Sources`; отформатировать
  нумерованные источники; при ошибке порта вернуть строку `Search failed: <message>`, не пробрасывая
  исключение. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommandImplTest.kt`.
- **AC-ai-tools-vane-2** — КОГДА выполняется `VaneSearchOutputAdapter`, СИСТЕМА ДОЛЖНА выбрать первый
  провайдер, у которого есть и chat-, и embedding-модель, отправить поиск с его ключами, смапить `message` и
  `sources` (content/title/url); при отсутствии подходящего провайдера — бросить `IllegalStateException`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchOutputAdapterTest.kt`.
- **AC-ai-tools-vane-3** — КОГДА вызывается любой `@Tool`-метод, СИСТЕМА ДОЛЖНА делегировать в
  `VaneSearchCommand` с соответствующим типом запроса и вернуть его `result`. Test:
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapterTest.kt`.

## Пробелы приёмки

- Выбор инструментов самой моделью не проверен: в `AiIntegrationTest` модель подменена WireMock-заглушкой,
  которая `@Tool`-методы не вызывает. Делегирование каждого `@Tool`-метода в команду проверено
  `VaneSearchToolAdapterTest`; регистрация класса в `UserAnswerAiService.tools` тестом не покрыта — её
  проверяет только сборка/старт приложения.
- Поведение, которого нет в AC, не тестировалось: форматирование пустого `message`, поля `history`/`stream`
  запроса клиента (адаптер их не заполняет) — названы пробелами, а не pass.
