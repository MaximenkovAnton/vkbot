# Документы проекта VK Bot — стадия docs (повторный проход)

**Board task:** `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`, задача «Подготовка проекта: vk».
**Стадия:** docs (spec), агент `architect`. **Проект:** `/Users/asmaksime1/IdeaProjects/test/vk`.
**Почему повторно:** verify вернул маршрут `docs` (`prepared-report.md` §6): **F1** — документированный запуск
`:src:app:quarkusDev` не работает с env из AGENTS.md; **G1** — неверная иерархия исключений; **G2** — два
диапазона строк за пределами файла; **G3** — устаревшие счётчики clean build. Эта стадия закрыла их и
перепроверила весь набор документов против дерева.
**Окружение:** macOS, Temurin 21.0.5, Gradle 9.3.0, Docker 28.3.2. Все команды — из корня репозитория.

## 0. Итог

- **F1, G1, G2, G3 закрыты**; дополнительно найдены и закрыты 4 расхождения (**N1–N4**, §1).
- **Все команды `AGENTS.md` §6 выполнены в этом проходе**; каждая — `exit 0`, кроме документированно
  неподдерживаемой `./gradlew jacocoTestReport` (`exit 1`, ровно как записано в AGENTS §6). Выводы — §4.
- **Документы написаны из кода.** Исходники, build-файлы, production-конфиги и CI этой стадией **не
  менялись**; `git status --short` — те же 147 путей, что видел verify, правки — 9 существующих документов.
- **ADR 0001–0006 остаются `accepted`, решения не менялись.** В 0005/0006 исправлены только устаревшие
  факты (номера строк, счётчики задач, классификация env, статус CI-джоба); противоречащих решений нет,
  поэтому `supersedes` никому не назначался и ни один accepted ADR не удалён.
- **`docs/capabilities/` не правились** — это продуктовая карта аналитика (стадия `capabilities` её уже
  создала, чек `capabilitiesCheck` зелёный). Единственная смысловая нестыковка карты передана владельцу
  (§6, H3).
- Именованные остатки (§6): health-эндпоинты отдают 503 (H1), stub-дефолт `${SUMMARY_ENABLED_CHATS:}`
  в конфиге (H2), Docker-образы не собирались (H4), продуктовые AC-пробелы (H5). Ни один не блокирует
  подготовку среды; H1/H2 — задача implement.

## 1. Что исправлено

