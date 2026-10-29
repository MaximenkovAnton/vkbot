# Конфигурация

Источник истины — `src/app/src/main/resources/application.yml` (179 строк) и подключаемый
`src/app/src/main/resources/application-queues.yml` (`quarkus.config.locations: application-queues.yml`,
`application.yml:18-20`). Других production-конфигов в дереве нет; копии из `src/infrastructure` удалены
([ADR 0006](../adr/0006-configuration-and-secrets.md)).

Значения `${VAR}` без дефолта обязательны: без них приложение не стартует. Тот же эффект у
`summary.enabled-chats`: его пустой дефолт тоже не даёт стартовать (см. «Промпты и саммаризация»).
Секреты в репозиторий не коммитятся, `.env` в `.gitignore`.

## VK

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `vk.secret` | `${VK_SECRET}` | секрет Callback API; неверный/пустой → 403 | `receiver/adapter/input/vk/VkMediatorRouterSecurityDecorator.kt:17` |
| `vk.confirmation-code` | `${VK_CONFIRMATION_CODE}` | ответ на `confirmation` | `receiver/config/ConstantConfiguration.kt:14` |
| `vk.api.version` | `5.199` | query-параметр `v` каждого запроса VK | `vkFacade/adapter/output/vk/VkClient.kt:13` |
| `vk.api.token` | `${VK_API_TOKEN}` | заголовок `Authorization: Bearer …` | `application.yml:47-50` |
| `vk.bot.id` | `${VK_BOT_ID}` | id бота в правиле «forward от бота → ответить» | `processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:17` |
| `vk.bot.mention` | `${VK_BOT_MENTION:@simarel}` | упоминание, включающее ответ в чате | там же `:19` |

Получение значений: сообщество VK → «Управление» → «API» → Callback API (URL `https://<host>/vk/callback`,
секрет и строка подтверждения генерируются VK) → «Ключи доступа» (токен с правами на сообщения).

## Промпты и саммаризация

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `aiUseranswerSystemprompt` | `${USER_ANSWER_SYSTEM_PROMPT:You are a helpful ai chatbot in a chat.}` | system-сообщение сервиса ответов | `ai/adapter/output/ai/answer/UserAnswerAiService.kt:17` |
| `aiSumarizationSystemprompt` | `${SUMMARY_SYSTEM_PROMPT:You are a chat summarizer. …}` | system-сообщение саммаризации | `ai/adapter/output/ai/summarization/SummarizationAiService.kt:11` |
| `aiOcrSystemPrompt` | `${OCR_SYSTEM_PROMPT:Describe every image in detail …}` | system-сообщение OCR | `ai/adapter/output/ai/ocr/OcrAiService.kt:13` |
| `summary.enabled-chats` | `${SUMMARY_ENABLED_CHATS:}` | **обязателен:** непустой список peerId через запятую (чаты, где включена саммаризация). Пустое/отсутствующее значение не конвертируется (`SRCFG00040`), приложение не стартует | `ai/command/summary/ProcessSummaryCommandImpl.kt:18-34` |
| `summary.threshold` | `${SUMMARY_THRESHOLD:100}` | минимум сообщений для саммаризации (когда прошлое саммари есть) | там же `:20,72-79` |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | максимум сообщений в выборке | там же `:22,108-125` |
| `summary.system-prompt` | `${SUMMARY_SYSTEM_PROMPT:…}` | **никем не читается** — мёртвый дубль `aiSumarizationSystemprompt` (`application.yml:17`) | — |

`summary.enabled-chats` — единственный ключ, у которого пустой дефолт не работает: SmallRye Config считает
пустую строку `null` и валидация инъекции `ProcessSummaryCommandImpl.kt:18` валит старт с
`SRCFG00040: The config property summary.enabled-chats is defined as the empty String ("") which the
following Converter considered to be null`. Поэтому `SUMMARY_ENABLED_CHATS` обязателен и непуст, а
формулировка «пусто = саммаризация выключена» неверна: пустое значение — отказ старта, а не выключение.

## База данных и миграции

