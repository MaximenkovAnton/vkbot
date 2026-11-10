# Конфигурация

Источник истины — `src/app/src/main/resources/application.yml` и подключаемый
`src/app/src/main/resources/application-queues.yml` (`quarkus.config.locations: application-queues.yml`,
`application.yml`). Других production-конфигов в дереве нет; копии из `src/infrastructure` удалены
([ADR 0006](../adr/0006-configuration-and-secrets.md)).

Значения `${VAR}` без дефолта обязательны: без них приложение не стартует.
Секреты в репозиторий не коммитятся, `.env` в `.gitignore`.

## VK

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `vk.group.id` | `${VK_GROUP_ID}` | положительный id сообщества для `groups.getLongPollServer`; без него приложение не стартует | `receiver/adapter/output/vk/VkLongPollOutputAdapter.kt` (`group_id` запроса), `receiver/adapter/input/vk/LongPollRunner.kt`, `vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt` (сообщество итога) |
| `vk.longpoll.enabled` | `${VK_LONGPOLL_ENABLED:true}` | включает опрос Bots Long Poll при старте; `false` — цикл опроса не запускается (тестовый профиль) | `receiver/adapter/input/vk/LongPollRunner.kt` |
| `vk.api.version` | `5.199` | query-параметр `v` каждого запроса VK | `vkFacade/adapter/output/vk/VkClient.kt:13`, `receiver/adapter/output/vk/VkLongPollOutputAdapter.kt:106` (`groups.getLongPollServer`) |
| `vk.api.token` | `${VK_API_TOKEN}` | заголовок `Authorization: Bearer …` | `application.yml` |
| `vk.bot.id` | `${VK_BOT_ID}` | id автора сообщений бота: правило «forward от бота → ответить» и автор итога саммаризации | `processor/command/isRequireAnswer/IsRequireAnswerCommand.kt:17`, `vkFacade/adapter/input/event/SummaryReadyEventProcessor.kt` |
| `vk.bot.mention` | `${VK_BOT_MENTION:@simarel}` | упоминание, включающее ответ в чате | там же `:19` |

Ключей `vk.secret` и `vk.confirmation-code` больше нет: Callback-эндпоинт удалён, события приходят опросом
Bots Long Poll ([ADR 0008](../adr/0008-bots-long-poll-transport.md)).

Получение значений: сообщество VK → «Управление» → «Работа с API»: во вкладке **Long Poll API** включить
Long Poll API с событием `message_new` и отключить Callback API; `VK_GROUP_ID` — положительный id сообщества
из того же раздела; `VK_API_TOKEN` — «Ключи доступа», ключ сообщества с правом `manage` (VK не даёт вызвать
`groups.getLongPollServer` ключом без него) и правами на сообщения. Порядок переключения —
[deployment.md](deployment.md).

## Промпты и саммаризация

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `aiUseranswerSystemprompt` | `${USER_ANSWER_SYSTEM_PROMPT:You are a helpful ai chatbot in a chat.}` | system-сообщение сервиса ответов | `ai/adapter/output/ai/answer/UserAnswerAiService.kt:17` |
| `aiSumarizationSystemprompt` | `${SUMMARY_SYSTEM_PROMPT:You are a chat summarizer. …}` | system-сообщение саммаризации | `ai/adapter/output/ai/summarization/SummarizationAiService.kt:11` |
| `aiOcrSystemPrompt` | `${OCR_SYSTEM_PROMPT:Describe every image in detail …}` | system-сообщение OCR | `ai/adapter/output/ai/ocr/OcrAiService.kt:13` |
| `aiGreetingSystemprompt` | `${AI_GREETING_SYSTEM_PROMPT:Ты — дружелюбный бот сообщества VK. Поздравляй коротко, тепло и по-русски.}` | system-сообщение сервиса поздравлений | `ai/adapter/output/ai/greeting/GreetingAiService.kt` |
| `aiNewsDigestSystemprompt` | `${NEWS_DIGEST_SYSTEM_PROMPT:Ты — помощник, который кратко пересказывает, что происходило в чате. Пиши по-русски, структурировано, без выдумок.}` | system-сообщение сервиса сводки «что нового»; ключ объявлен в `application.yml`, поэтому старт не зависит от env | `ai/adapter/output/ai/news/NewsDigestAiService.kt` |
| `summary.enabled-chats` | `${SUMMARY_ENABLED_CHATS:}` | список peerId через запятую (чаты, где включена саммаризация). Ключ опционален: пустое/отсутствующее значение даёт пустой список и выключает саммаризацию, приложение стартует | `ai/command/summary/ProcessSummaryCommandImpl.kt:18-34` |
| `summary.threshold` | `${SUMMARY_THRESHOLD:100}` | минимум сообщений для саммаризации (когда прошлое саммари есть) | там же `:20,72-79` |
| `summary.batch-size` | `${SUMMARY_BATCH_SIZE:100}` | максимум сообщений в выборке (у саммаризации) и лимит свежих сообщений сводки «что нового» | `ai/command/summary/ProcessSummaryCommandImpl.kt:22,108-125`; `ai/command/news/ProcessNewsDigestCommandImpl.kt` |
| `summary.system-prompt` | `${SUMMARY_SYSTEM_PROMPT:…}` | **никем не читается** — мёртвый дубль `aiSumarizationSystemprompt` (`application.yml`) | — |

