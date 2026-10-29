# Карта возможностей VK Bot

VK-бот — один деплой (`:src:app`), который принимает Callback-события сообщества VK, хранит переписку,
решает, нужен ли AI-ответ, генерирует его и отправляет в VK, а на длинных обсуждениях делает
саммаризацию. Карта — продуктовый рекорд, написанный из кода: строка на возможность, дальше файл
возможности со статусом, точками входа, данными, отношениями владения, ADR, acceptance criteria и тестами.

Карту проверяет `./scripts/check-capabilities.sh`; тот же чек входит в `check` задачей
`./gradlew capabilitiesCheck` и в CI джобом `docs`. Чек падает, если в индексе или в capability-файле
указан несуществующий путь кода, acceptance criterion не находит свой тест, `ADR NNNN` не резолвится,
статус вне `planned`/`delivered`/`deprecated`, id не совпадает с именем файла или файл возможности не
указан в индексе.

## Пути пользователя (journeys)

| Journey | Шаги |
|---|---|
| `journey-answer` — «Диалог с ботом»: участник пишет боту, бот отвечает в чате | `vk-webhook` → `message-storage` → `answer-decision` → `ai-answer` → `vk-message-send`; транспорт событий — `event-dispatch`, имена участников даёт `vk-profiles`, веб-поиск внутри ответа — `ai-tools-vane` |
| `journey-summary` — «Итог обсуждения»: бот подводит итог переписки и присылает его с forward-блоком | `message-storage` → `conversation-summary` → `vk-message-send`; транспорт событий — `event-dispatch` |
| `journey-stored-data` — «Сохранённые данные по запросу»: внешний клиент читает переписку, саммари и профили | `message-storage` (REST `/persistence/*`) |

## Возможности

| id | Что делает | Точки входа |
|---|---|---|
| [`vk-webhook`](vk-webhook.md) | принимает VK Callback: секрет, confirmation-код, `message_new`/`message_reply`, DMZ-маппинг JSON → домен, публикация `MESSAGE_NEW` | `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/VkMediatorRouterImpl.kt:16-40`, `src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/VkMediatorRouterSecurityDecorator.kt:11-29` |
| [`message-storage`](message-storage.md) | хранит сообщения, профили и саммари в PostgreSQL; читает их по REST и по in-process портам; публикует `MESSAGE_RECEIVED` и `SUMMARY_REQUESTED` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/event/MessageNewEventProcessor.kt:12-22`, `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/rest/PersistenceDataController.kt:21-127` |
| [`answer-decision`](answer-decision.md) | по правилам решает, нужен ли AI-ответ, и публикует `MESSAGE_REQUIRE_ANSWER` | `src/processor/src/main/kotlin/com/simarel/vkbot/processor/adapter/input/event/messageReceived/MessageReceivedEventProcessor.kt:12-22`, `src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:22-33` |
| [`ai-answer`](ai-answer.md) | собирает контекст чата и профили, генерирует ответ LLM и публикует `ANSWER_MESSAGE` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessor.kt:12-22`, `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommand.kt:32-47` |
| [`vk-message-send`](vk-message-send.md) | отправляет сообщение в VK API: peer_id, текст, forward-блок, детерминированный `random_id`, ошибка VK → исключение для retry | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SendMessageEventProcessor.kt:12-22`, `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/usecase/message/MessageSendUsecase.kt:15-44` |
| [`conversation-summary`](conversation-summary.md) | по порогу и включённым чатам саммаризирует переписку, ведёт статусы `PENDING`/`COMPLETED`/`FAILED`, сохраняет итог через `message-storage` (публикует `SUMMARY_READY`), `vk-facade` присылает итог с forward-блоком | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessor.kt:12-28`, `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt:36-125` |
| [`vk-profiles`](vk-profiles.md) | получает профили пользователей и групп из VK API, отдаёт их по REST и in-process портам | `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/rest/VkFacadeController.kt:14-32`, `src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/inprocess/VkProfileInProcessAdapter.kt:8-36` |
| [`event-dispatch`](event-dispatch.md) | доставляет события через RabbitMQ: routing key = имя события, retry-лестница 5s→1h, финальный DLQ | `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt:63-111`, `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/adapter/output/mq/PublishEventOutputAdapter.kt:15-33` |
| [`ai-tools-vane`](ai-tools-vane.md) | даёт LLM три инструмента веб-поиска через Vane: `search_web`, `search_academic`, `search_discussions` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapter.kt:10-32`, `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchClient.kt:11-25` |

## Не отображено в карте и почему

| Что есть в коде | Почему не строка карты |
|---|---|
| OCR: `RecognizeImagesCommand`, `RecognizeImagesCommandImpl`, `ImageRecognitionOutputAdapter`, `OcrAiService` | кода достаточно (`src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/ocr/RecognizeImagesCommandImpl.kt:8-27`), но команду не вызывает ни один use case и у неё нет входного адаптера — недостижимая возможность, а не продуктовая |
| `/q/health`, `/q/health/ready`, `/q/health/live` | даёт расширение `quarkus-smallrye-health` (`src/app/build.gradle.kts`), в дереве нет кода, который её реализует; точка входа — конфигурация платформы, не поведение продукта |
| OpenTelemetry и observation брокера | только конфигурация расширений в `src/app/src/main/resources/application.yml`, собственного кода с трейсами/метриками нет |
| logging-декораторы `Command`/`Port` | сквозная инфраструктура без пользовательского поведения: `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/decorator/logging/CommandLoggingDecorator.kt` |
| канал `summary-ready-queue` | объявлен в `src/app/src/main/resources/application.yml`, но `@Incoming("summary-ready-queue")` в коде нет — мёртвая конфигурация, описана в `docs/development/configuration.md` |
| `@QuarkusMain`-запуск | `src/app/src/main/kotlin/com/simarel/vkbot/app/Application.kt:6-12` — деплой, а не возможность продукта |
| тестовые модули `test-fixtures`, `test-common`, `arch-tests` | инфраструктура тестов, не поведение продукта |