| Ключ | Значение по умолчанию | Эффект |
|---|---|---|
| `quarkus.datasource.db-kind` | `postgresql` | драйвер PostgreSQL (`quarkus-jdbc-postgresql`) |
| `quarkus.datasource.devservices.enabled` | `true` | поднять PostgreSQL в Docker в dev/test |
| `quarkus.datasource.jdbc.initial-size` | `2` | начальный размер пула Agroal |
| `quarkus.datasource.jdbc.min-size` | `2` | минимальный размер пула |
| `quarkus.datasource.jdbc.max-size` | `20` | максимальный размер пула |
| `quarkus.datasource.jdbc.acquisition-timeout` | `30s` | таймаут получения соединения |
| `quarkus.liquibase.migrate-at-start` | `true` | применять changesets при старте |
| `quarkus.liquibase.change-log` | `db/changelog/db.changelog-master.yaml` | master-чейнджлог (`src/persistence/src/main/resources/db/changelog/`) |

В prod DevServices выключены автоматически (они живут только в dev/test), поэтому соединение задаётся
через `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`
(стандартное отображение env → `quarkus.datasource.*`).

## RabbitMQ и очереди

`application.yml:56-58` задаёт только DevServices (`quarkus.rabbitmq.devservices.image-name: rabbitmq:latest`);
в prod параметры коннектора SmallRye RabbitMQ (`rabbitmq-host`, `rabbitmq-port`, `rabbitmq-username`,
`rabbitmq-password`) задаются конфигурацией/переменными окружения — в текущем `application.yml` их нет,
это делает деплой.

Очереди (`mp.messaging.*`, `application.yml:66-167`):

| Канал | Тип | Exchange / queue | Особенности |
|---|---|---|---|
| `all-events-queue` | incoming | `all-events` / `queue` | DLX `retry-5s-exchange` |
| `retry-5s-queue` … `retry-1h-queue` | incoming | `retry-*` / `retry-*` | TTL 5s/30s/1m/15m/1h, DLX обратно в `all-events` |
| `dlq-final-queue` | incoming | `dlq-final` | без TTL, ручной разбор |
| `summary-ready-queue` | incoming | `all-events` / `summary-ready` | DLX `retry-5s-exchange`; **в коде нет `@Incoming` для этого канала** |
| `events-exchange` | outgoing | `all-events` | публикация событий, routing key = имя события |
| `retry-*-out`, `dlq-final-out` | outgoing | соответствующие exchange | перекладывание при retry |

`dlq.levels[]` (`application-queues.yml:1-32`) связывает уровень retry с очередью и TTL; читается
`infrastructure/config/DlqConfig.kt:6-16` и используется `EventDispatcher.kt:25-26,90-111`.

`smallrye.messaging.observation.enabled: true` (`application.yml:169-172`) включает наблюдаемость брокера.

## REST-клиенты и LLM

| Ключ | Значение | Эффект |
|---|---|---|
| `quarkus.rest-client.scope` | `jakarta.inject.Singleton` | один клиент на приложение |
| `quarkus.rest-client.logging.scope` | `request-response` | логирование запрос/ответ всех REST-клиентов |
| `quarkus.rest-client.vk.url` | `https://api.vk.ru/method` | база VK API (`configKey = "vk"`) |
| `quarkus.rest-client.vane.url` | `${VANE_URL}` | база Vane (`configKey = "vane"`); обязательна |
| `quarkus.rest-client.vane.connect-timeout` | `30000` | таймаут соединения Vane |
| `quarkus.rest-client.vane.read-timeout` | `120000` | таймаут чтения Vane |

Ollama/LangChain4j: в production-конфиге `quarkus.langchain4j.*` задан только логированием
(`log-requests: true`, `log-responses: true`, `tracing.include-prompt/completion`, `application.yml:59-64`).
Модель в dev поднимается DevServices; в prod endpoint/модель задаются стандартными ключами
`quarkus.langchain4j.ollama.base-url` / `chat-model.model-id` (так они заданы в тестах:
`src/app/src/test/resources/application-integration-test.yaml:85-92`, `WireMockTestResource.kt:24-31`).
В текущем `application.yml` их нет — это задача деплоя.

