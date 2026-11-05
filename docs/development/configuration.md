# Конфигурация

Источник истины — `src/app/src/main/resources/application.yml` и подключаемый
`src/app/src/main/resources/application-queues.yml` (`quarkus.config.locations: application-queues.yml`,
`application.yml:20-22`). Других production-конфигов в дереве нет; копии из `src/infrastructure` удалены
([ADR 0006](../adr/0006-configuration-and-secrets.md)).

Значения `${VAR}` без дефолта обязательны: без них приложение не стартует.
Секреты в репозиторий не коммитятся, `.env` в `.gitignore`.

## VK

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `vk.group.id` | `${VK_GROUP_ID}` | положительный id сообщества для `groups.getLongPollServer`; без него приложение не стартует | `receiver/adapter/output/vk/VkLongPollOutputAdapter.kt` (`group_id` запроса), `receiver/adapter/input/vk/LongPollRunner.kt`, `vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt` (сообщество итога) |
| `vk.longpoll.enabled` | `${VK_LONGPOLL_ENABLED:true}` | включает опрос Bots Long Poll при старте; `false` — цикл опроса не запускается (тестовый профиль) | `receiver/adapter/input/vk/LongPollRunner.kt` |
| `vk.api.version` | `5.199` | query-параметр `v` каждого запроса VK | `vkFacade/adapter/output/vk/VkClient.kt:13`, `receiver/adapter/output/vk/VkLongPollOutputAdapter.kt:99` (`groups.getLongPollServer`) |
| `vk.api.token` | `${VK_API_TOKEN}` | заголовок `Authorization: Bearer …` | `application.yml:49-52` |
| `vk.bot.id` | `${VK_BOT_ID}` | id автора сообщений бота: правило «forward от бота → ответить» и автор итога саммаризации | `processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:17`, `vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt` |
| `vk.bot.mention` | `${VK_BOT_MENTION:@simarel}` | упоминание, включающее ответ в чате | там же `:19` |

Ключей `vk.secret` и `vk.confirmation-code` больше нет: Callback-эндпоинт удалён, события приходят опросом
Bots Long Poll ([ADR 0008](../adr/0008-bots-long-poll-transport.md)).

Получение значений: сообщество VK → «Управление» → «API» → Bots Long Poll API: включить Long Poll API с
событием `message_new` и отключить Callback API; `VK_GROUP_ID` — положительный id сообщества из того же
раздела; `VK_API_TOKEN` — «Ключи доступа» (токен с правами на сообщения). Порядок переключения —
[deployment.md](deployment.md).

## Промпты и саммаризация

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `aiUseranswerSystemprompt` | `${USER_ANSWER_SYSTEM_PROMPT:You are a helpful ai chatbot in a chat.}` | system-сообщение сервиса ответов | `ai/adapter/output/ai/answer/UserAnswerAiService.kt:17` |
| `aiSumarizationSystemprompt` | `${SUMMARY_SYSTEM_PROMPT:You are a chat summarizer. …}` | system-сообщение саммаризации | `ai/adapter/output/ai/summarization/SummarizationAiService.kt:11` |
| `aiOcrSystemPrompt` | `${OCR_SYSTEM_PROMPT:Describe every image in detail …}` | system-сообщение OCR | `ai/adapter/output/ai/ocr/OcrAiService.kt:13` |
| `summary.enabled-chats` | `${SUMMARY_ENABLED_CHATS:}` | список peerId через запятую (чаты, где включена саммаризация). Ключ опционален: пустое/отсутствующее значение даёт пустой список и выключает саммаризацию, приложение стартует | `ai/command/summary/ProcessSummaryCommandImpl.kt:18-34` |
| `summary.threshold` | `${SUMMARY_THRESHOLD:100}` | минимум сообщений для саммаризации (когда прошлое саммари есть) | там же `:20,72-79` |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | максимум сообщений в выборке | там же `:22,108-125` |
| `summary.system-prompt` | `${SUMMARY_SYSTEM_PROMPT:…}` | **никем не читается** — мёртвый дубль `aiSumarizationSystemprompt` (`application.yml:19`) | — |

`summary.enabled-chats` биндится как `String` с `defaultValue = ""` (`ProcessSummaryCommandImpl.kt:18`),
поэтому пустая строка конвертером не отвергается: приложение стартует, `split(",")` даёт пустой набор
чатов и саммаризация выключается. `SRCFG00040` на этом ключе не возникает — он появляется только у
конвертеров, считающих пустую строку `null` (числа, списки).

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

`application.yml:58-60` задаёт только DevServices (`quarkus.rabbitmq.devservices.image-name: rabbitmq:latest`);
в prod параметры коннектора SmallRye RabbitMQ (`rabbitmq-host`, `rabbitmq-port`, `rabbitmq-username`,
`rabbitmq-password`) задаются конфигурацией/переменными окружения — в текущем `application.yml` их нет,
это делает деплой.

Очереди (`mp.messaging.*`, `application.yml:74-175`):

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

`smallrye.messaging.observation.enabled: true` (`application.yml:177-180`) включает наблюдаемость брокера.

## REST-клиенты и LLM