| id | Было (verify) | Стало (в коде/документах) | Где |
|---|---|---|---|
| **F1** | `SUMMARY_ENABLED_CHATS` документирован как опциональный с дефолтом `""`; `:src:app:quarkusDev` с env из AGENTS.md §5/§7 падал на старте | Переменная документирована как **обязательная и непустая**: `${SUMMARY_ENABLED_CHATS:}` (`application.yml:14`) разворачивается в пустую строку, конвертер Quarkus её отвергает (`SRCFG00040`); добавлена во все списки обязательных env, quick start, Docker-run; ложное «пусто = выключено» удалено | `AGENTS.md:105-160`, `README.md:81,92-94,105-110,124-130`, `docs/development/configuration.md:8-10,29-43,152-157`, `docs/development/deployment.md:64-76,102-112,128-138`, `docs/adr/0006-configuration-and-secrets.md:34,49-58` |
| **G1** | AGENTS §9: «иерархия ошибок в `share`: `ValidationException` → `VALIDATION_FAILED`, `AccessDeniedException` → `ACCESS_DENIED`» | Иерархия описана как есть: база `VkBotAppException` в `share`; `receiver.ValidationException` → 400, `receiver.AccessDeniedException` → 403, `vk-facade.VkException` → 503; `share.ValidationException` — обычный `RuntimeException` из VO, попадает в generic-маппер → **503**, а не 400 (текущее поведение, не задумка) | `AGENTS.md:181-191`, код: `share/domain/exception/VkBotAppException.kt:3-11`, `receiver/domain/exception/ValidationException.kt:6-12`, `receiver/domain/exception/AccessDeniedException.kt:6-8`, `vkFacade/adapter/output/vk/exception/VkException.kt:6-8`, `receiver/adapter/input/vk/ExceptionHandler.kt:10-39`, `share/domain/vo/PeerId.kt:22` |
| **G2** | `configuration.md:137` → `src/persistence/build.gradle.kts:89-130` (файл 125 строк); `modules.md:132` → `VkSendMessageOutputAdapter.kt:13-32` (файл 30 строк); `data-flow.md:114` → `:19-31` | Диапазоны исправлены: `:89-125`; `:12-29`; `:18-29`. Тот же дефект найден ещё в двух местах: `AGENTS.md:22` → `:41,47,55,89-125`, `modules.md:82` → `:89-125` | `docs/development/configuration.md:148`, `docs/architecture/modules.md:82,132`, `docs/architecture/data-flow.md:114`, `AGENTS.md:22` |
| **G3** | `deployment.md:18` — «`BUILD SUCCESSFUL in 2m 1s`, 167 задач» как утверждение о текущем поведении | Свежий clean build этого прохода: **`BUILD SUCCESSFUL in 2m 4s`, `168 actionable tasks: 168 executed`**; счётчики обновлены в deployment.md, AGENTS §6 и ADR 0005, названы снимком прогона | `docs/development/deployment.md:18`, `AGENTS.md:119`, `docs/adr/0005-quality-gates-and-ci.md:72` |
| **N1** *(найдено в этом проходе)* | `./gradlew test --tests "*ClassName*"` (корневая форма) объявлен работающим | Эта форма **падает**: фильтр применяется ко всем test-задачам, и `:src:app:test` даёт `No tests found for given includes` → `BUILD FAILED`, exit 1. Документирована и проверена модульная форма `./gradlew :src:<module>:test --tests "*ClassName*"` | `AGENTS.md:123`, `README.md:167`, `docs/development/testing.md:9-19`; прогон — §4 |
| **N2** *(найдено в этом проходе)* | `deployment.md:105`/`configuration.md:139` говорили только «health-эндпоинты есть» | Зафиксирован факт: `/q/health`, `/ready`, `/live` **отдают 503 DOWN** — 7 incoming-каналов (`retry-5s-queue`…`retry-1h-queue`, `dlq-final-queue`, `summary-ready-queue`) не имеют downstream-консьюмера (`SRMSG00208`), messaging-проверка помечает их `[KO]`. При этом вебхук работает (подтверждение `test` 200, неверный секрет 403). Записано как известное ограничение с условием починки | `AGENTS.md:131,156-162`, `README.md:126-130`, `docs/development/deployment.md:102-112`, `docs/development/configuration.md:136-143` |
| **N3** *(найдено в этом проходе)* | AGENTS/README/deployment говорили о DevServices PostgreSQL и RabbitMQ | Dev-режим поднимает и **Ollama DevService** (лог `ollama/ollama:latest started`); в integration-test профиле он выключен и замокан WireMock | `AGENTS.md:156`, `README.md:61,92`, `docs/development/deployment.md:136`, `docs/adr/0006-configuration-and-secrets.md:50`; тест-профиль — `src/app/src/test/resources/application-integration-test.yaml:86-92` |
| **N4** *(найдено в этом проходе)* | `build.gradle.kts:124-126` назван блоком `check` (ADR 0005, testing.md); «джоб docs красный до стадии capabilities»; `AGENTS.md` ссылался на несуществующий в репозитории `docs.md`; в deployment.md джоб `docs` описан как будущий | Ссылка исправлена на `:130-133`; джоб `docs` описан как зелёный (карта существует); AGENTS §6 больше не ссылается на путь вне репозитория; deployment.md:126 — «карта существует, чек зелёный» | `docs/adr/0005-quality-gates-and-ci.md:41,79-83`, `docs/development/testing.md:157`, `AGENTS.md:116`, `docs/development/deployment.md:126` |

## 2. F1 — воспроизведение и доказательство (три прогона)

Код/конфиг: `src/app/src/main/resources/application.yml:14` (`summary.enabled-chats: ${SUMMARY_ENABLED_CHATS:}`),
потребитель — `ai/command/summary/ProcessSummaryCommandImpl.kt:18` (`@ConfigProperty(..., defaultValue = "")`).

**A. Старая документированная команда (без `SUMMARY_ENABLED_CHATS`)** — `:src:app:quarkusDev`:
```
Failed to load config value of type class java.lang.String for: summary.enabled-chats
...
SRCFG00040: The config property summary.enabled-chats is defined as the empty String ("") which the
following Converter considered to be null: io.smallrye.config.Converters$BuiltInConverter
...
ERROR ... Failed to start quarkus (ApplicationStartException)
```
То есть «пусто» — не «саммаризация выключена», а отказ старта; документировать как optional было неверно.

