# Поток данных

Документ описывает реальные пути данных и вызовов в коде: от вебхука VK до отправки ответа, плюс
саммаризацию, отказоустойчивость и известные ограничения. Модульная карта — [modules.md](modules.md),
паттерны — [patterns.md](patterns.md).

## События и их владельцы

`Event` — закрытый enum из шести значений (`share/domain/Event.kt:3-10`). Routing key в RabbitMQ равен
имени события (`PublishEventOutputAdapter.kt:20-30`), диспетчер строит карту `Event.name → EventProcessor`
(`EventDispatcher.kt:21-24`).

| Событие | Публикует (код) | Потребляет (код) | Payload |
|---|---|---|---|
| `MESSAGE_NEW` | `PublishVkEventCommandImpl.kt:15-23` (receiver) | `MessageNewEventProcessor.kt:12-22` (persistence) | доменный `Message` |
| `MESSAGE_RECEIVED` | `SaveMessageUsecase.kt:33-38` (persistence) | `MessageReceivedEventProcessor.kt:12-22` (processor) | доменный `Message` |
| `SUMMARY_REQUESTED` | `SaveMessageUsecase.kt:39-49` (persistence) | `SummaryRequestedEventProcessor.kt:12-28` (ai) | `SummaryRequestedPayload(peerId, conversationMessageId)` (`share/domain/event/SummaryRequestedPayload.kt:3-6`) |
| `MESSAGE_REQUIRE_ANSWER` | `MessageReceivedUsecaseInput.kt:35-42` (processor) | `MessageRequireAnswerEventProcessor.kt:12-22` (ai) | доменный `Message` |
| `ANSWER_MESSAGE` | `GenerateAnswerUsecase.kt:28-38` (ai) | `SendMessageEventProcessor.kt:12-22` (vk-facade) | `ResponseMessage(messageText, responseTo)` |
| `SUMMARY_READY` | `PersistenceDataUsecase.kt:132-144` (persistence) | `SummaryReadyEventProcessor.kt:18-57` (vk-facade) | `SummaryReadyPayload(peerId, messageText, first/last msg id)` (`share/domain/event/SummaryReadyPayload.kt:3-8`) |

Payload сериализуется Jackson-строкой (`PublishEventOutputAdapter.kt:25-30`) и десериализуется в
конкретный тип в processor'е; payload'ы событий лежат в `share`, поэтому дрейф формата ловится компиляцией.

## 1. Входящий webhook (VK → receiver)

```
POST /vk/callback (JSON) → VkMediatorRouterSecurityDecorator → VkMediatorRouterImpl
  → MessageMapper (DMZ) → ReceiveMessageUsecase → PublishVkEventCommand → MESSAGE_NEW
```

- **Секрет.** `VkMediatorRouterSecurityDecorator.kt:11-29` сравнивает `event.secret` с `vk.secret` до
  бизнес-логики. Неверный/отсутствующий секрет → `AccessDeniedException` → HTTP 403
  (`ExceptionHandler.kt:10-24`).
- **Тип события.** `VkCallbackEvent.mapOrUnknown(type)` (`VkCallbackEvent.kt:10-14`): `message_new`,
  `message_reply`, `confirmation`, иначе `UNKNOWN`.
- **DMZ.** `MessageMapper.toDomain` (`MessageMapper.kt:13-37`) читает `object.message`, `fwd_messages`,
  `reply_message` и `attachments` (wall-посты), собирает `List<Message>` и вызывает
  `Message.of(...)`; `jakarta.json.JsonObject` не покидает адаптер.
- **Маршрутизация.** `ReceiveMessageUsecase.kt:17-32`: `CONFIRMATION` → код подтверждения;
  `UNKNOWN` → лог + `ok`; остальные типы с сообщением → `PublishVkEventCommand` → `MESSAGE_NEW`.
- **Ответ VK.** Всегда `text/plain` `ok` (кроме confirmation и ошибки секрета): вебхук не ждёт обработки.