`summary.enabled-chats` биндится как `String` с `defaultValue = ""` (`ProcessSummaryCommandImpl.kt:18`),
поэтому пустая строка конвертером не отвергается: приложение стартует, `split(",")` даёт пустой набор
чатов и саммаризация выключается. `SRCFG00040` на этом ключе не возникает — он появляется только у
конвертеров, считающих пустую строку `null` (числа, списки).

## Поздравления

| Ключ | Значение / env | Эффект | Где читается |
|---|---|---|---|
| `greetings.cron` | `${GREETINGS_CRON:0 0 0 * * ?}` | cron-расписание прогона в зоне `greetings.timezone`; дефолт — 00:00; `off`/`disabled` выключает расписание (тестовые профили) | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/adapter/input/schedule/DailyGreetingRunner.kt` (`@Scheduled`) |
| `greetings.timezone` | `${GREETINGS_TIMEZONE:Europe/Minsk}` | зона cron-расписания; невалидная зона валит старт при создании бина (`ZoneId.of`), тихого пропуска нет | там же |

Дефолт `GREETINGS_CRON=0 0 0 * * ?` означает, что первый прогон после деплоя — живой; в тестовых
ресурсах `greetings.cron: disabled`, иначе планировщик срабатывал бы в каждом `@QuarkusTest`.

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

`application.yml` задаёт только DevServices (`quarkus.rabbitmq.devservices.image-name: rabbitmq:latest`);
в prod параметры коннектора SmallRye RabbitMQ (`rabbitmq-host`, `rabbitmq-port`, `rabbitmq-username`,
`rabbitmq-password`) задаются конфигурацией/переменными окружения — в текущем `application.yml` их нет,
это делает деплой.

Очереди (`mp.messaging.*`, `application.yml:92-186`):

| Канал | Тип | Exchange / queue | Особенности |
|---|---|---|---|
| `all-events-queue` | incoming | `all-events` / `queue` | DLX `retry-5s-exchange` |
| `retry-5s-queue` … `retry-1h-queue` | incoming | `retry-*` / `retry-*` | TTL 5s/30s/1m/15m/1h, DLX обратно в `all-events` |
| `dlq-final-queue` | incoming | `dlq-final` | без TTL, ручной разбор |
| `events-exchange` | outgoing | `all-events` | публикация событий, routing key = имя события |
| `retry-*-out`, `dlq-final-out` | outgoing | соответствующие exchange | перекладывание при retry |

`dlq.levels[]` (`application-queues.yml:1-32`) связывает уровень retry с очередью и TTL; читается
`infrastructure/config/DlqConfig.kt:6-16` и используется `EventDispatcher.kt:25-26,90-111`.

`smallrye.messaging.observation.enabled: true` (`application.yml:188-191`) включает наблюдаемость брокера.

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
| `quarkus.langchain4j.chat-model.provider` | `openai` | build-time выбор провайдера chat-модели; с двумя LangChain4j-расширениями обязателен, иначе сборка падает на ambiguity |
| `quarkus.langchain4j.decision-model.provider` | `ollama` | build-time выбор провайдера decision-модели; второй обязательный ключ выбора |
| `quarkus.langchain4j.ollama.base-url` | `${DECISION_URL}` | голый корень System One-сервера (без `/v1`, без `/v1/systemone`); `DECISION_URL` обязателен, дефолта нет; расширение дописывает `v1/systemone` |
| `quarkus.langchain4j.ollama.timeout` | `5s` | таймаут HTTP-вызова decision-модели (дефолт расширения — `10s`) |
| `quarkus.langchain4j.ollama.decision-model.model-id` | `decision` | `model-id: decision` — имя модели в теле запроса; дефолт расширения `nimble`, сервер поле `model` игнорирует |
| `decision.spam-threshold` | `${DECISION_SPAM_THRESHOLD:0.9}` | порог спама: `spamProbability >= threshold` даёт спам-уведомление; читает `IsSpamCommandImpl` |
| `decision.spam-reply.enabled` | `${DECISION_SPAM_REPLY_ENABLED:true}` | kill-switch спам-этапа: `false` выключает вызов decision-модели спам-этапа и спам-ответ; интент-вызов после `requiredAnswer=true` не гаснет и отправляет нормализованный текст в System One (PII, ADR 0010/0012); читает `MessageReceivedUsecaseInput` |

LLM/LangChain4j: провайдер — OpenAI-совместимый endpoint, в деплое vLLM (ADR 0007). Кроме провайдера в
production-конфиге `quarkus.langchain4j.*` заданы логирование и трейсинг (`log-requests: true`,
`log-responses: true`, `tracing.include-prompt/completion`, `application.yml`).
Ловушка миграции: `base-url` — корень OpenAI API вместе с `/v1`, расширение само дописывает
`/chat/completions`; у прежнего провайдера это был голый корень сервера. DevServices LLM больше не
поднимают. В тестах те же ключи заданы в `src/app/src/test/resources/application.yaml:26-33` и
`src/app/src/test/resources/application-integration-test.yaml:89-97`, а HTTP-граница подменяется в `WireMockTestResource.kt:24-34`
(`base-url` = `baseUrl()/v1`).

Decision-модель ([ADR 0010](../adr/0010-decision-model-langchain4j.md),
[ADR 0012](../adr/0012-decision-runtime-spam-policy.md)) — второй LangChain4j-провайдер:
`decision-model.provider: ollama`, `DECISION_URL` — голый корень System One-сервера, `timeout: 5s`,
`model-id: decision`. Расширение само дописывает `v1/systemone`; `api-key` не нужен. Клиент вызывается
из runtime в `MessageReceivedUsecaseInput` в два этапа. Спам-этап: для сообщения человека в групповом
чате при `decision.spam-reply.enabled = true` — три вопроса (`spam`, `class`, `intent`), при
`spamProbability >= decision.spam-threshold` бот отвечает обычным сообщением-уведомлением. Интент-этап:
после `requiredAnswer=true`, если анализа ещё нет (личный чат, `decision.spam-reply.enabled = false`),
— ровно один вызов с теми же тремя вопросами (после fail-open спам-этапа — ровно одна дополнительная
попытка, всего два вызова); нормализованный текст уходит в System One, kill-switch его не гасит.
Недоступность
decision-сервера в runtime даёт fail-open: WARNING без текста сообщения и без throwable (только `peerId`
и класс исключения), сообщение обрабатывается как раньше, исключение в брокер не уходит.
Провайдерские ключи `quarkus.langchain4j.ollama.log-requests/log-responses` **не добавлены**.
Decision-логи **включены** наследованием глобального `true`: у ollama-конфига дефолт
`@WithDefault("${quarkus.langchain4j.log-requests}")`/`...log-responses`, а рекордер читает
`firstOrDefault(false, decision-model.*, ollama.*)`; поэтому нормализованный текст и ответ модели
логируются на INFO и **экспортируются по OTLP** (`quarkus.otel.logs.enabled: true` — PII покидает
процесс) — **PII, принятый риск** (решение Q1=B). Выключение — отдельное решение
(провайдерские `log-requests: false` + `log-responses: false`). `cause.message` (тело HTTP-ошибки) не
логировать выше DEBUG. В тестах заданы dummy `base-url: http://localhost:8089` и
`model-id: test-decision`, а `WireMockTestResource` подменяет `base-url` на `baseUrl()`.
`DECISION_URL` — обязательное значение контракта деплоя; поведение старта без переменной этой задачей
**не проверялось**, поэтому здесь не утверждается, что старт без неё проходит (named gap ADR 0010).
Деплой выставляет переменную вручную, первый деплой проверяет позитивную половину (переменная задана →
ключ резолвится); наблюдение M-1 — `impl.md` board-task `decision-integration`.

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
| `circuit-breaker.vk-profile` (`application.yml:193-198`) | ни одной аннотации `@CircuitBreaker`/`@Retry` в коде нет; ключ никем не читается |
| `summary.system-prompt` | не читается, см. выше |