**B. Команда из исправленных AGENTS §7** (`SUMMARY_ENABLED_CHATS=1,2,3`):
```
vk-bot 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.34.2) started in 17.635s. Listening on: http://localhost:8080
```
Smoke того же запуска:
```
POST /vk/callback {"type":"confirmation","secret":"test"}   -> http=200, body "test"
POST /vk/callback {"type":"confirmation","secret":"wrong"}  -> http=403, {"error":"Secret code not provided or incorrect"}
GET  /q/health                                              -> http=503, 7 каналов [KO], все-events и outbound [OK]
```
Dev-процесс остановлен, порт 8080 свободен. Таким образом README/AGENTS теперь позволяют запустить проект
(шаги и env совпадают с прогоном), а health-статус описан честно (N2).

## 3. G1 — ошибки и HTTP-маппинг как в коде

- `share/domain/exception/VkBotAppException.kt:3-11` — абстрактная база с `status(): ExceptionStatus`;
  `ExceptionStatus.kt:3-8` — `VALIDATION_FAILED`, `ACCESS_DENIED`, `EXTERNAL_FAILURE`, `UNKNOWN`.
- Конкретные маппящиеся исключения живут у своих входов: `receiver/domain/exception/ValidationException.kt:6-12`
  (`VALIDATION_FAILED`), `receiver/domain/exception/AccessDeniedException.kt:6-8` (`ACCESS_DENIED`),
  `vkFacade/adapter/output/vk/exception/VkException.kt:6-8` (`EXTERNAL_FAILURE`).
- `receiver/adapter/input/vk/ExceptionHandler.kt:10-39`: `VkBotAppException` → 400/403/503;
  `handleClassCastException` → `receiver.ValidationException` → 400; любой другой `RuntimeException` → 503.
- `share/domain/exception/ValidationException.kt:3` — обычный `RuntimeException`, который бросают VO
  (`PeerId.kt:22`); он **не** `VkBotAppException`, поэтому вебхук с некорректным VO-полем получает 503, а не 400.
  Документы теперь говорят именно это; исправление поведения — задача implement, не docs.

## 4. Команды этого прохода (все — из корня)

| # | Команда | Вывод | Exit |
|---|---|---|---|
| 1 | `./gradlew clean build --console=plain` | `BUILD SUCCESSFUL in 2m 4s`; `168 actionable tasks: 168 executed` | 0 |
| 2 | `./gradlew build --console=plain` (после правок документов) | `BUILD SUCCESSFUL in 1m 15s`; `154 actionable tasks: 11 executed, 143 up-to-date` | 0 |
| 3 | `./gradlew test --console=plain` | `BUILD SUCCESSFUL in 1m 6s`; по XML: `tests=59 failures=0 errors=0 skipped=0` | 0 |
| 4 | `./gradlew :src:processor:test --tests "*MessageReceivedUsecaseInputTest*"` | `BUILD SUCCESSFUL in 6s` | 0 |
| 5 | `./gradlew detekt --console=plain` | `BUILD SUCCESSFUL in 592ms`, `maxIssues: 0` | 0 |
| 6 | `./gradlew ktlintCheck --console=plain` | `BUILD SUCCESSFUL in 593ms` | 0 |
| 7 | `./gradlew ktlintFormat --dry-run --console=plain` | `BUILD SUCCESSFUL in 541ms`; задача существует, форматтер не применялся (код уже отформатирован) | 0 |
| 8 | `./gradlew coverageReport coverageVerification --console=plain` | `BUILD SUCCESSFUL in 1s`; отчёт `build/reports/jacoco/coverageReport/{html,coverageReport.xml}` | 0 |
| 9 | `./gradlew :src:testing:arch-tests:test --console=plain` | `BUILD SUCCESSFUL in 859ms`; 15 тестов (Module 7, Layer 5, Gradle 3) | 0 |
| 10 | `./gradlew :src:app:quarkusBuild --console=plain` | `BUILD SUCCESSFUL in 4s`; `src/app/build/quarkus-app/quarkus-run.jar` существует; корневого `build/quarkus-app` нет | 0 |
| 11 | `./gradlew capabilitiesCheck --console=plain` | `OK: capability map paths, test paths, statuses and ADR ids resolve`; `BUILD SUCCESSFUL in 1s` | 0 |
| 12 | `./scripts/check-capabilities.sh` | `OK: capability map paths, test paths, statuses and ADR ids resolve` | 0 |
| 13 | `./gradlew :src:app:quarkusDev` (env §7 **без** `SUMMARY_ENABLED_CHATS`) | `Failed to load config value ... summary.enabled-chats`; `SRCFG00040`; `Failed to start quarkus` (см. §2A) | fail (ожидаемо, F1) |
| 14 | `./gradlew :src:app:quarkusDev` (env §7 **с** `SUMMARY_ENABLED_CHATS=1,2,3`) | `started in 17.635s. Listening on: http://localhost:8080`; smoke §2B | 0 |
| 15 | `./gradlew test --tests "*ReceiveMessageUsecase*"` (корневая форма) | `:src:app:test FAILED`, `No tests found for given includes`; форма признана неверной и исправлена (N1) | 1 |
| 16 | `./gradlew :src:app:jacocoTestReport --console=plain` | `> Task :src:app:jacocoTestReport FAILED`; `Gradle detected a problem ... uses this output of task ':src:app:jandex' without declaring an explicit or implicit dependency` — совпадает с AGENTS §6 «not supported» | 1 |
| 17 | `ruby -e "require 'yaml'; ..."` | `yaml ok` | 0 |
| 18 | `./gradlew tasks --all` + grep | корневых `quarkusDev`/`quarkusBuild` нет — деплой только `:src:app` | 0 |

