# 2026-10-28: Конфигурация и секреты

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

Конфиг был продублирован: [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml) и копия в
[`src/infrastructure`](../../src/infrastructure) (удалена в этом проходе) с расходящимися значениями (`vk.api.version` 5.199 против 5.131).
Часть ключей не читалась кодом (`quarkus.jooq.dialect`, `quarkus.hibernate-orm`, `quarkus-flyway`), часть
обязательных ключей существовала только в тестовых конфигах (`vk.bot.id`, `vk.bot.mention`, `vane.*`,
system-промпты), а документация обещала env `DATABASE_URL`/`DB_USERNAME`/`DB_PASSWORD`, которые конфиг не читал.

## Решение

Единственный production-конфиг — [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml) плюс подключаемый
[`application-queues.yml`](../../src/app/src/main/resources/application-queues.yml) ([`application.yml:25-26`](../../src/app/src/main/resources/application.yml), `quarkus.config.locations`). Копии из [`src/infrastructure`](../../src/infrastructure)
удалены; уникальные ключи перенесены в app-конфиг.

### Ключи, которые читает код

| Ключ | Значение / env | Потребитель |
|---|---|---|
| `vk.group.id` | `${VK_GROUP_ID}` | `VkLongPollOutputAdapter` (`groups.getLongPollServer`, ADR 2026-11-05-bots-long-poll-transport) |
| `vk.longpoll.enabled` | `${VK_LONGPOLL_ENABLED:true}` | `LongPollRunner` (старт опроса Bots Long Poll, ADR 2026-11-05-bots-long-poll-transport) |
| `vk.api.token` | `${VK_API_TOKEN}` | [`application.yml:56`](../../src/app/src/main/resources/application.yml) (заголовок `Authorization` для VK) |
| `vk.api.version` | `5.199` | [`VkClient.kt:13`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkClient.kt), [`VkLongPollOutputAdapter.kt:106`](../../src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/output/vk/VkLongPollOutputAdapter.kt) (`v=` в каждом запросе VK) |
| `vk.bot.id` | `${VK_BOT_ID}` | [`IsRequireAnswerCommand.kt:17`](../../src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt) |
| `vk.bot.mention` | `${VK_BOT_MENTION:@simarel}` | [`IsRequireAnswerCommand.kt:19`](../../src/processor/src/main/kotlin/com/simarel/vkbot/processor/command/isRequireAnswer/IsRequireAnswerCommand.kt) |
| `aiUseranswerSystemprompt` | `${USER_ANSWER_SYSTEM_PROMPT:…}` | [`UserAnswerGenerator.kt:17`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/UserAnswerGenerator.kt) |
| `aiSumarizationSystemprompt` | `${SUMMARY_SYSTEM_PROMPT:…}` | [`ConversationSummarizer.kt:11`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/ConversationSummarizer.kt) |
| `aiOcrSystemPrompt` | `${OCR_SYSTEM_PROMPT:…}` | [`ImageDescriber.kt:13`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/ocr/ImageDescriber.kt) |
| `aiGreetingSystemprompt` | `${AI_GREETING_SYSTEM_PROMPT:…}` | [`GreetingTextGenerator.kt:11`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/greeting/GreetingTextGenerator.kt) (`@SystemMessage`, ADR 2026-11-07-daily-greetings) |
| `greetings.cron` | `${GREETINGS_CRON:0 0 0 * * ?}` | [`DailyGreetingRunner.kt:21`](../../src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/adapter/input/schedule/DailyGreetingRunner.kt) (`@Scheduled`; `off`/`disabled` выключает) |
| `greetings.timezone` | `${GREETINGS_TIMEZONE:Europe/Minsk}` | [`DailyGreetingRunner.kt:17,19,21`](../../src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/adapter/input/schedule/DailyGreetingRunner.kt) (`@Scheduled`/`ZoneId.of`) |
| `summary.enabled-chats` | `${SUMMARY_ENABLED_CHATS:}` | [`ProcessSummaryCommandImpl.kt:18`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt); `String` с пустым дефолтом — пустое значение выключает саммаризацию, но старт не валит |
| `summary.threshold` | `${SUMMARY_THRESHOLD:100}` | [`ProcessSummaryCommandImpl.kt:20`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt) |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | [`ProcessSummaryCommandImpl.kt:22`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt) |
| `quarkus.rest-client.vk.url` | `https://api.vk.ru/method` | `VkClient` (`configKey = "vk"`, [`VkClient.kt:12`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/output/vk/VkClient.kt)) |
| `quarkus.rest-client.vane.url` | `${VANE_URL}` | `VaneSearchClient` (`configKey = "vane"`, [`VaneSearchClient.kt:11`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchClient.kt)) |
| `quarkus.rest-client.vane.connect-timeout` / `read-timeout` | `30000` / `120000` | тот же клиент ([`application.yml:60-61`](../../src/app/src/main/resources/application.yml)) |
| `quarkus.datasource.*` | `db-kind: postgresql`, DevServices, pool 2/2/20 | Agroal/jOOQ |
| `quarkus.liquibase.*` | `migrate-at-start: true`, [`db/changelog/db.changelog-master.yaml`](../../src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml) | миграции ([`application.yml:38-40`](../../src/app/src/main/resources/application.yml)) |
| `quarkus.otel.logs/metrics/traces.enabled` | `true` | OpenTelemetry |
| `mp.messaging.*` | очереди/обменники/retry | [`application.yml:92-186`](../../src/app/src/main/resources/application.yml) + [`application-queues.yml:1-32`](../../src/app/src/main/resources/application-queues.yml) |
| `dlq.levels[]` | `retry-count`, `queue-name`, `ttl-ms`, `exchange-name` | [`DlqConfig.kt:6-16`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/config/DlqConfig.kt), [`EventDispatcher.kt:25-26`](../../src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt) |
| `smallrye.messaging.observation.enabled` | `true` | [`application.yml:188-191`](../../src/app/src/main/resources/application.yml) |
| `quarkus.langchain4j.*` | логирование запросов/ответов и трейсинг; OpenAI-совместимый провайдер: `openai.base-url` / `openai.api-key` / `openai.max-retries` / `openai.chat-model.model-name` (ADR 2026-11-05-llm-provider-vllm-openai) | [`application.yml:65-76`](../../src/app/src/main/resources/application.yml) |

