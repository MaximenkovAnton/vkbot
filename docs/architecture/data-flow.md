# Поток данных

Документ описывает реальные пути данных и вызовов в коде: от Bots Long Poll VK до отправки ответа, плюс
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
| `SUMMARY_READY` | `PersistenceDataUsecase.kt:195-207` (persistence) | `SummaryReadyEventProcessor.kt:18-57` (vk-facade) | `SummaryReadyPayload(peerId, messageText, first/last msg id)` (`share/domain/event/SummaryReadyPayload.kt:3-8`) |

Payload сериализуется Jackson-строкой (`PublishEventOutputAdapter.kt:25-30`) и десериализуется в
конкретный тип в processor'е; payload'ы событий лежат в `share`, поэтому дрейф формата ловится компиляцией.

## 1. Входящий Bots Long Poll (VK → receiver)

```
LongPollRunner (виртуальный поток, старт по StartupEvent)
  → groups.getLongPollServer (REST-клиент configKey "vk")
  → a_check: {server}?act=a_check&key=…&ts=…&wait=25
  → MessageMapper (DMZ) → ReceiveMessageUsecase → PublishVkEventCommand → MESSAGE_NEW
  → SaveLongPollTsPort: ts сохраняется только после успешной публикации
```

- **Транспорт.** `receiver` сам опрашивает Bots Long Poll API; публичного HTTP-входа у него нет —
  `POST /vk/callback` отвечает 404, `vk.secret`/`vk.confirmation-code` в конфигурации отсутствуют
  ([ADR 0008](../adr/0008-bots-long-poll-transport.md)). `groups.getLongPollServer` возвращает
  `server`, `key` и `ts`; на старте сессия запрашивается заново, а `server` и `key` нигде не хранятся.
- **Позиция.** В PostgreSQL (`long_poll_position`) лежит только `ts` последнего обработанного обновления.
  Если он есть, первый `a_check` идёт с ним — так доставляются сообщения, пришедшие за время простоя;
  если записи нет, опрос начинается с `ts` из ответа `groups.getLongPollServer`. `ts` сохраняется драйвером
  `LongPollRunner`, а не use case, и только после успешной публикации `MESSAGE_NEW`.
- **Тип обновления.** `VkUpdateType` (`VkUpdateType.kt`): `MESSAGE_NEW`, `MESSAGE_REPLY`, иначе `UNKNOWN`.
- **DMZ.** `MessageMapper.toDomain` (`MessageMapper.kt:13-37`) читает `object.message`, `fwd_messages`,
  `reply_message` и `attachments` (wall-посты), собирает `List<Message>` и вызывает
  `Message.of(...)`; `jakarta.json.JsonObject` не покидает адаптер.
- **Маршрутизация.** `ReceiveMessageUsecase`: обновление с сообщением → `PublishVkEventCommand` →
  `MESSAGE_NEW`; `UNKNOWN` или ошибка маппинга → обновление пропускается с ERROR-логом, `ts` сохраняется.
- **Ответ VK.** Потоковый: приложение не отвечает на событие, а забирает следующую пачку запросом
  `a_check`; время ожидания VK — `wait=25`, read-timeout клиента — 60 с.
- **Коды `failed`.** `1` — продолжить с новым `ts` из ответа (часть истории VK потерял, WARN); `2` —
  новая сессия с тем же `ts`; `3` — новая сессия и `ts` из `groups.getLongPollServer`.
- **Сбои.** Сбой публикации → пауза 5 с, `ts` не сохраняется, то же обновление повторяется; сеть или
  5xx на `a_check` → пауза нарастает до 60 с и сбрасывается после успеха.

## 2. Event routing (RabbitMQ)

- Обменник `all-events`; очередь `queue` (`mp.messaging.incoming.all-events-queue`,
  `application.yml:92-101`); при ошибке — `dead-letter-exchange: retry-5s-exchange`.
- `EventDispatcher.dispatch` (`EventDispatcher.kt:63-85`) читает `routingKey`, находит
  `EventProcessor` и вызывает `process(payload)` на виртуальном потоке (`@RunOnVirtualThread`).
