# Поток данных

Документ описывает реальные пути данных и вызовов в коде: от Bots Long Poll VK до отправки ответа, плюс
саммаризацию, отказоустойчивость и известные ограничения. Модульная карта — [modules.md](modules.md),
паттерны — [patterns.md](patterns.md).

## События и их владельцы

`Event` — закрытый enum из семи значений (`share/domain/Event.kt:3-11`). Routing key в RabbitMQ равен
имени события (`PublishEventOutputAdapter.kt:20-30`), диспетчер строит карту `Event.name → EventProcessor`
(`EventDispatcher.kt:21-24`).

| Событие | Публикует (код) | Потребляет (код) | Payload |
|---|---|---|---|
| `MESSAGE_NEW` | `PublishVkEventCommandImpl.kt:15-23` (receiver) | `MessageNewEventProcessor.kt:12-22` (persistence) | доменный `Message` |
| `MESSAGE_RECEIVED` | `SaveMessageUsecase.kt:33-38` (persistence) | `MessageReceivedEventProcessor.kt:12-22` (processor) | доменный `Message` |
| `SUMMARY_REQUESTED` | `SaveMessageUsecase.kt:39-49` (persistence) | `SummaryRequestedEventProcessor.kt:12-28` (ai) | `SummaryRequestedPayload(peerId, conversationMessageId)` (`share/domain/event/SummaryRequestedPayload.kt:3-6`) |
| `MESSAGE_REQUIRE_ANSWER` | `MessageReceivedUsecaseInput.kt:104-106` (processor, ветка `general`) | `MessageRequireAnswerEventProcessor.kt:12-22` (ai) | доменный `Message` |
| `ANSWER_MESSAGE` | `GenerateAnswerUsecase.kt:28-38` (ai); сводка «что нового» — `ProcessNewsDigestCommandImpl` (ai); процессор — спам-ветка и справка (`MessageReceivedUsecaseInput.kt:54-66,94-102`) | `SendMessageEventProcessor.kt:12-22` (vk-facade) | `ResponseMessage(messageText, responseTo)` |
| `NEWS_DIGEST_REQUESTED` | `MessageReceivedUsecaseInput.kt:104-106` (processor, ветка `news`) | `NewsDigestRequestedEventProcessor.kt:12-22` (ai) | доменный `Message` |
| `SUMMARY_READY` | `PersistenceDataUsecase.kt:238-269` (persistence) | `SummaryReadyEventProcessor.kt:18-57` (vk-facade) | `SummaryReadyPayload(peerId, messageText, first/last msg id)` (`share/domain/event/SummaryReadyPayload.kt:3-8`) |

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

1. `FetchUserProfileCommand` (`FetchUserProfileCommandImpl.kt:29-58`): собирает `from_id` из сообщения и
   forward'ов; читает сохранённые профили через `FindUserProfilesByIdsPort`/`FindGroupProfilesByIdsPort` и
   считает устаревшими отсутствующие и те, у кого `last_updated` старше месяца; устаревшие получает через
   in-process порты `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort` (реализация —
   `VkProfileInProcessAdapter` → VK API `users.get`/`groups.getById`), сливает с сохранёнными (значение из
   VK перекрывает старое, `null` оставляет старое) и сохраняет с новым `last_updated`. Ошибка получения
   профилей не валит сохранение: логируется WARNING (`:67-107`).
2. `SaveMessagePort` → `JooqMessageRepository.persist` (`JooqMessageRepository.kt:19-42`): insert в `messages`
   с `forwarded_messages` как JSONB (сериализация — `JacksonForwardedMessagesAdapter.kt:7-21`).
3. Публикует `MESSAGE_RECEIVED` с тем же `Message`.
4. Публикует `SUMMARY_REQUESTED` с `SummaryRequestedPayload(peerId, conversationMessageId)`.

## 4. Спам-проверка, решение «нужен ли ответ» и маршрутизация интентов (processor)

`MessageReceivedEventProcessor` (`MessageReceivedEventProcessor.kt:17-20`) → `MessageReceivedUsecaseInput`
(`MessageReceivedUsecaseInput.kt:35-79`).

Сначала — спам-этап. Для сообщения человека (`fromId.isHuman()`) в групповом чате (`peerId.isGroupChat()`)
при `decision.spam-reply.enabled = true` use case вызывает decision-модель один раз через
`AnalyzeMessageOutputPort` (адаптер `DecisionModelOutputAdapter`, ADR 0010/0012):

- `spamProbability >= decision.spam-threshold` → публикуется ровно один `ANSWER_MESSAGE` с
  `ResponseMessage` и текстом `Сработал автодетект спама. Вероятность спама: N%.`
  (`N = round(spamProbability * 100)`), обработка останавливается: `MESSAGE_REQUIRE_ANSWER` и другие
  события не публикуются. В групповом чате `vk-facade` добавит forward-блок исходного сообщения;
  `ai-answer` пропускается.