| Ключ | Значение | Эффект |
|---|---|---|
| `quarkus.rest-client.scope` | `jakarta.inject.Singleton` | один клиент на приложение |
| `quarkus.rest-client.logging.scope` | `request-response` | логирование запрос/ответ всех REST-клиентов |
| `quarkus.rest-client.vk.url` | `https://api.vk.ru/method` | база VK API (`configKey = "vk"`), в том числе для `groups.getLongPollServer` |
| `quarkus.rest-client.vane.url` | `${VANE_URL}` | база Vane (`configKey = "vane"`); обязательна |
| `quarkus.rest-client.vane.connect-timeout` | `30000` | таймаут соединения Vane |
| `quarkus.rest-client.vane.read-timeout` | `120000` | таймаут чтения Vane |
| `quarkus.langchain4j.openai.base-url` | `${AI_BASE_URL}` | корень OpenAI-совместимого API вместе с `/v1`; без него приложение не стартует |
| `quarkus.langchain4j.openai.api-key` | `${AI_API_KEY:dummy}` | ключ авторизации; дефолт `dummy` vLLM без `--api-key` игнорирует |
| `quarkus.langchain4j.openai.max-retries` | `2` | повторы после первой попытки — три попытки провайдера на вызов |
| `quarkus.langchain4j.openai.chat-model.model-name` | `${AI_CHAT_MODEL}` | имя chat-модели для ответа, саммаризации и OCR |

LLM/LangChain4j: провайдер — OpenAI-совместимый endpoint, в деплое vLLM (ADR 0007). Кроме провайдера в
production-конфиге `quarkus.langchain4j.*` заданы логирование и трейсинг (`log-requests: true`,
`log-responses: true`, `tracing.include-prompt/completion`, `application.yml:61-66`).
Ловушка миграции: `base-url` — корень OpenAI API вместе с `/v1`, расширение само дописывает
`/chat/completions`; у прежнего провайдера это был голый корень сервера. DevServices LLM больше не
поднимают. В тестах те же ключи заданы в `src/app/src/test/resources/application.yaml:22-29` и
`application-integration-test.yaml:85-93`, а HTTP-граница подменяется в `WireMockTestResource.kt:24-34`
(`base-url` = `baseUrl()/v1`).

Long Poll использует тот же `configKey = "vk"` для `groups.getLongPollServer`, а `a_check` уходит на
динамический `server` из ответа через `RestClientBuilder.baseUri`. Таймаут чтения клиента — 60 с: при
`wait=25` VK держит соединение до 25 секунд, и меньший таймаут рвал бы холостой ответ.

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
| `circuit-breaker.vk-profile` (`application.yml:182-187`) | ни одной аннотации `@CircuitBreaker`/`@Retry` в коде нет; ключ никем не читается |
| `summary.system-prompt` | не читается, см. выше |
| `summary-ready-queue` | канал объявлен, `@Incoming("summary-ready-queue")` в коде нет |

## Профили и тестовая конфигурация

- Production-профилей `%dev`/`%prod` в `src/app/src/main/resources` нет. Есть `%test` в
  `src/app/src/test/resources/application.yml:1-8` (`hibernate-orm.enabled: false`, `liquibase.enabled: false`,
  `summary.enabled-chats: 1,2,3`).
- `src/app/src/test/resources/application.yaml` — базовая тестовая конфигурация: `vk.group.id`,
  `vk.longpoll.enabled` (у `InitTest` опрос выключен — `vk.longpoll.enabled: false`), `vk.bot.*`,
  `summary.enabled-chats: 1,2,3`, DevServices вкл., observability off, `vane.url: http://localhost:8075`.
- `src/app/src/test/resources/application-integration-test.yaml` — профиль `integration-test`: RabbitMQ 3.12
  DevServices (shared, timeout 120s), PostgreSQL DevServices с `SELECT 1`, graceful shutdown, `auto-bind-exchange: false`
  для всех каналов, LangChain4j DevServices off (LLM мокается WireMock), Liquibase вкл.; опрос Bots Long Poll
  включается для `VkLongPollIntegrationTest`.
- `src/testing/test-common/src/main/resources/application.yaml` — общие значения для модульных тестов:
  `vk.group.id`, `vk.longpoll.enabled: false`, `vk.api.*`, `vk.bot.*`, DevServices off, observability off,
  `vane.url`.

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
таблицы описаны вручную в `persistence/adapter/output/persistence/jooq/Tables.kt:6-64`.

## Секреты

В репозитории нет ни одного секрета. Обязательные env для prod: `VK_GROUP_ID`, `VK_API_TOKEN`,
`VK_BOT_ID`, `VANE_URL`, `AI_BASE_URL`, `AI_CHAT_MODEL`, `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`; опциональные — `AI_API_KEY` (дефолт
`dummy`), `VK_BOT_MENTION`, `VK_LONGPOLL_ENABLED` (дефолт `true`),
`USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`, `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`.
`VK_SECRET` и `VK_CONFIRMATION_CODE` удалены вместе с Callback-эндпоинтом (ADR 0008).
Для RabbitMQ prod-параметры коннектора задаёт деплой (см. выше).