## 2. Event routing (RabbitMQ)

- Обменник `all-events`; очередь `queue` (`mp.messaging.incoming.all-events-queue`,
  `application.yml:66-75`); при ошибке — `dead-letter-exchange: retry-5s-exchange`.
- `EventDispatcher.dispatch` (`EventDispatcher.kt:63-84`) читает `routingKey`, находит
  `EventProcessor` и вызывает `process(payload)` на виртуальном потоке (`@RunOnVirtualThread`).
- Неизвестный routing key логируется как ошибка, сообщение подтверждается (`:86-88`).
- Исключение обработки → `handleRetry` (`:90-111`): сообщение уходит в очередь уровня
  `x-retry-count`, заголовок увеличивается; уровни/TTL — `application-queues.yml:1-32`:
  `retry-5s` (5 с) → `retry-30s` (30 с) → `retry-1m` → `retry-15m` → `retry-1h` → `dlq-final` (без TTL).
  Все retry-очереди возвращают сообщение в `all-events` через `x-dead-letter-exchange`.

## 3. Сохранение нового сообщения (persistence)

`MessageNewEventProcessor.process` (`MessageNewEventProcessor.kt:17-20`) читает `Message` из JSON и зовёт
`SaveMessageInputPort`.

`SaveMessageUsecase.execute` (`SaveMessageUsecase.kt:28-51`, `@Transactional`):

1. `FetchUserProfileCommand` (`FetchUserProfileCommandImpl.kt:26-57`): собирает `from_id` из сообщения и
   forward'ов; проверяет, какие профили уже есть (`FilterExisting*IdsPort`); недостающие получает через
   in-process порты `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort` (реализация —
   `VkProfileInProcessAdapter` → VK API `users.get`/`groups.getById`) и сохраняет. Ошибка получения профилей
   не валит сохранение: логируется WARNING (`:63-95`).
2. `SaveMessagePort` → `JooqMessageRepository.persist` (`JooqMessageRepository.kt:19-42`): insert в `messages`
   с `forwarded_messages` как JSONB (сериализация — `JacksonForwardedMessagesAdapter.kt:7-21`).
3. Публикует `MESSAGE_RECEIVED` с тем же `Message`.
4. Публикует `SUMMARY_REQUESTED` с `SummaryRequestedPayload(peerId, conversationMessageId)`.

## 4. Решение «нужен ли ответ» (processor)

`MessageReceivedEventProcessor` (`MessageReceivedEventProcessor.kt:17-20`) → `MessageReceivedUsecaseInput`
(`MessageReceivedUsecaseInput.kt:21-44`) → `IsRequireAnswerCommand` (`IsRequireAnswerCommand.kt:22-33`):

