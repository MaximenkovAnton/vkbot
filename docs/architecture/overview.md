# Обзор архитектуры

VK Bot — **гексагональный монолит в одном деплое**. Горизонтальные слои разделены внутри модулей,
вертикали (фичи) живут в тех же модулях, поведение спрятано за портами, Quarkus — на краю.
Решение: [ADR 0003](../adr/0003-module-boundaries-and-layer-rules.md); один деплой —
[ADR 0002](../adr/0002-single-deployable-app-module.md).

## Принципы

1. **Фреймворк — адаптер, не архитектура.** `domain` и `usecase`/`command` не импортируют Quarkus, jOOQ,
   Jackson, JAX-RS и LangChain4j. Это проверяют ArchUnit-правила L1–L3
   (`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/LayerDependenciesTest.kt:51-94`) и
   сборка падает на нарушении.
2. **Один деплой.** Приложение — `:src:app`; корень — агрегатор конвенций
   (`build.gradle.kts:1-8`, `src/app/build.gradle.kts:3`). Артефакт — `src/app/build/quarkus-app/quarkus-run.jar`.
3. **Контракты — в `share`.** Входящие/исходящие порты, `Command<REQ,RESP>`, `Event`, доменные модели и VO
   лежат в `src/share` (`share/port/Port.kt:3-9`, `share/command/Command.kt:3-8`,
   `share/domain/Event.kt:3-10`). Модули зависят только от них.
4. **Синхронные вызовы внутри деплоя — in-process.** `ai` и `persistence` общаются через порты `share`,
   реализованные модулем-поставщиком, а не через loopback HTTP
   ([ADR 0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md)).
5. **Асинхронность только там, где она нужна.** Вебхук VK не ждёт AI: этапы идут через RabbitMQ с
   retry-лестницей и финальным DLQ.

## Слои

| Слой | Где | Что содержит | Правило |
|---|---|---|---|
| `domain` | `<module>/domain/` | сущности, VO, доменные ошибки | без фреймворков и других слоёв (L1) |
| `port/input` | `<module>/port/input/` | use-case контракты, которые зовёт внешний мир | без реализаций и адаптеров (L2) |
| `port/output` | `<module>/port/output/` | интерфейсы к внешнему миру | без реализаций (L2) |
| `usecase` | `<module>/usecase/` | реализация `port/input` над доменом и `port/output` | без адаптеров и транспорта (L3) |
| `command` | `<module>/command/` | атомарные `Command<REQ,RESP>` | без адаптеров и транспорта (L3) |
| `adapter/input` | `<module>/adapter/input/` | REST, `@Incoming`-консьюмеры, in-process адаптеры | зовёт только порты (L4) |
| `adapter/output` | `<module>/adapter/output/` | jOOQ, VK API, AI, Vane, JSON | не ходит во входные адаптеры и usecase (L5) |
| `config` | `<module>/config/` | CDI-продюсеры, конфигурация модуля | видит всё только здесь |

Модульная карта и публичные входы — [modules.md](modules.md). Пошаговые правила — [AGENTS.md](../../AGENTS.md) §4.

## Runtime-топология

```
Вход:
  POST /vk/callback          -> receiver/adapter/input/vk/VkMediatorRouterImpl.kt:16-40
  GET/POST /persistence/*    -> persistence/adapter/input/rest/PersistenceDataController.kt:21-127
  GET /api/profiles/*        -> vk-facade/adapter/input/rest/VkFacadeController.kt:14-32
  RabbitMQ all-events-queue  -> infrastructure/mq/adapter/input/EventDispatcher.kt:63-84

Выход:
  RabbitMQ events-exchange   -> infrastructure/adapter/output/mq/PublishEventOutputAdapter.kt:15-33
  PostgreSQL                 -> persistence/adapter/output/persistence/jooq/*
  VK API                     -> vk-facade/adapter/output/vk/VkClient.kt:12-39 (configKey "vk")
  Vane (search)              -> ai/adapter/output/ai/tool/vane/VaneSearchClient.kt:11-25 (configKey "vane")
  Ollama (LLM)               -> ai/adapter/output/ai/answer/UserAnswerAiService.kt:13-19 (LangChain4j)

In-process (без HTTP):
  ai -> persistence          -> share/port/output/persistence/*  <-  PersistenceDataInProcessAdapter.kt
  persistence -> vk-facade   -> share/port/output/vk/*           <-  VkProfileInProcessAdapter.kt
```

## Основной поток

Событий ровно шесть — закрытый `enum class Event` (`share/domain/Event.kt:3-10`):
`MESSAGE_NEW`, `MESSAGE_RECEIVED`, `MESSAGE_REQUIRE_ANSWER`, `ANSWER_MESSAGE`, `SUMMARY_REQUESTED`,
`SUMMARY_READY`.