Метрики этого прогона:

```
tests=59 failures=0 errors=0 skipped=0
app 7 | infrastructure 2 | persistence 9 | processor 2 | receiver 9 | share 8
arch-tests 15 | vk-facade 7 | ai 0 (своих тестов нет — известный пробел)
JaCoCo LINE covered=586 missed=1510 ratio=0.2796 (coverageMin=0.25)
JaCoCo INSTRUCTION ratio=0.2862, BRANCH ratio=0.1527
```

Негативные прогоны гейтов (detekt, ktlintCheck, ArchUnit L3/модули, coverage-порог, capabilities-путь/ADR)
выполнены стадией verify и записаны в `prepared-report.md` §2; документы не меняют код, поэтому здесь они
не повторялись. В этом проходе заново доказаны две «нестандартные» команды: `jacocoTestReport` (падает —
§4.16) и корневая форма `--tests` (падает — §4.15); обе теперь описаны корректно.

## 5. Проверка документов против дерева

- **Ссылки `path:line`:** скрипт извлёк 429 backtick-ссылок из `AGENTS.md`, `README.md`, `docs/**.md`
  и проверил существование пути и диапазона строк. После правок — **ни одного выхода за файл**; 2
  «MISSING» — ложные срабатывания на списках строк через запятую (`build.gradle.kts:41,47,55,89-125`).
- **Markdown-ссылки** внутри `docs/` резолвятся; индекс ADR в `docs/README.md` — 0001–0006; все `ADR NNNN`
  в документах существуют.
- **Карта модулей:** `settings.gradle.kts:22-32` — 11 `include`; 12 `build.gradle.kts` (корень + 11);
  каждая строка карты в `AGENTS.md` §3 и `docs/architecture/modules.md` существует в дереве.
- **Мёртвые имена:** `SEND_MESSAGE`, `DATABASE_URL`/`DB_USERNAME`/`DB_PASSWORD`, `/metrics`, `/health`,
  `docs/architecture/ai-tools.md` в документах отсутствуют (кроме явного списка «не документировать» в
  AGENTS §11 и исторической ссылки в ADR 0006); `jacocoTestReport` упомянут только как неподдерживаемая
  команда и в истории ADR 0005.
- **UNACTUAL audit.md §4** — ни одно утверждение не выжило: §4.1–4.12 переписаны ещё предыдущим проходом;
  этот проход повторно проверил F1/G1 и закрыл N1–N4. Структура документов и ссылки читателя сохранены.
- **Набор документов** (все существуют): `AGENTS.md`; `README.md`; `docs/README.md`;
  `docs/architecture/{overview,modules,patterns,data-flow}.md`; `docs/development/{testing,configuration,deployment}.md`;
  `docs/adr/0001-0006*.md`; продуктовую карту `docs/capabilities/` владеет аналитик (уже создана).