- Неизвестный routing key логируется как ошибка, сообщение подтверждается (`:87-89`).
- Исключение обработки → `handleRetry` (`:91-113`): сообщение уходит в очередь уровня
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
  `PersistenceDataUsecase.handleSaveCompletedSummary` (`PersistenceDataUsecase.kt:178-209`), который и
  публикует `SUMMARY_READY` с текстом «📋 Суммаризация обсуждения…».
- `SummaryReadyEventProcessor` (`SummaryReadyEventProcessor.kt:23-54`) строит forward на диапазон
  сообщений `[first, last]` и отправляет через тот же `MessageSendUsecase`.

## 8. Поздравления по расписанию (greetings)

```
greetings.cron (по умолчанию 0 0 0 * * ?), зона greetings.timezone
  → DailyGreetingRunner (@Scheduled Quarkus)
  → RunDailyGreetingsUsecase
  → CollectGreetingPlansCommand: групповые чаты (peer_id > 2e9) + профили по (day, month) + чаты from_id
      → один HolidayGreeting на праздник + один BirthdayGreeting на всех именинников чата
  → ClaimDailyGreetingPort: один INSERT … ON CONFLICT DO NOTHING на (date, peer) до отправки
  → на каждый план: GenerateGreetingTextCommand → GenerateGreetingTextPort (ИИ; шаблон при отказе)
  → SendGreetingMessagePort → PlainMessageSendUsecase → VK API messages.send
```

- **Расписание.** `DailyGreetingRunner` — тонкий `@ApplicationScoped`-бин с
  `@Scheduled(cron = "{greetings.cron}", timeZone = "{greetings.timezone}")`; ключ `greetings.cron`
  (дефолт `0 0 0 * * ?`, то есть 00:00, env `GREETINGS_CRON`), `off`/`disabled` выключает расписание;
  зона `greetings.timezone` (дефолт `Europe/Minsk`, env `GREETINGS_TIMEZONE`). Отдельного виртуального
  потока и расчёта ближайшей полуночи нет; пропущенное срабатывание не догоняется (catch-up нет).
- **События.** Прогон запускается планировщиком, а не событием VK: новых значений `Event`, сообщений
  RabbitMQ и HTTP-входов нет.
- **План.** Праздники — 1 января и 256-й день года (все групповые чаты с перепиской); именинники — профили
  из `vk_user_profiles.birth_date` на `(day, month)` даты, каждый в чаты из `messages.from_id` (включая
  личные переписки). На 1 марта невисокосного года запрашиваются и `(1, 3)`, и `(29, 2)`. План чата —
  один `GreetingPlan.HolidayGreeting` на каждый праздник и один `GreetingPlan.BirthdayGreeting` на всех
  именинников чата с отсортированными именами.
- **Текст.** `GenerateGreetingTextCommand` зовёт `GenerateGreetingTextPort` с запросом
  `Request(date, occasion, people)` (`ai`, system-промпт `aiGreetingSystemprompt`): для праздника
  `occasion = Holiday.title` и пустой `people` («все»), для именинников — `occasion = День рождения` и
  отсортированные имена. При любом другом исключении или пустом ответе возвращается шаблон («С Новым годом!»,
  «С Днём программиста!», `С днём рождения, …!`) и пишется WARN; `InterruptedException`
  пробрасывается с сохранённым флагом и шаблоном не подменяется.
- **At-most-once.** Claim-строка `greeting_log` (PK `(greeting_date, peer_id)`) пишется один раз на чат до
  отправки всех его сообщений: повторный прогон даты получает `claimed = false` и не отправляет, два
  экземпляра проигрывают конфликт. Цена — ошибка отправки не повторяется, оставшиеся сообщения чата
  теряются до следующих суток (известное ограничение, ADR 0009).
- **Изоляция.** Ошибка claim → ERROR-лог с `peerId`, все сообщения чата учитываются в `failed`, остальные
  чаты продолжаются; ошибка отправки одного сообщения учитывается в `failed`, но следующие сообщения и
  чаты продолжаются; `InterruptedException` (остановка приложения) пробрасывается с сохранённым флагом.
- **Отправка.** Плоская ветка `vk-facade`: один `SendGreetingMessagePort.Request` на план,
  `forwardedMessages = null`, `random_id` от текста и peer; forward/reply-путь (`MessageSendUsecase`) не
  используется.

## Отказоустойчивость и границы

