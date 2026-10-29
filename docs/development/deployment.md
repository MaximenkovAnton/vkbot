# Деплой и развёртывание

Один деплой — `:src:app` ([ADR 0002](../adr/0002-single-deployable-app-module.md)). Документ описывает
артефакт, образы, обязательные переменные окружения, миграции и откат; всё, что не проверялось прогоном,
помечено явно.

## Артефакт

```bash
./gradlew clean build
# -> src/app/build/quarkus-app/quarkus-run.jar
test -f src/app/build/quarkus-app/quarkus-run.jar   # smoke, входит в CI job build
test ! -e build/quarkus-app                          # второго раннера нет
```

`./gradlew build` собирает все 11 модулей, прогоняет тесты, detekt, ktlintCheck и порог покрытия и
собирает Quarkus-артефакт (`:src:app:quarkusBuild` входит в `build`). Проверено в этом проходе:
`BUILD SUCCESSFUL in 2m 4s`, 168 задач, артефакт на месте, корневого `build/quarkus-app` нет.

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
  -e VK_SECRET=... -e VK_CONFIRMATION_CODE=... -e VK_API_TOKEN=... -e VK_BOT_ID=... \
  -e VANE_URL=... -e SUMMARY_ENABLED_CHATS=1,2,3 \
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
| `VK_SECRET` | секрет Callback API |
| `VK_CONFIRMATION_CODE` | код подтверждения VK |
| `VK_API_TOKEN` | токен группы VK |
| `VK_BOT_ID` | id бота для правила ответа |
| `VANE_URL` | база Vane (search API) |
| `SUMMARY_ENABLED_CHATS` | непустой список peerId через запятую; пустое значение не конвертируется (`SRCFG00040`) и валит старт |
| `QUARKUS_DATASOURCE_JDBC_URL` | JDBC URL PostgreSQL |
| `QUARKUS_DATASOURCE_USERNAME` / `QUARKUS_DATASOURCE_PASSWORD` | доступ к БД |
| `RABBITMQ_HOST` (и при необходимости `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`) | коннектор SmallRye RabbitMQ |

Опциональные: `VK_BOT_MENTION`, `USER_ANSWER_SYSTEM_PROMPT`, `SUMMARY_SYSTEM_PROMPT`, `OCR_SYSTEM_PROMPT`,
`SUMMARY_THRESHOLD`, `SUMMARY_BATCH_SIZE`.

Замечания:

- В `application.yml` prod-ключи RabbitMQ и Ollama не заданы (только DevServices) — их должен задать
  деплой: `rabbitmq-host`/`rabbitmq-port`/… для коннектора и `quarkus.langchain4j.ollama.base-url` для LLM.
- `RABBITMQ_HOST` — стандартное отображение MicroProfile Config на `rabbitmq-host`; запуском prod-конфигурации
  в этом проходе не проверялось.
- Секреты храните в секрет-менеджере платформы; в репозиторий и образ они не попадают.

Полная таблица ключей и значений по умолчанию — [configuration.md](configuration.md).

## Миграции и откат

- Схема принадлежит `:src:persistence`; master-чейнджлог
  `src/persistence/src/main/resources/db/changelog/db.changelog-master.yaml` включает changesets
  `001-create-messages-table`, `002-create-user-profiles-table`, `003-create-group-profiles-table`,
  `004-create-summaries-table`.
- Миграции применяются на старте (`quarkus.liquibase.migrate-at-start: true`,
  `application.yml:32-34`). Liquibase-ключей в тестах — `enabled: false` в `%test`.
- Правило: **применённый changeset не редактируется**; следующий номер — `005`.
- Rollback-команд Liquibase в проекте нет: откат приложения — предыдущий образ, схема обязана быть
  совместима на один релиз назад (аддитивные изменения). Откат данных — из бэкапа БД.
- Порядок деплоя: сначала применить совместимую миграцию (новый образ при старте), затем переключить
  трафик; при откате — вернуть предыдущий образ (схема остаётся).

## Health и наблюдаемость

- Health-эндпоинты: `/q/health`, `/q/health/ready`, `/q/health/live` (`quarkus-smallrye-health`).
  **Сейчас они отдают 503 DOWN:** семь incoming-каналов (`retry-5s-queue` … `retry-1h-queue`,
  `dlq-final-queue`, `summary-ready-queue`) объявлены без downstream-консьюмера, и проверка SmallRye
  Reactive Messaging помечает их `[KO]` (`SRMSG00208: has no downstreams`). Проверено в этом проходе:
  `GET /q/health` → 503, при этом `POST /vk/callback` работает (confirmation → 200 `test`, неверный
  секрет → 403). Это не гейт деплоя, но readiness-проба Kubernetes не пройдёт; чтобы health стал
  зелёным, эти каналы нужно исключить из проверок или дать им консьюмера — отдельная задача.
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
export VK_SECRET=... VK_CONFIRMATION_CODE=... VK_API_TOKEN=... VK_BOT_ID=... VANE_URL=http://localhost:3000 \
       SUMMARY_ENABLED_CHATS=1,2,3
./gradlew :src:app:quarkusDev
```

DevServices поднимают PostgreSQL, RabbitMQ и Ollama (нужен Docker); в dev доступны dev-UI и hot reload.
Вебхук — `POST http://localhost:8080/vk/callback`. `SUMMARY_ENABLED_CHATS` обязателен и непуст
(`application.yml:14`); в тестовых профилях задан `1,2,3`.

## Что не проверено в этом проходе

- Сборка Docker-образов (JVM/native/legacy) — Dockerfile'ы прочитаны, `docker build` не запускался.
- Production-конфигурация RabbitMQ/Ollama — ключей в `application.yml` нет, деплой задаёт их сам.
- Native-сборка и её образ.
