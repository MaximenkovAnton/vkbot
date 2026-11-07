# Деплой и развёртывание

Один деплой — `:src:app` ([ADR 0002](../adr/0002-single-deployable-app-module.md)). Документ описывает
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
собирает Quarkus-артефакт (`:src:app:quarkusBuild` входит в `build`). Проверено в этом проходе:
`BUILD SUCCESSFUL in 2m 4s`, 180 задач, артефакт на месте, корневого `build/quarkus-app` нет.

Структура `quarkus-app`: `app/`, `lib/`, `quarkus/`, `quarkus-run.jar`, `quarkus-app-dependencies.txt`.

## JVM-образ (подтверждённый путь)

`src/docker/Dockerfile.jvm`:

- база `registry.access.redhat.com/ubi9/openjdk-21:1.23` (`:81`);
- копирует **`src/app/build/quarkus-app`** (`:87-90`), не корневой `build`;
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
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/vkbot \
  -e QUARKUS_DATASOURCE_USERNAME=... -e QUARKUS_DATASOURCE_PASSWORD=... \
  -e RABBITMQ_HOST=rabbitmq \
  vkbot:jvm
```

## Native и legacy-jar

- `Dockerfile.native` и `Dockerfile.native-micro` копируют `src/app/build/*-runner`
  (`Dockerfile.native:24`, `Dockerfile.native-micro:27`); `Dockerfile.legacy-jar` — `src/app/build/lib/*` и
  `src/app/build/*-runner.jar` (`:86-87`).
- **Ограничение:** `.dockerignore` сейчас пропускает только `src/app/build/quarkus-app/*` (и старые
  `build/*`), поэтому native/legacy-образы не получат файлы без расширения белого списка. Перед сборкой
  добавить `!src/app/build/*-runner` и `!src/app/build/lib/*` в `.dockerignore`.
- Native-сборка (`./gradlew build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false`)
  требует GraalVM/нативного тулчейна и в этом проходе не выполнялась; в CI не входит (ADR 0005).
  Legacy-jar требует отдельного packaging-типа и тоже не проверялся.

## Переменные окружения

Обязательные в production (без них приложение не стартует):

| Переменная | Назначение |
|---|---|
| `VK_GROUP_ID` | положительный id сообщества; `groups.getLongPollServer` без него не собрать |
| `VK_API_TOKEN` | токен группы VK |
| `VK_BOT_ID` | id бота для правила ответа |
| `VANE_URL` | база Vane (search API) |
| `AI_BASE_URL` | корень OpenAI-совместимого API LLM вместе с `/v1` (в деплое — vLLM) |
| `AI_CHAT_MODEL` | имя chat-модели; одна модель обслуживает ответ, саммари, OCR и поздравления |
| `QUARKUS_DATASOURCE_JDBC_URL` | JDBC URL PostgreSQL |
| `QUARKUS_DATASOURCE_USERNAME` / `QUARKUS_DATASOURCE_PASSWORD` | доступ к БД |
| `RABBITMQ_HOST` (и при необходимости `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`) | коннектор SmallRye RabbitMQ |

Опциональные: `AI_API_KEY` (дефолт `dummy`; нужен при включённой на сервере авторизации),
`VK_LONGPOLL_ENABLED` (дефолт `true`; `false` выключает опрос), `VK_BOT_MENTION`,
`SUMMARY_ENABLED_CHATS` (список peerId через запятую; пусто — саммаризация
выключена), `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`,
`AI_GREETING_SYSTEM_PROMPT`, `SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`,
`GREETINGS_ENABLED` (дефолт `true`; `false` выключает ежедневный прогон поздравлений),
`GREETINGS_TIMEZONE` (дефолт `Europe/Minsk`).

`GREETINGS_ENABLED=true` по умолчанию: первый прогон после деплоя — живой, в ближайшие 00:00 зоны
`GREETINGS_TIMEZONE`. Гасят его at-most-once (`greeting_log`), один прогон в сутки и отсутствие
ручного триггера; на время приёмки выставляйте `GREETINGS_ENABLED=false`.

`VK_SECRET` и `VK_CONFIRMATION_CODE` из контракта деплоя удалены: Callback-эндпоинта нет, приём идёт
опросом Bots Long Poll ([ADR 0008](../adr/0008-bots-long-poll-transport.md)).

Замечания:

- В `application.yml` prod-ключи RabbitMQ не заданы (только DevServices) — их должен задать деплой:
  `rabbitmq-host`/`rabbitmq-port`/… для коннектора. Ключи LLM в `application.yml` есть, но читают env:
  `AI_BASE_URL` — это корень OpenAI API вместе с `/v1` (расширение дописывает `/chat/completions`),
  `AI_CHAT_MODEL` — имя модели (ADR 0007).
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
  `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml` включает changesets
  `001-create-messages-table`, `002-create-user-profiles-table`, `003-create-group-profiles-table`,
  `004-create-summaries-table`, `005-create-long-poll-position-table`, `006-create-greeting-log-table`.
- Миграции применяются на старте (`quarkus.liquibase.migrate-at-start: true`,
  `application.yml`). Liquibase-ключей в тестах — `enabled: false` в `%test`.
- Правило: **применённый changeset не редактируется**; следующий номер — `007`.
- Rollback-команд Liquibase в проекте нет: откат приложения — предыдущий образ, схема обязана быть
  совместима на один релиз назад (аддитивные изменения). Откат данных — из бэкапа БД.
- Порядок деплоя: сначала применить совместимую миграцию (новый образ при старте), затем переключить
  трафик; при откате — вернуть предыдущий образ (схема остаётся).

## Health и наблюдаемость

- Health-эндпоинты: `/q/health`, `/q/health/ready`, `/q/health/live` (`quarkus-smallrye-health`).
  **Сейчас они отдают 503 DOWN:** семь incoming-каналов (`retry-5s-queue` … `retry-1h-queue`,
  `dlq-final-queue`, `summary-ready-queue`) объявлены без downstream-консьюмера, и проверка SmallRye
  Reactive Messaging помечает их `[KO]` (`SRMSG00208: has no downstreams`). Это не гейт деплоя, но
  readiness-проба Kubernetes не пройдёт; чтобы health стал зелёным, эти каналы нужно исключить из
  проверок или дать им консьюмера — отдельная задача. Health не связан с приёмом событий: опрос Bots Long
  Poll идёт своим циклом и от HTTP-проб не зависит.
- OpenTelemetry: экспорт логов/метрик/трейсов по OTLP (по умолчанию `http://localhost:4317`);
  эндпоинт коллектора задаётся стандартными ключами OTel. Prometheus-scrape и `/metrics` отсутствуют.
- Логи — JSON-формат (`quarkus-logging-json`) в собранном деплое.

## CI

`.github/workflows/build-and-test.yml` (push/pull_request в `main`, JDK 21 temurin):

| Job | Что проверяет |
|---|---|
| `build` | `./gradlew :src:app:quarkusBuild`, наличие `src/app/build/quarkus-app/quarkus-run.jar`, отсутствие `build/quarkus-app` |
| `test` | `./gradlew test` (нужен Docker для DevServices) |
| `lint` | `./gradlew detekt ktlintCheck` |
| `coverage` | `./gradlew test coverageReport coverageVerification` + upload HTML-отчёта |
| `docs` | `./scripts/check-capabilities.sh` (карта возможностей существует, чек зелёный) |

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
него (или с пустым значением) приложение стартует с выключенной саммаризацией (`application.yml`); в
тестовых профилях задан `1,2,3`.

## Что не проверено в этом проходе

- Сборка Docker-образов (JVM/native/legacy) — Dockerfile'ы прочитаны, `docker build` не запускался.
- Production-конфигурация RabbitMQ — ключей в `application.yml` нет, деплой задаёт их сам; ключи LLM в
  конфиге есть, но их значения приходят из env `AI_BASE_URL`/`AI_CHAT_MODEL`/`AI_API_KEY`.
- Native-сборка и её образ.