- отказ decision-модели (`RuntimeException`) — fail-open: WARNING без текста сообщения и без throwable
  (только `peerId` и класс исключения), исключение не уходит в брокер, обработка продолжается по
  правилам ниже. `InterruptedException` восстанавливает флаг прерывания и пробрасывается.
- при `decision.spam-reply.enabled = false` спам-этап модель не вызывает; ленивый интент-вызов после
  `requiredAnswer=true` (личный чат; группа — при упоминании бота или forward от бота) по-прежнему
  отправляет нормализованный текст в System One (PII, ADR 0010/0012).

Если спам не сработал (или проверка неприменима), работает `IsRequireAnswerCommand`
(`IsRequireAnswerCommand.kt:22-33`):

| Условие | Ответ |
|---|---|
| отправитель не человек (`!fromId.isHuman()`) | не нужен |
| не групповой чат (`!peerId.isGroupChat()`) | нужен |
| текст начинается с `!` или `\` | не нужен |
| текст содержит `vk.bot.mention` | нужен |
| последнее пересланное сообщение — от бота (`fromId == vk.bot.id`) | нужен |
| иначе | не нужен |

Если ответ нужен — маршрутизация по итоговому интенту, иначе поток завершается и ленивого вызова нет.

### Маршрутизация интентов

Порядок: спам-этап → `requiredAnswer` → интент. Третий вопрос decision-модели (`intent` =
`help`/`news`/`general`) попадает в `DecisionAnalysis.intent`; точные `/help`/`/помощь` (trim, без учёта
регистра, без аргументов) распознаются детерминированно на интент-этапе, до ленивого вызова, а
`/help`/`/помощь` с аргументами или `@`-суффиксом дают детерминированный `general`. Если анализа ещё нет
(спам-этап неприменим: личный чат, не-человек, `enabled=false`), интент-этап делает ровно один ленивый
вызов; fail-open спам-этапа даёт ровно одну дополнительную попытку (всего ровно два вызова) и `general`
при двух отказах.

| Интент | Событие | Дальше |
|---|---|---|
| `HELP` | ровно один `ANSWER_MESSAGE` со справкой (`responseTo = Message`) | `vk-message-send`; `ai-answer` пропущен |
| `NEWS` | ровно один `NEWS_DIGEST_REQUESTED` с доменным `Message` | `ai` → сводка «что нового» (раздел 5) |
| `GENERAL` | ровно один `MESSAGE_REQUIRE_ANSWER` с доменным `Message` | `ai-answer` |

Если routing key не имеет консюмера, диспетчер не находит `EventProcessor`, пишет ERROR
`Unknown message (type: …): <payload>` (`EventDispatcher.kt:89`; весь payload — текст сообщения,
PII/OTLP) и подтверждает сообщение (`:74-76`). Сейчас консюмер есть у всех семи событий; эта ветка
остаётся общим поведением диспетчера для будущих имён.

## 5. Сводка «что нового» (ai)

```
NEWS_DIGEST_REQUESTED → ai/NewsDigestRequestedEventProcessor
    → ProcessNewsDigestUsecase → ProcessNewsDigestCommandImpl
    → NewsPeriodParser: текст запроса → [from, to] (или период от последнего сообщения автора)
    → in-process persistence: саммари периода + свежие сообщения (≤ summary.batch-size)
    → NewsDigestAiAdapter (3 попытки) → ANSWER_MESSAGE
    → vk-facade/SendMessageEventProcessor → VK API messages.send
