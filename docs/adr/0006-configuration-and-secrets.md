# ADR 0006: Конфигурация и секреты

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

Конфиг был продублирован: `src/app/src/main/resources/application.yml` и копия в
`src/infrastructure` (удалена в этом проходе) с расходящимися значениями (`vk.api.version` 5.199 против 5.131).
Часть ключей не читалась кодом (`quarkus.jooq.dialect`, `quarkus.hibernate-orm`, `quarkus-flyway`), часть
обязательных ключей существовала только в тестовых конфигах (`vk.bot.id`, `vk.bot.mention`, `vane.*`,
system-промпты), а документация обещала env `DATABASE_URL`/`DB_USERNAME`/`DB_PASSWORD`, которые конфиг не читал.

## Решение

Единственный production-конфиг — `src/app/src/main/resources/application.yml` плюс подключаемый
`application-queues.yml` (`application.yml`, `quarkus.config.locations`). Копии из `src/infrastructure`
удалены; уникальные ключи перенесены в app-конфиг.

### Ключи, которые читает код

| Ключ | Значение / env | Потребитель |
|---|---|---|
| `vk.group.id` | `${VK_GROUP_ID}` | `VkLongPollOutputAdapter` (`groups.getLongPollServer`, ADR 0008) |
| `vk.longpoll.enabled` | `${VK_LONGPOLL_ENABLED:true}` | `LongPollRunner` (старт опроса Bots Long Poll, ADR 0008) |
| `vk.api.token` | `${VK_API_TOKEN}` | `application.yml` (заголовок `Authorization` для VK) |
| `vk.api.version` | `5.199` | `VkClient.kt:13`, `VkLongPollOutputAdapter.kt:99` (`v=` в каждом запросе VK) |
| `vk.bot.id` | `${VK_BOT_ID}` | `IsRequireAnswerCommand.kt:17` |
| `vk.bot.mention` | `${VK_BOT_MENTION:@simarel}` | `IsRequireAnswerCommand.kt:19` |
| `aiUseranswerSystemprompt` | `${USER_ANSWER_SYSTEM_PROMPT:…}` | `UserAnswerAiService.kt:17` |
| `aiSumarizationSystemprompt` | `${SUMMARY_SYSTEM_PROMPT:…}` | `SummarizationAiService.kt:11` |
| `aiOcrSystemPrompt` | `${OCR_SYSTEM_PROMPT:…}` | `OcrAiService.kt:13` |
| `summary.enabled-chats` | `${SUMMARY_ENABLED_CHATS:}` | `ProcessSummaryCommandImpl.kt:18`; `String` с пустым дефолтом — пустое значение выключает саммаризацию, но старт не валит |
| `summary.threshold` | `${SUMMARY_THRESHOLD:100}` | `ProcessSummaryCommandImpl.kt:20` |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | `ProcessSummaryCommandImpl.kt:22` |
| `quarkus.rest-client.vk.url` | `https://api.vk.ru/method` | `VkClient` (`configKey = "vk"`, `VkClient.kt:12`) |
| `quarkus.rest-client.vane.url` | `${VANE_URL}` | `VaneSearchClient` (`configKey = "vane"`, `VaneSearchClient.kt:11`) |
| `quarkus.rest-client.vane.connect-timeout` / `read-timeout` | `30000` / `120000` | тот же клиент (`application.yml`) |
| `quarkus.datasource.*` | `db-kind: postgresql`, DevServices, pool 2/2/20 | Agroal/jOOQ |
| `quarkus.liquibase.*` | `migrate-at-start: true`, `db/changelog/db.changelog-master.yaml` | миграции (`application.yml`) |
| `quarkus.otel.logs/metrics/traces.enabled` | `true` | OpenTelemetry |
| `mp.messaging.*` | очереди/обменники/retry | `application.yml` + `application-queues.yml:1-32` |
| `dlq.levels[]` | `retry-count`, `queue-name`, `ttl-ms`, `exchange-name` | `DlqConfig.kt:6-16`, `EventDispatcher.kt:25-26` |
| `smallrye.messaging.observation.enabled` | `true` | `application.yml` |
| `quarkus.langchain4j.*` | логирование запросов/ответов и трейсинг; OpenAI-совместимый провайдер: `openai.base-url` / `openai.api-key` / `openai.max-retries` / `openai.chat-model.model-name` (ADR 0007) | `application.yml` |

Ключей `vk.secret`/`vk.confirmation-code` больше нет: Callback-эндпоинт удалён в ADR 0008, а вход событий VK
идёт исходящим опросом Bots Long Poll через `vk.group.id` и `vk.longpoll.enabled`.

### Dev/test и prod

- DevServices (PostgreSQL, RabbitMQ, LGTM) включены в `application.yml` и в тестовых
  профилях; production задаётся env. LLM больше не поднимается DevServices: это внешний
  OpenAI-совместимый endpoint (ADR 0007). Обязательные env для prod: `VK_GROUP_ID`,
  `VK_API_TOKEN`, `VK_BOT_ID`, `VANE_URL`, `AI_BASE_URL`, `AI_CHAT_MODEL`,
  `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`,
  `RABBITMQ_HOST` (плюс `RABBITMQ_PORT/USERNAME/PASSWORD` при необходимости);
  опциональные — `AI_API_KEY` (дефолт `dummy`), `VK_BOT_MENTION`, `VK_LONGPOLL_ENABLED` (дефолт `true`),
  `SUMMARY_ENABLED_CHATS` (пусто = саммаризация выключена),
  `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`,
  `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`.
- Тестовые конфиги: `src/app/src/test/resources/application.yaml` (профиль по умолчанию для `@QuarkusTest`),
  `application-integration-test.yaml` (профиль `integration-test`: RabbitMQ/PostgreSQL DevServices,
  LangChain4j DevServices отключены, WireMock), `application.yml` (`%test`-настройки),
  `src/testing/test-common/src/main/resources/application.yaml`
  (`vk.*`, `vane.url` для тестов без env).

### Секреты

В репозитории секретов нет: значения приходят из `${ENV}`; `.env` в `.gitignore`. Дефолт-URL не
добавляется ни для Vane, ни для LLM: без `VANE_URL`, `AI_BASE_URL` или `AI_CHAT_MODEL` приложение не
стартует в prod — это осознанно (отказ на старте лучше, чем запрос в неизвестный хост).

## Последствия

- Один файл конфигурации на деплой; второй копии нет.
- Формально объявленный, но не связанный с кодом долг: блок `circuit-breaker.vk-profile`
  (`application.yml`) и ключ `summary.system-prompt` (`application.yml`) никем не читаются.
  Это зафиксировано как долг; удаление или подключение — отдельная задача.
- Наблюдаемость: OTel включён, Prometheus-endpoint **не** обещается (в сборке нет
  `quarkus-micrometer-registry-prometheus`).
- Health-эндпоинты — стандартные Quarkus `/q/health`, `/q/health/ready`, `/q/health/live`
  (расширение `quarkus-smallrye-health`, `src/app/build.gradle.kts:37`); `/health` из старого README не существует.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Конфиг на модуль | Расхождение копий уже произошло (5.199 против 5.131); второй источник истины не нужен |
| Коммит секретов или дефолт-заглушки | Утечка; отказ на старте дешевле |
| Дефолт-URL Vane | Молчаливый запрос в неизвестный хост вместо явного отказа |
| Prometheus-registry сейчас | Вне DoD; OTel-метрики уже объявлены, реестр — отдельная задача |
| Оставить `quarkus.jooq`/`hibernate-orm`/`flyway` | Ключи мертвы: расширений в сборке нет, миграции — Liquibase |