| Условие | Ответ |
|---|---|
| отправитель не человек (`!fromId.isHuman()`) | не нужен |
| не групповой чат (`!peerId.isGroupChat()`) | нужен |
| текст начинается с `!` или `\` | не нужен |
| текст содержит `vk.bot.mention` | нужен |
| последнее пересланное сообщение — от бота (`fromId == vk.bot.id`) | нужен |
| иначе | не нужен |

Если ответ нужен — публикуется `MESSAGE_REQUIRE_ANSWER`, иначе поток завершается.

## 5. Генерация ответа (ai)

`MessageRequireAnswerEventProcessor` (`MessageRequireAnswerEventProcessor.kt:17-20`) → `GenerateAnswerUsecase`
(`GenerateAnswerUsecase.kt:23-40`) → `GenerateAnswerCommand` (`GenerateAnswerCommand.kt:32-47`):

1. **Контекст** — `ConversationContextRestAdapter` (`ConversationContextRestAdapter.kt:28-67`):
   последние 20 сообщений чата через `FindStoredMessagesBeforePort`, `from_id` из истории и текущего
   сообщения (включая forward'ы), профили пользователей/групп через in-process persistence-порты;
   `forwardedMessages` парсится из JSONB здесь же (`:88-95`).
2. **LLM** — `GenerateAnswerOutputAdapter` (`GenerateAnswerOutputAdapter.kt:19-28`) форматирует профили,
   историю и текущее сообщение (с forward-блоками) и вызывает `UserAnswerAiService`
   (`UserAnswerAiService.kt:13-45`, `@SystemMessage("{config:aiUseranswerSystemprompt}")`, инструменты
   `VaneSearchToolAdapter`). Память чата — `TransientChatMemoryProvider` (10 сообщений, только в рамках
   запроса).
3. **Публикация** — `ANSWER_MESSAGE` с `ResponseMessage(messageText, responseTo = исходное сообщение)`.

## 6. Отправка в VK (vk-facade)

`SendMessageEventProcessor` (`SendMessageEventProcessor.kt:17-20`) читает `ResponseMessage` →
`MessageSendUsecase` (`MessageSendUsecase.kt:17-27`):

- `peer_id` и текст — из `responseTo` и ответа;
- forward: для группового чата, если явно не задан, формируется `ForwardedMessages` (reply на исходное
  сообщение) (`:29-35`);
- `random_id` — детерминированный (SHA-256 от текста, `peer_id` и даты исходного сообщения, `:37-43`);
- `SendVkMessageCommand` → `VkSendMessageOutputAdapter` (`VkSendMessageOutputAdapter.kt:18-29`) →
  `VkClient.sendMessage` (`VkClient.kt:18-25`) `POST https://api.vk.ru/method/messages.send` с
  `v=${vk.api.version}`, `disable_mentions=1`, `dont_parse_links=1` (`:12-15`);
- ошибка VK (`response.error != null`) → `VkException` → retry через `EventDispatcher`.

## 7. Саммаризация

```
SUMMARY_REQUESTED → ai/ProcessSummaryCommand → GenerateSummaryCommand
   → in-process persistence: create pending, find messages, save completed/failed
   → persistence/PersistenceDataUsecase.saveCompletedSummary → SUMMARY_READY
   → vk-facade/SummaryReadyEventProcessor → VK API messages.send
```

- `ProcessSummaryCommandImpl.execute` (`ProcessSummaryCommandImpl.kt:36-106`):
  - чат не в `summary.enabled-chats` → выход (`:37-40`);
  - уже есть pending-саммари → пропуск (`:42-51`);
  - последнее саммари зависло в `PENDING` → WARNING и пропуск (`:58-64`);
  - сообщения: если саммари ещё не было — `findMessagesBefore(currentMessageId + 1, batchSize)`, иначе
    `findMessagesBetween(lastSummary.lastMessageId, currentMessageId, batchSize)` (`:108-125`);
  - меньше `summary.threshold` при наличии прошлого саммари → пропуск (`:72-79`);
  - иначе `GenerateSummaryCommand` (`:89-103`).
- `GenerateSummaryCommandImpl.execute` (`GenerateSummaryCommandImpl.kt:26-63`): создаёт pending-запись,
  читает до 1000 сообщений, форматирует их с именами авторов (профили из in-process портов), вызывает
  LLM (`SummarizationAiAdapter` — 3 попытки, `SummarizationAiAdapter.kt:18-41`), сохраняет completed;
  при исключении помечает failed и пробрасывает дальше (`:59-62`).
- Сохранение completed идёт через `SaveStoredCompletedSummaryPort` → in-process адаптер →
  `PersistenceDataUsecase.handleSaveCompletedSummary` (`PersistenceDataUsecase.kt:115-146`), который и
  публикует `SUMMARY_READY` с текстом «📋 Суммаризация обсуждения…».
- `SummaryReadyEventProcessor` (`SummaryReadyEventProcessor.kt:23-54`) строит forward на диапазон
  сообщений `[first, last]` и отправляет через тот же `MessageSendUsecase`.

## Отказоустойчивость и границы