## Профили и тестовая конфигурация

- Production-профилей `%dev`/`%prod` в `src/app/src/main/resources` нет. Есть `%test` в
  `src/app/src/test/resources/application.yml` (`hibernate-orm.enabled: false`, `liquibase.enabled: false`,
  `greetings.cron: disabled`, `summary.enabled-chats: 1,2,3`).
- `src/app/src/test/resources/application.yaml` — базовая тестовая конфигурация: `vk.group.id`,
  `vk.longpoll.enabled` (у `InitTest` опрос выключен — `vk.longpoll.enabled: false`), `vk.bot.*`,
  `summary.enabled-chats: 1,2,3`, `greetings.cron: disabled`, DevServices вкл., observability off,
  `vane.url: http://localhost:8075`.
- `src/app/src/test/resources/application-integration-test.yaml` — профиль `integration-test`: RabbitMQ 3.12
  DevServices (shared, timeout 120s), PostgreSQL DevServices с `SELECT 1`, graceful shutdown, `auto-bind-exchange: false`
  для всех каналов, LangChain4j DevServices off (LLM мокается WireMock), Liquibase вкл.; опрос Bots Long Poll
  включается для `VkLongPollIntegrationTest`; `greetings.cron: disabled` — расписание в тестах не срабатывает.
- `src/testing/test-common/src/main/resources/application.yaml` — общие значения для модульных тестов:
  `vk.group.id`, `vk.longpoll.enabled: false`, `vk.api.*`, `vk.bot.*`, DevServices off, observability off,
  `vane.url`.