Ключей `vk.secret`/`vk.confirmation-code` больше нет: Callback-эндпоинт удалён в ADR 2026-11-05-bots-long-poll-transport, а вход событий VK
идёт исходящим опросом Bots Long Poll через `vk.group.id` и `vk.longpoll.enabled`.

### Dev/test и prod

- DevServices (PostgreSQL, RabbitMQ, LGTM) включены в [`application.yml:29-32,62-64`](../../src/app/src/main/resources/application.yml) и в тестовых
  профилях; production задаётся env. LLM больше не поднимается DevServices: это внешний
  OpenAI-совместимый endpoint (ADR 2026-11-05-llm-provider-vllm-openai). Обязательные env для prod: `VK_GROUP_ID`,
  `VK_API_TOKEN`, `VK_BOT_ID`, `VANE_URL`, `AI_BASE_URL`, `AI_CHAT_MODEL`,
  `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`,
  `RABBITMQ_HOST` (плюс `RABBITMQ_PORT/USERNAME/PASSWORD` при необходимости);
  опциональные — `AI_API_KEY` (дефолт `dummy`), `VK_BOT_MENTION`, `VK_LONGPOLL_ENABLED` (дефолт `true`),
  `SUMMARY_ENABLED_CHATS` (пусто = саммаризация выключена),
  `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`,
  `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`.
- Тестовые конфиги: [`src/app/src/test/resources/application.yaml`](../../src/app/src/test/resources/application.yaml) (профиль по умолчанию для `@QuarkusTest`),
  [`application-integration-test.yaml`](../../src/app/src/test/resources/application-integration-test.yaml) (профиль `integration-test`: RabbitMQ/PostgreSQL DevServices,
  LangChain4j DevServices отключены, WireMock), [`application.yml`](../../src/app/src/main/resources/application.yml) (`%test`-настройки),
  [`src/testing/test-common/src/main/resources/application.yaml`](../../src/testing/test-common/src/main/resources/application.yaml)
  (`vk.*`, `vane.url` для тестов без env).

### Секреты

В репозитории секретов нет: значения приходят из `${ENV}`; `.env` в `.gitignore`. Дефолт-URL не
добавляется ни для Vane, ни для LLM: без `VANE_URL`, `AI_BASE_URL` или `AI_CHAT_MODEL` приложение не
стартует в prod — это осознанно (отказ на старте лучше, чем запрос в неизвестный хост).

## Последствия

- Один файл конфигурации на деплой; второй копии нет.
- Формально объявленный, но не связанный с кодом долг: блок `circuit-breaker.vk-profile`
  ([`application.yml:193-198`](../../src/app/src/main/resources/application.yml)) и ключ `summary.system-prompt` ([`application.yml:23`](../../src/app/src/main/resources/application.yml)) никем не читаются.
  Это зафиксировано как долг; удаление или подключение — отдельная задача.
- Наблюдаемость: OTel включён, Prometheus-endpoint **не** обещается (в сборке нет
  `quarkus-micrometer-registry-prometheus`).
- Health-эндпоинты — стандартные Quarkus `/q/health`, `/q/health/ready`, `/q/health/live`
  (расширение `quarkus-smallrye-health`, [`src/app/build.gradle.kts:37`](../../src/app/build.gradle.kts)); `/health` из старого README не существует.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Конфиг на модуль | Расхождение копий уже произошло (5.199 против 5.131); второй источник истины не нужен |
| Коммит секретов или дефолт-заглушки | Утечка; отказ на старте дешевле |
| Дефолт-URL Vane | Молчаливый запрос в неизвестный хост вместо явного отказа |
| Prometheus-registry сейчас | Вне DoD; OTel-метрики уже объявлены, реестр — отдельная задача |
| Оставить `quarkus.jooq`/`hibernate-orm`/`flyway` | Ключи мертвы: расширений в сборке нет, миграции — Liquibase |