## OpenTelemetry

| Ключ | Значение | Эффект |
|---|---|---|
| `quarkus.otel.logs.enabled` | `true` | экспорт логов по OTLP |
| `quarkus.otel.logs.level` | `INFO` | минимальный уровень экспортируемых логов |
| `quarkus.otel.metrics.enabled` | `true` | экспорт метрик |
| `quarkus.otel.traces.enabled` | `true` | экспорт трейсов |

Эндпоинт OTLP по умолчанию — `http://localhost:4317`; без коллектора в логах видны ошибки экспорта
(наблюдалось в тестах). Prometheus-реестр не подключён, `/metrics` нет.

## Мёртвые и неподключённые ключи

| Ключ | Почему мёртвый |
|---|---|
| `circuit-breaker.vk-profile` (`application.yml:174-179`) | ни одной аннотации `@CircuitBreaker`/`@Retry` в коде нет; ключ никем не читается |
| `summary.system-prompt` | не читается, см. выше |
| `summary-ready-queue` | канал объявлен, `@Incoming("summary-ready-queue")` в коде нет |

## Профили и тестовая конфигурация

- Production-профилей `%dev`/`%prod` в `src/app/src/main/resources` нет. Есть `%test` в
  `src/app/src/test/resources/application.yml:1-8` (`hibernate-orm.enabled: false`, `liquibase.enabled: false`,
  `summary.enabled-chats: 1,2,3`).
- `src/app/src/test/resources/application.yaml` — базовая тестовая конфигурация: `vk.*`, `summary.enabled-chats: 1,2,3`,
  DevServices вкл., observability off, `vane.url: http://localhost:8075`.
- `src/app/src/test/resources/application-integration-test.yaml` — профиль `integration-test`: RabbitMQ 3.12
  DevServices (shared, timeout 120s), PostgreSQL DevServices с `SELECT 1`, graceful shutdown, `auto-bind-exchange: false`
  для всех каналов, Ollama DevServices off (мокается WireMock), Liquibase вкл.
- `src/testing/test-common/src/main/resources/application.yaml` — общие значения для модульных тестов:
  `vk.*`, DevServices off, observability off, `vane.url`.

## Health

Расширение `quarkus-smallrye-health` подключено (`src/app/build.gradle.kts:36`), отдельных ключей нет —
действуют умолчания Quarkus: `/q/health`, `/q/health/ready`, `/q/health/live`. Путей `/health` и `/metrics` нет.
Эндпоинты сейчас отдают **503 DOWN**: семь incoming-каналов (`retry-*-queue`, `dlq-final-queue`,
`summary-ready-queue`) не имеют downstream-консьюмера и попадают в messaging-проверку как `[KO]`
(`SRMSG00208`). Health не входит в гейты; вывод и условие починки — `deployment.md`, раздел
«Health и наблюдаемость».

## jOOQ codegen (выключен)

`./gradlew :src:persistence:generateJooq` существует, но выполняется только при заданном `JOOQ_JDBC_URL`
(`src/persistence/build.gradle.kts:89-125`, `onlyIf { System.getenv("JOOQ_JDBC_URL") != null }`).
Переменные: `JOOQ_JDBC_URL`, `JOOQ_JDBC_USER`, `JOOQ_JDBC_PASSWORD`. Генерация не нужна для сборки:
таблицы описаны вручную в `persistence/adapter/output/persistence/jooq/Tables.kt:6-57`.

## Секреты

В репозитории нет ни одного секрета. Обязательные env для prod: `VK_SECRET`, `VK_CONFIRMATION_CODE`,
`VK_API_TOKEN`, `VK_BOT_ID`, `VANE_URL`, `SUMMARY_ENABLED_CHATS` (непустой), `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`; опциональные — `VK_BOT_MENTION`,
`USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`, `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`.
Для RabbitMQ prod-параметры коннектора задаёт деплой (см. выше).
