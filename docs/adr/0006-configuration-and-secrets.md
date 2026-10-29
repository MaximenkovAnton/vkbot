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
`application-queues.yml` (`application.yml:19-20`, `quarkus.config.locations`). Копии из `src/infrastructure`
удалены; уникальные ключи перенесены в app-конфиг.

### Ключи, которые читает код

| Ключ | Значение / env | Потребитель |
|---|---|---|
| `vk.secret` | `${VK_SECRET}` | `VkMediatorRouterSecurityDecorator.kt:17` |
| `vk.confirmation-code` | `${VK_CONFIRMATION_CODE}` | `ConstantConfiguration.kt:14` |
| `vk.api.token` | `${VK_API_TOKEN}` | `application.yml:50` (заголовок `Authorization` для VK) |
| `vk.api.version` | `5.199` | `VkClient.kt:13` (`v=` в каждом запросе VK) |
| `vk.bot.id` | `${VK_BOT_ID}` | `IsRequireAnswerCommand.kt:17` |
| `vk.bot.mention` | `${VK_BOT_MENTION:@simarel}` | `IsRequireAnswerCommand.kt:19` |
| `aiUseranswerSystemprompt` | `${USER_ANSWER_SYSTEM_PROMPT:…}` | `UserAnswerAiService.kt:17` |
| `aiSumarizationSystemprompt` | `${SUMMARY_SYSTEM_PROMPT:…}` | `SummarizationAiService.kt:11` |
| `aiOcrSystemPrompt` | `${OCR_SYSTEM_PROMPT:…}` | `OcrAiService.kt:13` |
| `summary.enabled-chats` | `${SUMMARY_ENABLED_CHATS:}` | `ProcessSummaryCommandImpl.kt:18`; пустое значение не конвертируется (`SRCFG00040`), поэтому переменная обязательна и непуста |
| `summary.threshold` | `${SUMMARY_THRESHOLD:100}` | `ProcessSummaryCommandImpl.kt:20` |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | `ProcessSummaryCommandImpl.kt:22` |
| `quarkus.rest-client.vk.url` | `https://api.vk.ru/method` | `VkClient` (`configKey = "vk"`, `VkClient.kt:12`) |
| `quarkus.rest-client.vane.url` | `${VANE_URL}` | `VaneSearchClient` (`configKey = "vane"`, `VaneSearchClient.kt:11`) |
| `quarkus.rest-client.vane.connect-timeout` / `read-timeout` | `30000` / `120000` | тот же клиент (`application.yml:54-55`) |
| `quarkus.datasource.*` | `db-kind: postgresql`, DevServices, pool 2/2/20 | Agroal/jOOQ |
| `quarkus.liquibase.*` | `migrate-at-start: true`, `db/changelog/db.changelog-master.yaml` | миграции (`application.yml:32-34`) |
| `quarkus.otel.logs/metrics/traces.enabled` | `true` | OpenTelemetry |
| `mp.messaging.*` | очереди/обменники/retry | `application.yml:66-167` + `application-queues.yml:1-32` |
| `dlq.levels[]` | `retry-count`, `queue-name`, `ttl-ms`, `exchange-name` | `DlqConfig.kt:6-16`, `EventDispatcher.kt:25-26` |
| `smallrye.messaging.observation.enabled` | `true` | `application.yml:169-172` |
| `quarkus.langchain4j.*` | логирование запросов/ответов и трейсинг | `application.yml:59-64` |

### Dev/test и prod

- DevServices (PostgreSQL, RabbitMQ, Ollama, LGTM) включены в `application.yml:23-26,56-58` и в тестовых
  профилях; production задаётся env. Обязательные env для prod: `VK_SECRET`, `VK_CONFIRMATION_CODE`,
  `VK_API_TOKEN`, `VK_BOT_ID`, `VANE_URL`, `SUMMARY_ENABLED_CHATS` (непустой: `${SUMMARY_ENABLED_CHATS:}`
  разворачивается в пустую строку, которую конвертер отвергает с `SRCFG00040`, — отказ старта),
  `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`,
  `RABBITMQ_HOST` (плюс `RABBITMQ_PORT/USERNAME/PASSWORD` при необходимости);
  опциональные — `VK_BOT_MENTION`, `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`,
  `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`.
- Тестовые конфиги: `src/app/src/test/resources/application.yaml` (профиль по умолчанию для `@QuarkusTest`),
  `application-integration-test.yaml` (профиль `integration-test`: RabbitMQ/PostgreSQL DevServices, Ollama
  отключён, WireMock), `application.yml` (`%test`-настройки), `src/testing/test-common/src/main/resources/application.yaml`
  (`vk.*`, `vane.url` для тестов без env).

### Секреты

В репозитории секретов нет: значения приходят из `${ENV}`; `.env` в `.gitignore`. Дефолт-URL Vane не
добавляется: без `VANE_URL` приложение не стартует в prod — это осознанно (отказ на старте лучше, чем
запрос в неизвестный хост).

## Последствия

- Один файл конфигурации на деплой; второй копии нет.
- Формально объявленный, но не связанный с кодом долг: блок `circuit-breaker.vk-profile`
  (`application.yml:174-179`) и ключ `summary.system-prompt` (`application.yml:17`) никем не читаются.
  Это зафиксировано как долг; удаление или подключение — отдельная задача.
- Наблюдаемость: OTel включён, Prometheus-endpoint **не** обещается (в сборке нет
  `quarkus-micrometer-registry-prometheus`).
- Health-эндпоинты — стандартные Quarkus `/q/health`, `/q/health/ready`, `/q/health/live`
  (расширение `quarkus-smallrye-health`, `src/app/build.gradle.kts:36`); `/health` из старого README не существует.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Конфиг на модуль | Расхождение копий уже произошло (5.199 против 5.131); второй источник истины не нужен |
| Коммит секретов или дефолт-заглушки | Утечка; отказ на старте дешевле |
| Дефолт-URL Vane | Молчаливый запрос в неизвестный хост вместо явного отказа |
| Prometheus-registry сейчас | Вне DoD; OTel-метрики уже объявлены, реестр — отдельная задача |
| Оставить `quarkus.jooq`/`hibernate-orm`/`flyway` | Ключи мертвы: расширений в сборке нет, миграции — Liquibase |