| Механизм | Где | Что гарантирует |
|---|---|---|
| At-least-once доставка | RabbitMQ + ack после `process` (`EventDispatcher.kt:77-83`) | сообщение не теряется при падении обработчика; возможны повторы |
| Позиция `ts` после публикации | `LongPollRunner` | сбой между публикацией и сохранением даёт дубликат, но не потерю обновления |
| Паузы опроса | `LongPollRunner` | 5 с при сбое публикации, нарастающая до 60 с при сетевых ошибках и 5xx; цикл не падает |
| Retry-лестница | `EventDispatcher.kt:91-113`, `application-queues.yml` | 5 попыток с интервалами, затем финальный DLQ |
| DLQ | `dlq-final-queue` / `dlq-final-exchange` | сообщение сохранено для ручного разбора |
| Виртуальные потоки | `@RunOnVirtualThread` (`EventDispatcher.kt:64`) | I/O-обработчик не блокирует платформенный поток |
| Изоляция профилей | `FetchUserProfileCommandImpl.kt:63-95` | сбой VK при получении профилей не отменяет сохранение сообщения |
| Повтор AI-саммари | `SummarizationAiAdapter.kt:10-40` | 3 попытки внутри одного события |
| Детерминированный `random_id` | `MessageSendUsecase.kt:37-43` | повторная отправка того же ответа даёт тот же `random_id` |

## Известные ограничения (не скрывать)

- **Нет идемпотентности записи.** У `messages` нет уникального ключа, `JooqMessageRepository.persist` —
  чистый insert (`JooqMessageRepository.kt:19-42`). Повторная доставка `MESSAGE_NEW` создаст дубль; при
  Long Poll это осознанный размен: сбой между публикацией и сохранением `ts` повторяет обновление.
  Условие апгрейда: уникальный `(peer_id, conversation_message_id)` + upsert, затем outbox — если дубли
  появятся в проде (в этом проходе не менялось, [ADR 0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md) §Последствия).
- **Приём рассчитан на один экземпляр.** Токен сообщества и позиция `ts` одни на деплой: второй экземпляр
  опрашивал бы VK с тем же `ts` и мешал первому. Горизонтальное масштабирование приёма не поддерживается
  ([ADR 0008](../adr/0008-bots-long-poll-transport.md)).
- **`failed: 1` теряет часть истории.** VK отбрасывает события, которые приложение не забрало; опрос
  продолжается с нового `ts`, потеря фиксируется WARN-логом и восстановлению не подлежит.
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
- **`circuit-breaker.vk-profile` не действует:** блок в конфиге есть (`application.yml:193-198`),
  аннотаций нет; защита VK API — только retry-лестница брокера.

## Observability

- OpenTelemetry включён для логов, метрик и трейсов (`quarkus.otel.*`, `application.yml`);
  экспорт — OTLP (по умолчанию `localhost:4317`; без коллектора в логах тестов видны ошибки экспорта).
- Брокерская наблюдаемость — `smallrye.messaging.observation.enabled: true` (`application.yml:188-191`).
- Собственный трейс есть только у `daily-greetings`: спан `greetings.daily-run` в
  `DailyGreetingRunner.kt` с атрибутами `date`/`planned`/`sent`/`failed` и статусом `OK`/`ERROR`; метрик,
  MDC и трейсов другого кода нет. Остальное — конфигурация расширений и logging-декораторы
  (`CommandLoggingDecorator.kt:12-28`, `UsecaseLoggingDecorator.kt:12-28`, TRACE).

## Как добавить шаг в поток

1. Новое значение в `Event` (`share/domain/Event.kt`) — если шаг асинхронный.
2. Новый `EventProcessor` в модуле-владельце шага; он сам попадёт в карту диспетчера по `event()`.
3. Публикация события из предыдущего use case через `PublishEventCommand` (не напрямую из адаптера).
4. Если шаг синхронный и внутри деплоя — новый порт в `share` + in-process адаптер у поставщика
   ([ADR 0004](../adr/0004-in-process-ports-instead-of-loopback-rest.md)), без HTTP.

Никакая карта процессоров не регистрируется вручную: `@All processors: MutableList<EventProcessor>`
(`EventDispatcher.kt:20-24`) собирает бины CDI.
