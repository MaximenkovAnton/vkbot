# Деплой и развёртывание

Один деплой — `:src:app` ([ADR 2026-10-28-single-deployable-app-module](../adr/2026-10-28-single-deployable-app-module.md)). Документ описывает
артефакт, образы, обязательные переменные окружения, переключение сообщества на Bots Long Poll, миграции и
откат; всё, что не проверялось прогоном, помечено явно.

## Артефакт

```bash
./gradlew clean build
# -> src/app/build/quarkus-app/quarkus-run.jar
test -f src/app/build/quarkus-app/quarkus-run.jar   # smoke, входит в CI job build
test ! -e build/quarkus-app                          # второго раннера нет
```

`./gradlew build` собирает все 12 модулей, прогоняет тесты, detekt, ktlintCheck и порог покрытия и
собирает Quarkus-артефакт (`:src:app:quarkusBuild` входит в `build`). Проверено в этом проходе на
замороженном дереве: `./gradlew build` → `BUILD SUCCESSFUL in 1m 43s`, 180 задач (включая `capabilitiesCheck`),
артефакт на месте, корневого `build/quarkus-app` нет.

Структура `quarkus-app`: `app/`, `lib/`, `quarkus/`, `quarkus-run.jar`, `quarkus-app-dependencies.txt`.

## JVM-образ (подтверждённый путь)

[`src/docker/Dockerfile.jvm`](../../src/docker/Dockerfile.jvm):

- база `registry.access.redhat.com/ubi9/openjdk-21:1.23` (`:81`);
- копирует **[`src/app/build/quarkus-app`](../../src/app/build/quarkus-app)** (`:87-90`), не корневой `build`;
- `EXPOSE 8080` (`:92`), `USER 185` (`:93`);
- `JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"`
  (`:94`), `JAVA_APP_JAR=/deployments/quarkus-run.jar`, entrypoint `run-java.sh` (`:95-97`).

`.dockerignore` пропускает `src/app/build/quarkus-app/*`, поэтому этот образ собирается из текущего дерева.
Сборка образа в этом проходе **не выполнялась** (нет подтверждения `docker build`), но Dockerfile и
источник артефакта согласованы. Команды:

```bash
./gradlew clean build
docker build -f src/docker/Dockerfile.jvm -t vkbot:jvm .
docker run -p 8080:8080 \
  -e VK_GROUP_ID=... -e VK_API_TOKEN=... -e VK_BOT_ID=... \
  -e VANE_URL=... -e AI_BASE_URL=... -e AI_CHAT_MODEL=... -e SUMMARY_ENABLED_CHATS=1,2,3 \
  -e DECISION_URL=... \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/vkbot \
  -e QUARKUS_DATASOURCE_USERNAME=... -e QUARKUS_DATASOURCE_PASSWORD=... \
  -e RABBITMQ_HOST=rabbitmq \
  vkbot:jvm
```

## Native и legacy-jar