## Health

Расширение `quarkus-smallrye-health` подключено (`src/app/build.gradle.kts:37`), кастомных эндпоинтов нет —
действуют умолчания Quarkus: `/q/health`, `/q/health/ready`, `/q/health/live`. Путей `/health` и `/metrics` нет.
Messaging-проверка SmallRye Reactive Messaging исключает шесть retry/DLQ-каналов ключом
`quarkus.messaging.health.<channel>.enabled: false` (`retry-5s-queue`, `retry-30s-queue`, `retry-1m-queue`,
`retry-15m-queue`, `retry-1h-queue`, `dlq-final-queue`; `application.yml:77-90`): они объявлены ради TTL/DLX
и downstream-консьюмера не имеют, без исключения проверка помечает их `[KO]` (`SRMSG00208: has no
downstreams`) и эндпоинты отдают 503 DOWN. `all-events-queue` остаётся под проверкой — у него есть консьюмер
`EventDispatcher.dispatch`. `HealthTest` проверяет 200 UP на всех трёх эндпоинтах; подробности —
`deployment.md`, раздел «Health и наблюдаемость».

## jOOQ codegen (выключен)

`./gradlew :src:persistence:generateJooq` существует, но выполняется только при заданном `JOOQ_JDBC_URL`
(`src/persistence/build.gradle.kts:89-125`, `onlyIf { System.getenv("JOOQ_JDBC_URL") != null }`).
Переменные: `JOOQ_JDBC_URL`, `JOOQ_JDBC_USER`, `JOOQ_JDBC_PASSWORD`. Генерация не нужна для сборки:
таблицы описаны вручную в `persistence/adapter/output/persistence/jooq/Tables.kt:6-72`.

## Секреты

В репозитории нет ни одного секрета. Обязательные env для prod: `VK_GROUP_ID`, `VK_API_TOKEN`,
`VK_BOT_ID`, `VANE_URL`, `AI_BASE_URL`, `AI_CHAT_MODEL`, `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`; опциональные — `AI_API_KEY` (дефолт
`dummy`), `VK_BOT_MENTION`, `VK_LONGPOLL_ENABLED` (дефолт `true`),
`USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`, `AI_GREETING_SYSTEM_PROMPT`,
`NEWS_DIGEST_SYSTEM_PROMPT`, `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`, `GREETINGS_CRON` (дефолт
`0 0 0 * * ?`; `off`/`disabled` выключает расписание), `GREETINGS_TIMEZONE` (дефолт `Europe/Minsk`).
`VK_SECRET` и `VK_CONFIRMATION_CODE` удалены вместе с Callback-эндпоинтом (ADR 0008).
Для RabbitMQ prod-параметры коннектора задаёт деплой (см. выше).