```
VK ──POST /vk/callback──> receiver
    secret check (декоратор) → DMZ-маппинг JSON → ReceiveMessageUsecase
    → MESSAGE_NEW (RabbitMQ)

MESSAGE_NEW ──> persistence/MessageNewEventProcessor
    SaveMessageUsecase: fetch profiles (in-process vk-порт) → save message
    → MESSAGE_RECEIVED и SUMMARY_REQUESTED

MESSAGE_RECEIVED ──> processor/MessageReceivedEventProcessor
    IsRequireAnswerCommand → (если нужен ответ) MESSAGE_REQUIRE_ANSWER

MESSAGE_REQUIRE_ANSWER ──> ai/MessageRequireAnswerEventProcessor
    GenerateAnswerUsecase: контекст (in-process persistence-порты) → LLM
    → ANSWER_MESSAGE

ANSWER_MESSAGE ──> vk-facade/SendMessageEventProcessor
    MessageSendUsecase → VK API messages.send

SUMMARY_REQUESTED ──> ai/SummaryRequestedEventProcessor
    ProcessSummaryCommand: порог/включённые чаты → GenerateSummaryCommand
    → summarization LLM → SaveStoredCompletedSummaryPort (in-process persistence)

persistence/PersistenceDataUsecase.saveCompletedSummary
    → SUMMARY_READY

SUMMARY_READY ──> vk-facade/SummaryReadyEventProcessor
    MessageSendUsecase с forward-блоком → VK API messages.send
```

Подробности шагов, payload'ов и отказоустойчивости — [data-flow.md](data-flow.md).

## Ключевые компоненты

- **Домен** (`share/domain/model`, `share/domain/vo`): `Message`, `StoredMessage`, `Summary`,
  `VkUserProfile`, `VkGroupProfile`; VO на `@JvmInline value class` — `MessageText`, `PeerId`, `FromId`,
  `Date`, `ConversationMessageId`, `GroupId`, `Payload`.
- **Контракты** (`share/port`, `share/command`): `OutputPort<REQ,RESP>` — один метод на порт;
  `Command<REQ,RESP>`; `EventProcessor` — единый вход консьюмера.
- **Use case'ы**: `ReceiveMessageUsecase`, `SaveMessageUsecase`, `PersistenceDataUsecase`,
  `MessageReceivedUsecaseInput`, `GenerateAnswerUsecase`, `ProcessSummaryUsecase`, `GenerateSummaryUsecase`,
  `MessageSendUsecase`, `VkProfileUsecase`.
- **Команды**: `PublishVkEventCommand`, `PublishEventCommand`, `IsRequireAnswerCommand`,
  `FetchUserProfileCommand`, `GenerateAnswerCommand`, `GenerateSummaryCommand`, `ProcessSummaryCommand`,
  `RecognizeImagesCommand` (реализована, ни к одному use case не подключена), `SendVkMessageCommand`,
  `GetProfileCommand`, `VaneSearchCommand`.
- **Адаптеры**: входные — REST-контроллеры, `@Incoming`-процессоры, in-process адаптеры; выходные —
  jOOQ-репозитории, VK REST client, LangChain4j AI-сервисы, Vane client, Jackson-сериализация.
- **Инфраструктура**: `EventDispatcher` (маршрутизация по routing key = имя события, retry, DLQ),
  `PublishEventOutputAdapter`, logging-декораторы, `DlqConfig`, `MapperConfig`.

## Безопасность и валидация

- **Секрет VK** проверяется CDI-декоратором до бизнес-логики:
  `receiver/adapter/input/vk/VkMediatorRouterSecurityDecorator.kt:11-29`. Неверный/отсутствующий секрет →
  `AccessDeniedException` → HTTP 403 (`ExceptionHandler.kt:10-24`).
- **DMZ**: VK JSON парсится в DTO в `receiver/adapter/input/dto/`, в домен маппится
  `receiver/adapter/input/vk/mapper/MessageMapper.kt:13-58`. `jakarta.json.JsonObject` не покидает входной
  адаптер.
- **Value objects** валидируют и нормализуют значения (`MessageText.of`, `PeerId.of`, `FromId.of`).
- **Ошибки** — иерархия `VkBotAppException` со статусом (`share/domain/exception/`), единый маппинг в
  `ExceptionHandler.kt:10-39`.

## Что здесь не реализовано

Документ не должен обещать того, чего нет в коде:

- нет CQRS и event sourcing: события не сохраняются, event store отсутствует;
- нет circuit breaker: расширение fault-tolerance объявлено (`src/vk-facade/build.gradle.kts:21`), но
  аннотаций `@CircuitBreaker`/`@Retry` нет, блок `circuit-breaker.vk-profile` в конфиге никем не читается;
- нет Prometheus-эндпоинта `/metrics` и пути `/health`; health — `/q/health`;
- нет отдельного «AI-клиента»: AI вызывается через LangChain4j AI-сервисы (`@RegisterAiService`).

## Связанные решения

| ADR | О чём |
|---|---|
| [0001](../adr/0001-stack-kotlin-quarkus-gradle.md) | стек и версии |
| [0002](../adr/0002-single-deployable-app-module.md) | один деплой `:src:app` |
| [0003](../adr/0003-module-boundaries-and-layer-rules.md) | границы модулей и слоёв |
| [0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md) | in-process порты, события RabbitMQ |
| [0005](../adr/0005-quality-gates-and-ci.md) | гейты и CI |
| [0006](../adr/0006-configuration-and-secrets.md) | конфигурация и секреты |