- [`Dockerfile.native`](../../src/docker/Dockerfile.native) и [`Dockerfile.native-micro`](../../src/docker/Dockerfile.native-micro) копируют `src/app/build/*-runner`
  ([`Dockerfile.native:24`](../../src/docker/Dockerfile.native#L24), [`Dockerfile.native-micro:27`](../../src/docker/Dockerfile.native-micro#L27)); [`Dockerfile.legacy-jar`](../../src/docker/Dockerfile.legacy-jar) — `src/app/build/lib/*` и
  `src/app/build/*-runner.jar` (`:86-87`).
- **Ограничение:** `.dockerignore` сейчас пропускает только `src/app/build/quarkus-app/*` (и старые
  `build/*`), поэтому native/legacy-образы не получат файлы без расширения белого списка. Перед сборкой
  добавить `!src/app/build/*-runner` и `!src/app/build/lib/*` в `.dockerignore`.
- Native-сборка (`./gradlew build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false`)
  требует GraalVM/нативного тулчейна и в этом проходе не выполнялась; в CI не входит (ADR 2026-10-28-quality-gates-and-ci).
  Legacy-jar требует отдельного packaging-типа и тоже не проверялся.

## Переменные окружения

Обязательные в production (без них приложение не стартует):

| Переменная | Назначение |
|---|---|
| `VK_GROUP_ID` | положительный id сообщества; `groups.getLongPollServer` без него не собрать |
| `VK_API_TOKEN` | токен группы VK |
| `VK_BOT_ID` | id бота для правила ответа |
| `VANE_URL` | база Vane (search API) |
| `AI_BASE_URL` | корень OpenAI-совместимого API LLM вместе с `/v1` (в деплое — llama.cpp, `llama-server`) |
| `AI_CHAT_MODEL` | имя chat-модели; одна модель обслуживает ответ, саммари, OCR, поздравления и «что нового» |
| `QUARKUS_DATASOURCE_JDBC_URL` | JDBC URL PostgreSQL |
| `QUARKUS_DATASOURCE_USERNAME` / `QUARKUS_DATASOURCE_PASSWORD` | доступ к БД |
| `RABBITMQ_HOST` (и при необходимости `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`) | коннектор SmallRye RabbitMQ |

`DECISION_URL` — обязательное значение контракта деплоя и новый поток данных в runtime: decision-модель
вызывается из `MessageReceivedUsecaseInput` для сообщения человека в групповом чате. Поведение старта без
переменной этой задачей **не проверялось** (named gap ADR 2026-11-08-decision-model-langchain4j), поэтому здесь не утверждается, что
старт без неё проходит:

| Переменная | Назначение |
|---|---|
| `DECISION_URL` | голый корень System One-сервера `llama-server` (без `/v1` и без `/v1/systemone`) |

Опциональные: `AI_API_KEY` (дефолт `dummy`; нужен при включённой на сервере авторизации),
`VK_LONGPOLL_ENABLED` (дефолт `true`; `false` выключает опрос), `VK_BOT_MENTION`,
`SUMMARY_ENABLED_CHATS` (список peerId через запятую; пусто — саммаризация
выключена), `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`,
`AI_GREETING_SYSTEM_PROMPT`, `NEWS_DIGEST_SYSTEM_PROMPT` (system-промпт сводки «что нового»; дефолт
в [`application.yml`](../../src/app/src/main/resources/application.yml)), `AI_PROFILE_SYSTEM_PROMPT` (system-промпт досье об участнике чата; дефолт в
[`application.yml`](../../src/app/src/main/resources/application.yml), новых обязательных env нет), `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`,
`GREETINGS_CRON` (дефолт `0 0 0 * * ?`, то есть 00:00; `off`/`disabled` выключает расписание),
`GREETINGS_TIMEZONE` (дефолт `Europe/Minsk`),
`DECISION_SPAM_THRESHOLD` (дефолт `0.9` — порог спама),
`DECISION_SPAM_REPLY_ENABLED` (дефолт `true`; `false` гасит только спам-ветку — вызов decision-модели и
PII-egress при включённой jailbreak-ветке продолжаются),
`DECISION_JAILBREAK_THRESHOLD` (дефолт `0.85` — порог попытки взлома ИИ),
`DECISION_JAILBREAK_REPLY_ENABLED` (дефолт `true`; `false` гасит только jailbreak-ветку).

`GREETINGS_CRON=0 0 0 * * ?` по умолчанию: первый прогон после деплоя — живой, в ближайшие 00:00 зоны
`GREETINGS_TIMEZONE`. Гасят его at-most-once (`greeting_log`), один прогон в сутки и отсутствие
ручного триггера; на время приёмки выставляйте `GREETINGS_CRON=off` (или `disabled`).

`VK_SECRET` и `VK_CONFIRMATION_CODE` из контракта деплоя удалены: Callback-эндпоинта нет, приём идёт
опросом Bots Long Poll ([ADR 2026-11-05-bots-long-poll-transport](../adr/2026-11-05-bots-long-poll-transport.md)).

Замечания:

- `DECISION_URL` — обязательное значение контракта деплоя: `quarkus.langchain4j.ollama.base-url:
  ${DECISION_URL}` (голый корень System One-сервера `llama-server`, без `/v1`). Порядок раскатки — **сначала env и
  decision-сервер, принимающий и отвечающий на четыре вопроса `spam`, `class`, `intent`, `jailbreak`,
  затем образ**. Старый образ (`DECISION_URL` он читает и шлёт туда трёхвопросный запрос) в окне между
  выкатами работает с новым сервером, поэтому новый сервер обязан принимать и старый трёхвопросный
  запрос; оператор проверяет это до переключения образа. Если сервер строгий и трёхвопросный запрос
  отвергает, каждый применимый вызов старого образа уходит в fail-open: WARNING без PII (только `peerId`
  и класс исключения), спам-детект и `intent` недоступны до конца выката образа, ответы работают как
  раньше. Обратный случай — новый образ со старым трёхвопросным сервером: каждый применимый вызов тоже
  fail-open, jailbreak- и спам-детект недоступны. Переменную выставляет деплой **вручную**; поведение
  старта без переменной этой задачей не проверялось, на отказ старта рассчитывать нельзя
  ([ADR 2026-11-08-decision-model-langchain4j](../adr/2026-11-08-decision-model-langchain4j.md),
  [ADR 2026-11-09-decision-runtime-spam-policy](../adr/2026-11-09-decision-runtime-spam-policy.md),
  [ADR 2026-11-10-decision-jailbreak-detection](../adr/2026-11-10-decision-jailbreak-detection.md),
  [ADR 2026-11-10-jailbreak-rubric-and-threshold](../adr/2026-11-10-jailbreak-rubric-and-threshold.md)). Операционный откат без отката образа:
  `DECISION_JAILBREAK_REPLY_ENABLED=false` гасит только jailbreak-ветку; вызов decision-модели и
  PII-egress останавливают только оба ключа `false` — `DECISION_SPAM_REPLY_ENABLED=false` и
  `DECISION_JAILBREAK_REPLY_ENABLED=false`; двухключевой стоп гасит только decision-этап
  (спам/jailbreak), а ленивый интент-вызов после `requiredAnswer=true` остаётся; предыдущий образ —
  полный откат.
- В [`application.yml`](../../src/app/src/main/resources/application.yml) prod-ключи RabbitMQ не заданы (только DevServices) — их должен задать деплой:
  `rabbitmq-host`/`rabbitmq-port`/… для коннектора. Ключи LLM в [`application.yml`](../../src/app/src/main/resources/application.yml) есть, но читают env:
  `AI_BASE_URL` — это корень OpenAI API вместе с `/v1` (расширение дописывает `/chat/completions`),
  `AI_CHAT_MODEL` — имя модели (ADR 2026-11-05-llm-provider-vllm-openai).
- `RABBITMQ_HOST` — стандартное отображение MicroProfile Config на `rabbitmq-host`; запуском prod-конфигурации
  в этом проходе не проверялось.
- Секреты храните в секрет-менеджере платформы; в репозиторий и образ они не попадают.

Полная таблица ключей и значений по умолчанию — [configuration.md](configuration.md).

## Переключение сообщества на Bots Long Poll

Порядок фиксирован: настройки сообщества меняет человек, образ выкатывается последним.

1. VK → «Управление» → «API» → Bots Long Poll API: включить Long Poll API и событие `message_new`.
   Callback API пока остаётся включённым — старый образ продолжает принимать Callback-события, и приём не
   прерывается.
2. Отключить Callback API в том же разделе.
3. Выкатить образ без `VK_SECRET`/`VK_CONFIRMATION_CODE` и с `VK_GROUP_ID`
   (`VK_LONGPOLL_ENABLED` можно не задавать — дефолт `true`).

Окно между шагами 2 и 3 держите минимальным: пока новый образ не запущен, опрос не идёт. После старта
приложение само запрашивает `groups.getLongPollServer`; сохранённой позиции при первом переключении нет,
поэтому опрос начинается с `ts` из ответа VK. Дальше `ts` переживает рестарты в таблице
`long_poll_position`.

Требование: **один экземпляр приложения**. Токен и позиция `ts` одни на сообщество; два экземпляра с
одним токеном и одной позицией не поддерживаются — они делили бы `ts` и теряли события. По той же
причине rolling update с двумя одновременно живыми экземплярами не подходит: сначала останавливается
старый, затем поднимается новый (стратегия `Recreate`).

Откат: вернуть предыдущий образ, снова включить Callback API и передать прежние `VK_SECRET` и
`VK_CONFIRMATION_CODE`; сохранённый `ts` остаётся в таблице и откату не мешает.

## Проверка токена и Long Poll перед выкаткой

Две проверки до старта образа — они отделяют отказ доступа от ошибки приложения:

```bash
curl -s "https://api.vk.com/method/groups.getById?access_token=$VK_API_TOKEN&v=5.199"
curl -s "https://api.vk.com/method/groups.getLongPollSettings?group_id=$VK_GROUP_ID&access_token=$VK_API_TOKEN&v=5.199"
```

Ожидаемо: первая команда возвращает сообщество с `id` = `VK_GROUP_ID` (токен принадлежит этому сообществу),
вторая — `"is_enabled":true` и `message_new` в `events`. Если вместо этого приходит `{"error":{…}}`,
приложение стартует, но опрос не пойдёт: `groups.getLongPollServer` вернёт тот же отказ, а в ERROR уйдёт
`VK long poll request failed (VkLongPollServerException): groups.getLongPollServer failed: code 15 subcode
1133 message …` с паузой до 60 с.

| Код | Что значит | Что делать |
|---|---|---|
| `15` | «Access denied… cannot be called with current scopes»: у ключа сообщества нет права `manage` — VK требует его для `groups.getLongPollServer`; реже — ключ от другого сообщества или Long Poll API в сообществе выключен | пересоздать ключ доступа сообщества с правом `manage` (и правами на сообщения), сверить `id` из первой команды, включить Long Poll API |
| `27` | ключ группы недействителен или выпущен для другого сообщества | выпустить ключ заново для `VK_GROUP_ID` |
| `100` | неверный `group_id` | `VK_GROUP_ID` — положительный id сообщества, не id пользователя и не id приложения |

## Миграции и откат

- Схема принадлежит `:src:persistence`; master-чейнджлог
  [`src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml`](../../src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml) включает changesets
  `001-create-messages-table`, `002-create-user-profiles-table`, `003-create-group-profiles-table`,
  `004-create-summaries-table`, `005-create-long-poll-position-table`, `006-create-greeting-log-table`,
  `007-add-user-profile-data-and-history`, `008-replace-profile-data-with-human-details`,
  `009-create-user-statistics-tables`, `010-create-user-knowledge-tables` (пять таблиц знаний: `user_facts`,
  `user_fact_evidence`, `user_knowledge_summary`, `chat_knowledge_summary`, `knowledge_extraction_cursors`
  и второй нетранзакционный changeset `010-create-messages-knowledge-index`).
- Миграции применяются на старте (`quarkus.liquibase.migrate-at-start: true`,
  [`application.yml`](../../src/app/src/main/resources/application.yml)). Liquibase-ключей в тестах — `enabled: false` в `%test`.
- Правило: **применённый changeset не редактируется**; следующий номер — 011.
- Таблицы знаний `010` аддитивны: предыдущий образ их не читает и не пишет, откат образа на один релиз
  назад проходит без изменения схемы. Знания после начала записи откатываются только из бэкапа БД
  (Liquibase forward-only).
- **Changeset `010-create-messages-knowledge-index` — нетранзакционный** (`runInTransaction: false`):
  `DROP INDEX CONCURRENTLY IF EXISTS idx_messages_peer_id_conversation_message_id` + `CREATE INDEX
  CONCURRENTLY idx_messages_peer_id_conversation_message_id ON messages (peer_id, conversation_message_id)`
  не выполняются внутри транзакции и идут при старте приложения. Таймауты в changeset не выставляются:
  `SET LOCAL` в нетранзакционном changeset невозможен, а сессионные `SET lock_timeout`/`SET
  statement_timeout` проект не выставляет — действуют настройки соединения и БД. Прерванная сборка
  (рестарт, отмена, разрыв соединения) может оставить индекс недействительным: диагностика
  `select c.relname, i.indisvalid, i.indisready from pg_class c join pg_index i on i.indexrelid = c.oid
  where c.relname = 'idx_messages_peer_id_conversation_message_id';` — `indisvalid=f`/`indisready=f`
  означает незавершённую сборку; планировщик такой индекс игнорирует (`EXPLAIN` — seq scan), а повторный
  `CREATE INDEX ... IF NOT EXISTS` его не пересобирает. Восстановление вручную:
  `drop index concurrently if exists idx_messages_peer_id_conversation_message_id;`, затем повторить
  changeset (перезапуск приложения). Пара drop+create идемпотентна, данные не затрагиваются.
- Rollback-команд Liquibase в проекте нет: откат приложения — предыдущий образ, схема обязана быть
  совместима на один релиз назад (аддитивные изменения). Откат данных — из бэкапа БД.
- **Исключение — changeset `008-replace-profile-data-with-human-details`.** Он удаляет колонку
  `vk_user_profiles.profile_data` (и очищает старую историю), а предыдущий образ её читает и пишет
  ([`VkUserProfileTable.kt:41-63`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/VkUserProfileTable.kt#L41-L63)) и падает на `findByIds`/`persist`
  ([`FetchUserProfileCommandImpl.kt:36`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt#L36)). Поэтому 008 применяется только вместе с новым образом; откат
  образа после 008 — только с восстановлением БД из бэкапа до 008, иначе сохранение сообщений ломается.
- **Backfill статистики — ручной ops-шаг, не миграция.** Changeset 009 создаёт только пустые таблицы;
  накопленные `messages` переносятся скриптом [`backfill-user-statistics.sql`](../../src/persistence/src/main/resources/db/backfill/backfill-user-statistics.sql)
  ([`src/persistence/src/main/resources/db/backfill/`](../../src/persistence/src/main/resources/db/backfill)). Скрипт не владеет транзакцией и требует
  остановленного писателя, поэтому порядок такой: **остановить приложение** (единственный писатель
  агрегатов; при живой аппликации транзакции сохранения сообщений встают на upsert'ах до конца
  пересборки) → из каталога скрипта выполнить
  `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f backfill-user-statistics.sql` →
  проверить `sum(message_count)` в `user_statistics` (и суммы по чатам/часам) против `count(*)` в
  `messages` → запустить приложение. Скрипт берёт `LOCK TABLE user_statistics, user_chat_statistics,
  user_hour_statistics IN ACCESS EXCLUSIVE MODE` с `SET LOCAL lock_timeout = '2s'`: при чужом писателе
  (или втором запуске) он падает fail fast примерно за 2 секунды, не меняя строк, а сбой в середине
  откатывает весь файл. Повторный запуск идемпотентен (`DELETE` + пересборка в одной транзакции); после
  пересборки скрипт выполняет `ANALYZE` на трёх таблицах, отдельная команда не нужна.
- **Окно дрейфа.** Между откатом на предыдущий образ (он пишет `messages`, но не агрегаты) и возвратом
  новой версии агрегаты расходятся с журналом: сообщения окна в статистику не попадут. Это named gap;
  согласованность восстанавливает тот же ручной пересчёт [`backfill-user-statistics.sql`](../../src/persistence/src/main/resources/db/backfill/backfill-user-statistics.sql) (остановка
  писателя + `--single-transaction` + `ON_ERROR_STOP`); автоматического заполнения нет.
- Порядок деплоя: сначала применить совместимую миграцию (новый образ при старте), затем переключить
  трафик; при откате — вернуть предыдущий образ. Для decision-клиента данных и миграций
  нет: откат — предыдущий образ; `DECISION_URL` остаётся в деплое, её читает и предыдущий образ
  (`${DECISION_URL}` без дефолта, [`application.yml:115`](../../src/app/src/main/resources/application.yml#L115)); повторная раскатка — тем же порядком
  «env → образ».
- **«Профиль» (`profile-dossier`) — always-on без kill-switch.** Схема и данные не меняются (новых
  changeset нет), новых обязательных env нет: `AI_PROFILE_SYSTEM_PROMPT` опционален, дефолт в
  [`application.yml`](../../src/app/src/main/resources/application.yml). Откат — предыдущий образ; откат возвращает потерю запросов «профиль» (unknown
  routing key → ERROR с payload), данные и схема при откате не страдают.

## Health и наблюдаемость

- Health-эндпоинты: `/q/health`, `/q/health/ready`, `/q/health/live` (`quarkus-smallrye-health`) отдают
  **200 UP** и годятся в readiness-пробу Kubernetes. Шесть retry/DLQ-каналов (`retry-5s-queue` …
  `retry-1h-queue`, `dlq-final-queue`) объявлены ради TTL/DLX и downstream-консьюмера не имеют, поэтому
  исключены из проверки SmallRye Reactive Messaging ключом `quarkus.messaging.health.<channel>.enabled:
  false` ([`application.yml:119-132`](../../src/app/src/main/resources/application.yml#L119-L132)); иначе она помечает их `[KO]` (`SRMSG00208: has no downstreams`) и health
  отдаёт 503 DOWN. Под проверкой остаётся `all-events-queue` — единственный канал с консьюмером
  (`EventDispatcher.dispatch`). Health не связан с приёмом событий: опрос Bots Long Poll идёт своим циклом
  и от HTTP-проб не зависит.
- OpenTelemetry: экспорт логов/метрик/трейсов по OTLP (по умолчанию `http://localhost:4317`);
  эндпоинт коллектора задаётся стандартными ключами OTel. Prometheus-scrape и `/metrics` отсутствуют.
- Логи — JSON-формат (`quarkus-logging-json`) в собранном деплое.

## CI

[`.github/workflows/build-and-test.yml`](../../.github/workflows/build-and-test.yml) (push/pull_request в `main`, JDK 21 temurin):

| Job | Что проверяет |
|---|---|
| `build` | `./gradlew :src:app:quarkusBuild`, наличие [`src/app/build/quarkus-app/quarkus-run.jar`](../../src/app/build/quarkus-app/quarkus-run.jar), отсутствие `build/quarkus-app` |
| `lint` | `./gradlew detekt ktlintCheck` |
| `coverage` | `./gradlew test coverageReport coverageVerification` — единственный прогон тестов в CI (нужен Docker для DevServices), upload HTML-отчёта; на push в `main` — бейджи покрытия и тестов коммитятся в [`.github/badges/`](../../.github/badges) |
| `docs` | [`./scripts/check-capabilities.sh`](../../scripts/check-capabilities.sh) (карта возможностей существует, чек зелёный) |

Бейджи лежат в [`.github/badges/`](../../.github/badges) рядом с кодом, отдельной ветки нет: на push в `main`
джоб `coverage` перегенерирует SVG и коммитит их бот-коммитом с `[skip ci]`, поэтому удалить бейджи вместе с
веткой нельзя. Это единственный шаг CI, который пишет в репозиторий: джобу `coverage` нужен `contents: write`
(задано в workflow), а репозиторий должен разрешать GitHub Actions писать
(`Settings → Actions → General → Workflow permissions`).

## Локальный запуск

```bash
export VK_GROUP_ID=... VK_API_TOKEN=... VK_BOT_ID=... VANE_URL=http://localhost:3000 \
       AI_BASE_URL=http://localhost:8000/v1 AI_CHAT_MODEL=... \
       SUMMARY_ENABLED_CHATS=1,2,3
./gradlew :src:app:quarkusDev
```

DevServices поднимают PostgreSQL и RabbitMQ (нужен Docker); LLM — внешний OpenAI-совместимый endpoint из
`AI_BASE_URL` (DevServices его больше не поднимают). В dev доступны dev-UI и hot reload. Публичного
HTTP-входа у приложения нет: `/vk/callback` отвечает 404, события приходят опросом Bots Long Poll —
приложение само обращается к VK (`VK_GROUP_ID` и `VK_API_TOKEN`). `SUMMARY_ENABLED_CHATS` опционален: без
него (или с пустым значением) приложение стартует с выключенной саммаризацией ([`application.yml`](../../src/app/src/main/resources/application.yml)); в
тестовых профилях задан `1,2,3`.

## Что не проверено в этом проходе

- Сборка Docker-образов (JVM/native/legacy) — Dockerfile'ы прочитаны, `docker build` не запускался.
- Production-конфигурация RabbitMQ — ключей в [`application.yml`](../../src/app/src/main/resources/application.yml) нет, деплой задаёт их сам; ключи LLM в
  конфиге есть, но их значения приходят из env `AI_BASE_URL`/`AI_CHAT_MODEL`/`AI_API_KEY`.
- Native-сборка и её образ.