## 6. Что остаётся (named gaps, владельцы)

| id | Гэп | Доказательство | Владелец / условие |
|---|---|---|---|
| **H1** | `/q/health`, `/ready`, `/live` отдают **503 DOWN**: 7 incoming-каналов без downstream-консьюмера (`retry-*-queue`, `dlq-final-queue`, `summary-ready-queue`) попадают в messaging health как `[KO]` (`SRMSG00208`) | прогон §2B, §4.14; конфиг `src/app/src/main/resources/application.yml:66-167` | implement: исключить эти каналы из health-проверок (`health-enabled`) или дать им консьюмера; документы уже описывают фактическое поведение |
| **H2** | `${SUMMARY_ENABLED_CHATS:}` — stub-дефолт, противоречащий design §6 («дефолтов-заглушек в коде не добавляем»): вместо явной обязательности даёт `SRCFG00040` | `application.yml:14`, `ProcessSummaryCommandImpl.kt:18`; §2A | implement: убрать пустой дефолт (`${SUMMARY_ENABLED_CHATS}`) — тогда отсутствие env даст явную ошибку расширения; документы уже верны и после этого изменения (значение required) |
| **H3** | Продуктовая карта: `docs/capabilities/conversation-summary.md:15` говорит «по умолчанию список пуст», что после F1 недостижимо (пустой список = отказ старта) | `docs/capabilities/conversation-summary.md:15,39` | business-analyst в стадии capabilities: переформулировать («список обязателен и непуст; саммаризация только для перечисленных чатов») |
| **H4** | Docker-образы (JVM/native/legacy) не собирались; native/legacy требуют правки `.dockerignore` | `docs/development/deployment.md:32-34,48-58` | вне scope подготовки (design §12); при включении образов в гейт — исправить `.dockerignore` |
| **H5** | Продуктовые AC-пробелы: нет тестов у `conversation-summary`, `ai-tools-vane`, retry/DLQ `event-dispatch`, контекста `ai-answer`, запросов `vk-profiles` (из `capabilities.md` §6) | `docs/capabilities/*`, `prepared-report.md` §6 G5 | следующая тестовая работа (test-authoring), не блокирует подготовку среды |

## 7. Критерии приёмки стадии

| Критерий | Статус | Доказательство |
|---|---|---|
| `docs.md` и все объявленные документы созданы/актуальны | да | §5; 17 outputs зарегистрированы |
| AGENTS.md содержит выполненные команды и карту модулей | да | §4.1–4.18, §5 |
| README позволяет запустить проект | да | §2B: quick start с `SUMMARY_ENABLED_CHATS`, приложение стартует, вебхук отвечает |
| docs/architecture описывают код со ссылками | да | 429 проверенных ссылок, диапазоны валидны |
| docs/development описывают тесты/конфиг/деплой | да | команды §4, ключи `configuration.md`, артефакт/образы `deployment.md` |
| ADR 0001–0006 accepted, индекс актуален, история сохранена | да | ни один ADR не удалён; решения не менялись; `docs/README.md:23-32` |
| Ни один UNACTUAL из audit.md не выжил | да | §5 |
| docs/capabilities не написана здесь; долг назван | да | §6 H3/H5 (карта создана аналитиком) |
| Код, сборка, конфиги, CI не менялись | да | изменены 9 документов + этот отчёт; 147 путей git status |

## 8. Границы стадии

Изменены в этом проходе: `AGENTS.md`, `README.md`, `docs/architecture/modules.md`,
`docs/architecture/data-flow.md`, `docs/development/testing.md`, `docs/development/configuration.md`,
`docs/development/deployment.md`, `docs/adr/0005-quality-gates-and-ci.md`,
`docs/adr/0006-configuration-and-secrets.md` и этот отчёт. `docs/architecture/overview.md`,
`docs/architecture/patterns.md`, `docs/README.md` и `docs/capabilities/**` не правились (проверены, актуальны).
`src/**`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.github/**`, `scripts/**` не менялись;
dev-процессы остановлены, порт 8080 свободен. Логи прогонов: `/tmp/vk-docs-{clean-build,final-build,gates,dev1,dev2,jacoco}.log`.