```

- `NewsDigestRequestedEventProcessor.process` (`NewsDigestRequestedEventProcessor.kt:17-20`) читает
  доменный `Message` из JSON и передаёт его в `ProcessNewsDigestInputPort`; use case — тонкий делегат
  команды.
- **Период.** `NewsPeriodParser.parse(message.messageText.value, message.date.value)` — чистый домен:
  относительные и абсолютные формы, границы дня в offset-е сообщения. Явный срок длиннее
  `Duration.ofDays(7)` → ответ «Слишком длинный срок: сводка возможна максимум за неделю.» без чтения
  БД, LLM и профилей. Срок не найден → `FindLastStoredMessageByAuthorPort(peerId, fromId,
  beforeConversationMessageId)`; `to` — дата запроса, `from` — дата последнего сообщения автора, если она
  не позже `to` и не старше 7 суток, иначе `to.minusDays(7)`.
- **Данные.** `FindStoredSummariesInPeriodPort` отдаёт только `COMPLETED`-саммари, чей id-диапазон
  пересекает период; `FindStoredMessagesBetweenDatesPort` — ≤ `summary.batch-size` последних сообщений
  периода. Сообщение-запрос исключено (вопрос, а не контент), из свежих сообщений оставлены только те,
  что новее `max(lastMessageId)` саммари периода. Авторы форматируются общим `MessageAuthorFormatter` —
  теми же именами, что у саммаризации.
- **LLM.** `NewsDigestAiService` (`@RegisterAiService` без инструментов и памяти чата, system-промпт
  `{config:aiNewsDigestSystemprompt}`) + `NewsDigestAiAdapter` — ровно три попытки, пустой или
  пробельный ответ считается неудачной попыткой. После третьей — `NewsDigestGenerationException`;
  команда отвечает «Не удалось собрать сводку. Попробуйте позже.» и пишет ERROR только с `peerId` и
  простым именем класса исключения, без текста/PII. Если данных нет вовсе — «Нет сообщений за этот
  период.» без вызова LLM.
- **Запись.** `summaries` не пишется, `SUMMARY_REQUESTED`/`SUMMARY_READY` не публикуются: у команды
  только read-порты и `PublishEventCommand`. Отправляется ровно один `ANSWER_MESSAGE` с
  `responseTo = исходное Message`; ошибки чтения БД и публикации не перехватываются — уходят в
  retry-лестницу брокера.

## 6. Генерация ответа (ai)

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

## 7. Отправка в VK (vk-facade)

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

## 8. Саммаризация

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
- `GenerateSummaryCommandImpl.execute` (`GenerateSummaryCommandImpl.kt:23-60`): создаёт pending-запись,
  читает до 1000 сообщений, форматирует их общим `MessageAuthorFormatter` (имена авторов из профилей
  in-process портов), вызывает
  LLM (`SummarizationAiAdapter` — 3 попытки, `SummarizationAiAdapter.kt:18-41`), сохраняет completed;
  при исключении помечает failed и пробрасывает дальше (`:56-59`).
- Сохранение completed идёт через `SaveStoredCompletedSummaryPort` → in-process адаптер →
  `PersistenceDataUsecase.handleSaveCompletedSummary` (`PersistenceDataUsecase.kt:238-269`), который и
  публикует `SUMMARY_READY` с текстом «📋 Суммаризация обсуждения…».
- `SummaryReadyEventProcessor` (`SummaryReadyEventProcessor.kt:23-54`) строит forward на диапазон
  сообщений `[first, last]` и отправляет через тот же `MessageSendUsecase`.

## 9. Поздравления по расписанию (greetings)

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
| Изоляция профилей | `FetchUserProfileCommandImpl.kt:67-107` | сбой VK при получении профилей не отменяет сохранение сообщения |
| Повтор AI-саммари | `SummarizationAiAdapter.kt:10-40` | 3 попытки внутри одного события |
| Повтор LLM-сводки «что нового» | `NewsDigestAiAdapter` | 3 попытки внутри одного события; финальный отказ — фолбэк-ответ, не retry брокера |
| Детерминированный `random_id` | `MessageSendUsecase.kt:37-43` | повторная отправка того же ответа даёт тот же `random_id` |

## Известные ограничения (не скрывать)

- **Сводка «что нового» без лимитов и без дедупликации.** LLM-вызов (до трёх попыток порта) на каждый
  запрос; at-least-once может дать повторный LLM-вызов и повторный ответ, собственного дедупа и outbox
  нет — дубли ограничивает только детерминированный `random_id` на стороне VK.
- **Календарный день сводки — в offset-е сообщения.** Long Poll отдаёт UTC, поэтому «сегодня»/«вчера»
  считаются как UTC-дни (для UTC+3 граница смещена); бизнес-зона чата — отдельное решение.
- **Саммари периода в сводке не клипуются по датам:** берётся `shortSummary` саммари, чей id-диапазон
  пересекает период, поэтому на границе периода в сводке возможно лишнее.
- **`max-tokens` для сводки не задан**, усечения промпта нет: отказ провайдера по длине контекста даёт
  фолбэк-ответ «Не удалось собрать сводку. Попробуйте позже.».
- **Метрик и спанов сводки нет:** наблюдаемость — брокерская, OTLP-логи и ERROR при отказе LLM; отдельного
  INFO-лога успеха нет.
- **Справка и «что нового» подчиняются `requiredAnswer`.** В групповом чате нужен упоминание бота или
  forward от бота; точная команда — только `/help`/`/помощь` без аргументов, `/help` с аргументами
  уходит в `general`.
- **Kill-switch гасит только спам-этап.** `decision.spam-reply.enabled = false` не выключает ленивый
  интент-вызов после `requiredAnswer=true`; полный откат decision-модели — предыдущий образ.
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