| Механизм | Где | Что гарантирует |
|---|---|---|
| At-least-once доставка | RabbitMQ + ack после `process` (`EventDispatcher.kt:76-82`) | сообщение не теряется при падении обработчика; возможны повторы |
| Retry-лестница | `EventDispatcher.kt:90-111`, `application-queues.yml` | 5 попыток с интервалами, затем финальный DLQ |
| DLQ | `dlq-final-queue` / `dlq-final-exchange` | сообщение сохранено для ручного разбора |
| Виртуальные потоки | `@RunOnVirtualThread` (`EventDispatcher.kt:64`) | I/O-обработчик не блокирует платформенный поток |
| Изоляция профилей | `FetchUserProfileCommandImpl.kt:63-95` | сбой VK при получении профилей не отменяет сохранение сообщения |
| Повтор AI-саммари | `SummarizationAiAdapter.kt:10-40` | 3 попытки внутри одного события |
| Детерминированный `random_id` | `MessageSendUsecase.kt:37-43` | повторная отправка того же ответа даёт тот же `random_id` |

## Известные ограничения (не скрывать)

- **Нет идемпотентности записи.** У `messages` нет уникального ключа, `JooqMessageRepository.persist` —
  чистый insert (`JooqMessageRepository.kt:19-42`). Повторная доставка `MESSAGE_NEW` создаст дубль.
  Условие апгрейда: уникальный `(peer_id, conversation_message_id)` + upsert, затем outbox — если дубли
  появятся в проде (в этом проходе не менялось, [ADR 0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md) §Последствия).
- **Нет outbox.** Сохранение в БД и публикация в RabbitMQ не транзакционны относительно друг друга
  (`SaveMessageUsecase.kt:28-51`): сбой публикации после записи оставляет сообщение без следующего шага.
  Ретраи на уровне брокера не покрывают этот случай.
- **`forwardedMessages` — строка.** `StoredMessage.forwardedMessages: String?`
  (`share/domain/model/StoredMessage.kt:13`); JSONB парсится на границе в
  `ConversationContextRestAdapter.kt:77-95`. Типизация — отдельная задача.
- **Саммари-сообщение не идемпотентно по `random_id`:** синтетическое сообщение получает
  `date = Instant.now()` (`SummaryReadyEventProcessor.kt:35-43`), поэтому повтор даёт другой `random_id`.
- **OCR-команда не подключена:** `RecognizeImagesCommand` реализована, но ни один use case её не вызывает
  (`RecognizeImagesCommandImpl.kt:8-27` — единственное использование). Это не поток данных.
- **`circuit-breaker.vk-profile` не действует:** блок в конфиге есть (`application.yml:174-179`),
  аннотаций нет; защита VK API — только retry-лестница брокера.

## Observability

- OpenTelemetry включён для логов, метрик и трейсов (`quarkus.otel.*`, `application.yml:35-42`);
  экспорт — OTLP (по умолчанию `localhost:4317`; без коллектора в логах тестов видны ошибки экспорта).
- Брокерская наблюдаемость — `smallrye.messaging.observation.enabled: true` (`application.yml:169-172`).
- Собственного кода с `Tracer`, MDC или `MeterRegistry` в проекте нет — только конфигурация расширений и
  logging-декораторы (`CommandLoggingDecorator.kt:12-28`, `UsecaseLoggingDecorator.kt:12-28`, TRACE).

## Как добавить шаг в поток

1. Новое значение в `Event` (`share/domain/Event.kt`) — если шаг асинхронный.
2. Новый `EventProcessor` в модуле-владельце шага; он сам попадёт в карту диспетчера по `event()`.
3. Публикация события из предыдущего use case через `PublishEventCommand` (не напрямую из адаптера).
4. Если шаг синхронный и внутри деплоя — новый порт в `share` + in-process адаптер у поставщика
   ([ADR 0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md)), без HTTP.

Никакая карта процессоров не регистрируется вручную: `@All processors: MutableList<EventProcessor>`
(`EventDispatcher.kt:20-24`) собирает бины CDI.
