# Карта возможностей VK Bot — редакция 2 (стадия capabilities)

**Стадия:** capabilities (spec), board task `3f1f83e5-b597-40ed-a911-ca9c12ef77c6`, задача «Подготовка проекта: vk».
**Проход:** второй — движок вернул таск `verify → docs → capabilities` после того, как `docs` исправил F1/G1,
найденные стадией `verify`.
**Вход:** `audit.md`, `design.md`, `scaffold.md`/`tooling.md`, `docs.md` (редакция 2), `prepared-report.md`
(verify), `docs/capabilities/` редакции 1 и весь код под ними.
**Окружение:** macOS, JDK 21.0.5 Temurin, Gradle 9.3.0 (`--offline`), DSH-агент `business-analyst`.
**Принцип:** карта написана **из кода**; каждое утверждение ниже проверено по файлам и строкам в этом проходе,
а не перенесено из `design.md`.

## 0. Итог

- Карта не изменилась по составу: `docs/capabilities/index.md` + 9 файлов, все 9 — `delivered`, каждая
  существует в коде. Изменены 3 файла по существу (см. §2): `conversation-summary.md` (семантика
  обязательного `SUMMARY_ENABLED_CHATS` и кто доставляет `SUMMARY_READY`), `vk-webhook.md` (заметка о 503
  теперь ссылается на принятое поведение в `AGENTS.md` §9), `index.md` (строка `conversation-summary`).
- Устаревших строк нет; ни одна возможность не переименована и не удалена. Правки — следствие того, что
  стадия `docs` исправила F1/G1 (`prepared-report.md` §3.5, §6), а не изменения кода: `src/**` с прошлого
  прохода не менялся.
- Чек `./scripts/check-capabilities.sh` (задача `./gradlew capabilitiesCheck` в `check` → `build`) падает на
  мёртвом пути кода, мёртвом тесте в acceptance criterion и мёртвом `ADR NNNN`; сегодня exit 0. Негативные
  прогоны — §9, процитированы.
- Acceptance criteria (EARS + id) — 17 в 7 возможностях, у каждого существующий тест. У `conversation-summary`
  и `ai-tools-vane` AC нет, у `event-dispatch` два AC не покрывают retry/DLQ — это зафиксированные пробелы, §7.
- В этой стадии изменены только `docs/capabilities/**` и этот файл. Код приложения (`src/**`),
  `docs/architecture/**`, `docs/adr/**`, `docs/development/**`, `build.gradle.kts`, `scripts/` не менялись.

## 1. Продуктовая картина (до вопросов и решений)

VK-бот — один деплой (`:src:app`), который принимает Callback-события сообщества VK, хранит переписку,
решает, нужен ли AI-ответ, генерирует его через LLM и отправляет в VK, а на длинных обсуждениях делает
саммаризацию. Runtime-цепочка: `VK webhook → RabbitMQ → persistence → processor → ai → vk-facade → VK API`.

### Возможности, которые уже есть в этой области

| id | Точки входа, доказывающие существование | Данные, которыми владеет | Journey |
|---|---|---|---|
| `vk-webhook` | `POST /vk/callback` (`src/receiver/src/main/kotlin/com/simarel/vkbot/receiver/adapter/input/vk/VkMediatorRouterImpl.kt:16-40`), секрет-декоратор (`VkMediatorRouterSecurityDecorator.kt:11-29`) | нет; создаёт доменное `Message` и событие `MESSAGE_NEW` | `journey-answer` (1) |
| `message-storage` | `MESSAGE_NEW`-консьюмер (`src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/event/MessageNewEventProcessor.kt:12-22`), `POST /persistence/*` (`PersistenceDataController.kt:21-127`), 9 in-process адаптеров | схема `messages`, `vk_user_profiles`, `vk_group_profiles`, `summaries` (jOOQ + Liquibase), lifecycle саммари | `journey-answer` (2), `journey-summary` (1), `journey-stored-data` |
| `answer-decision` | `MESSAGE_RECEIVED`-консьюмер (`src/processor/.../MessageReceivedEventProcessor.kt:12-22`), правила (`IsRequireAnswerCommand.kt:22-33`) | нет | `journey-answer` (3) |
| `ai-answer` | `MESSAGE_REQUIRE_ANSWER`-консьюмер (`src/ai/.../MessageRequireAnswerEventProcessor.kt:12-22`), `GenerateAnswerCommand.kt:32-47` | транзиентное окно памяти на 10 сообщений | `journey-answer` (4) |
| `vk-message-send` | `ANSWER_MESSAGE`-консьюмер (`src/vk-facade/.../SendMessageEventProcessor.kt:12-22`), `MessageSendUsecase.kt:15-44` | контракт `ForwardedMessages` и правило `random_id` | `journey-answer` (5), `journey-summary` (последний) |
| `conversation-summary` | `SUMMARY_REQUESTED`-консьюмер (`src/ai/.../SummaryRequestedEventProcessor.kt:12-28`), `ProcessSummaryCommandImpl.kt:36-125` | смысл lifecycle `PENDING`/`COMPLETED`/`FAILED` и правила запуска | `journey-summary` (центральный) |
| `vk-profiles` | `GET /api/profiles/{users,groups}/batch` (`src/vk-facade/.../VkFacadeController.kt:14-32`), in-process порты (`VkProfileInProcessAdapter.kt:8-36`) | контракт профиля и правило отрицательного id группы | поддержка обоих journey + внешний REST |
| `event-dispatch` | `@Incoming("all-events-queue")` (`src/infrastructure/.../EventDispatcher.kt:63-111`), публикатор (`PublishEventOutputAdapter.kt:15-33`) | топология брокера (exchange/очереди/retry) и каталог имён событий | транспорт обоих journey |
| `ai-tools-vane` | три `@Tool` (`src/ai/.../VaneSearchToolAdapter.kt:10-32`), `VaneSearchClient.kt:11-25` | нет | внутри `journey-answer` |

### Сущности, контракты и разрешения, которых касается продукт

- **Сущности:** `Message` (`share/domain/model/Message.kt`), `StoredMessage`, `Summary` + `SummaryStatus`
  (`PENDING`/`COMPLETED`/`FAILED`), `ResponseMessage`, `VkUserProfile`/`VkGroupProfile`, `Event` (закрытый
  enum из шести имён).
- **Контракты (порты):** `PersistenceDataInputPort` (+9 in-process адаптеров), `VkSendMessageInputPort`,
  `PublishEventOutputPort`/`PublishEventCommand`, `EventProcessor`, 9 persistence-портов `share`,
  `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort`, `VaneSearchOutputPort`.
- **Разрешения и доступ:** секрет и confirmation-код VK (`vk.secret`, `vk.confirmation-code`, ADR 0006);
  токен VK (`vk.api.token`) — единственный доступ к `messages.send`, `users.get`, `groups.getById`;
  права внешнего клиента ограничены тем, что реально смонтировано: `POST /vk/callback`,
  `/persistence/*`, `/api/profiles/*`; аутентификации на REST нет — это сетевой периметр деплоя.
- **Конфигурация, без которой продукт не стартует:** `VK_SECRET`, `VK_CONFIRMATION_CODE`, `VK_API_TOKEN`,
  `VK_BOT_ID`, `VANE_URL`, `SUMMARY_ENABLED_CHATS` (непустой), datasource и RabbitMQ env (ADR 0006).
- **Таблицы и миграции:** `messages` (JSONB `forwarded_messages`), `vk_user_profiles`, `vk_group_profiles`,
  `summaries` — владелец схемы `message-storage`; миграции в
  `src/persistence/src/main/resources/db/changelog/`.

### Как сюда входит новая «возможность» этой стадии

Новой продуктовой возможности по запросу нет — запрос «Подготовить проект» означает среду, а не фичу.
Продуктовый рекорд (`docs/capabilities/`) — то, что стадия добавляет к продукту: он **переиспользует**
ADR 0001–0006 (стек, один деплой, границы модулей, in-process порты, гейты, конфигурация), **опирается** на
существующий гейт (`capabilitiesCheck` в `check`) и **не меняет** ни одного runtime-контракта.
Journey карты — три пользовательских пути (§4); отдельного шага она не образует, это описание продукта.

### Владение общими вещами

| Общее | Владелец | Потребители |
|---|---|---|
| доменное `Message` | `vk-webhook` (единственный producer из VK JSON) | `message-storage`, `answer-decision`, `ai-answer` |
| `StoredMessage` (читающая модель) | `message-storage` | `ai-answer`, `conversation-summary` |
| `VkUserProfile`/`VkGroupProfile` | `vk-profiles` | `message-storage`, `ai-answer`, `conversation-summary` |
| таблицы `messages`, `vk_user_profiles`, `vk_group_profiles`, `summaries` | `message-storage` (схема, jOOQ, Liquibase) | все, кто читает через порты |
| `SummaryStatus`-lifecycle | `conversation-summary` | `message-storage` (хранит) |
| события и их payload'ы | producer события | consumer |
| каталог `Event` и routing key | `event-dispatch` | все |
| `PublishEventOutputPort`/`PublishEventCommand` | `event-dispatch` | все публикующие |
| `VkSendMessageInputPort` | `vk-message-send` | `ai-answer` (через `ANSWER_MESSAGE`), `conversation-summary` (через `SUMMARY_READY` → `SummaryReadyEventProcessor`) |
| `VkClient` (`messages.send` / `users.get`, `groups.getById`) | `vk-message-send` / `vk-profiles` по методам; общая часть — конфигурация (ADR 0006) | оба |
| `PersistenceDataInputPort` и 9 in-process адаптеров | `message-storage` | `ai` (через `share`-порты), REST |

## 2. Что изменилось в этой редакции и почему

Стадия `docs` исправила находки verify; две из них меняли утверждения карты. Код не менялся, поэтому
изменились ровно эти строки:

| Файл | Было | Стало | Почему |
|---|---|---|---|
| `conversation-summary.md` (правило и ADR 0006) | «чат не в `summary.enabled-chats` (по умолчанию список пуст)»; «по умолчанию саммаризация выключена» | список приходит из обязательного непустого `SUMMARY_ENABLED_CHATS`; пустое значение не конвертируется (`SRCFG00040`) и валит старт, поэтому «выключено по умолчанию» недостижимо | F1 verify: `application.yml:14` — `${SUMMARY_ENABLED_CHATS:}`; `ProcessSummaryCommandImpl.kt:18` — `defaultValue = ""`; `AGENTS.md` §5/§7, `docs/development/configuration.md:33-41`, ADR 0006 теперь фиксируют обязательность |
| `conversation-summary.md` (что делает) | «этот же модуль доставляет итог в чат» | `SUMMARY_READY` публикует `message-storage`; потребитель — `vk-facade` (`SummaryReadyEventProcessor.kt:23-54`), отправка — через `VkSendMessageInputPort` | это утверждение было неточным уже в редакции 1: доставку делает `vk-facade`, а не `persistence` |
| `conversation-summary.md` (что делает) | — | добавлено: `ProcessSummaryCommandImpl.kt:97-103` ловит исключение и логирует его, поэтому событие подтверждается и retry брокера нет | проверено по коду: `catch (e: Exception)` в `execute` не пробрасывает, а `GenerateSummaryCommandImpl.kt:59-62` помечает `FAILED` |
| `vk-webhook.md` (пробелы) | «Вопрос к архитектору, не AC» | «текущее поведение, зафиксированное в `AGENTS.md` §9; AC на 400 нет, изменение — кодовая задача» | G1 verify закрыт стадией docs: `AGENTS.md:188-190` документирует 503 для `share.ValidationException` |
| `index.md` (строка `conversation-summary`) | «публикует `SUMMARY_READY` и присылает итог» | «сохраняет итог через `message-storage` (публикует `SUMMARY_READY`), `vk-facade` присылает итог» | уточнение владельца публикации и доставки; состав путей не менялся |

ADR 0005 и 0006 стадия `docs` тоже переписала (sha артефактов отличается от прошлого прохода), но карта
ссылается только на id, а `docs/adr/0005-*.md` и `0006-*.md` существуют и не противоречат коду — преемник
не требуется. Подробнее — §8, находка 4.

## 3. Как я проверял код в этом проходе

- `ast-index map` — `215 files | 11 modules`; все 10 каталогов на месте.
- `ast-index search "@Incoming"` — ровно один вход (`EventDispatcher.kt:63`); `"@Path"` — два
  REST-контроллера (`PersistenceDataController.kt:22`, `VkFacadeController.kt:14`) плюс `vk/callback`
  (`VkMediatorRouterImpl.kt:16,24`); `"@Tool"` — три (`VaneSearchToolAdapter.kt:15,21,27`);
  `"@QuarkusMain"` — один (`src/app/.../Application.kt:6`); `@Scheduled` — нет. Состав совпадает с картой
  и с §6 «не отображено».
- `ast-index refs "RecognizeImagesCommand"` — только объявление и impl, вызовов нет; `refs
  "forwardedContextString"` — только определение (`Message.kt:40`). Оба остаются не-возможностями (§6).
- Все `src/…:[N-M]` диапазоны карты сверены с длиной файлов: скрипт-цикл не нашёл ни одного диапазона за
  концом файла и ни одного отсутствующего файла (вывод пуст).
- Прочитаны владельцы изменённых утверждений: `ProcessSummaryCommandImpl.kt` (18-34, 36-125, 97-103),
  `GenerateSummaryCommandImpl.kt:26-89`, `SummarizationAiAdapter.kt:17-41`,
  `PersistenceDataUsecase.kt:115-146`, `SummaryReadyEventProcessor.kt:23-54`, `MessageSendUsecase.kt:15-44`,
  `IsRequireAnswerCommand.kt:22-33`, `EventDispatcher.kt:63-111`,
  `PersistenceDataInProcessAdapter.kt` (9 классов), `TransientChatMemoryProvider.kt:9-16`.
- Тестовые пути не брались из документации: `check-capabilities.sh` проверяет существование каждого и в этом
  проходе даёт exit 0.

## 4. Строки, journey и владение

| id | Статус | Journey | Зависит от |
|---|---|---|---|
| `vk-webhook` | delivered | `journey-answer` (шаг 1) | `event-dispatch` |
| `message-storage` | delivered | `journey-answer` (2), `journey-summary` (1), `journey-stored-data` | `event-dispatch`, `vk-profiles` |
| `answer-decision` | delivered | `journey-answer` (3) | `event-dispatch` |
| `ai-answer` | delivered | `journey-answer` (4) | `event-dispatch`, `message-storage`, `ai-tools-vane` |
| `vk-message-send` | delivered | `journey-answer` (5), `journey-summary` (последний) | `event-dispatch` |
| `conversation-summary` | delivered | `journey-summary` (центральный) | `event-dispatch`, `message-storage`, `vk-message-send` |
| `vk-profiles` | delivered | поддержка обоих journey + внешний REST | — |
| `event-dispatch` | delivered | транспорт обоих journey | — |
| `ai-tools-vane` | delivered | `journey-answer`, внутри `ai-answer` | — |

Journeys и таблица владения — в `docs/capabilities/index.md` (три journey) и в каждом файле («Общие данные
и контракты»); сводка — §1 выше. Все отношения владения проверены по коду: producer каждого события —
единственный вызов `PublishEventCommand` с этим `Event`; у схемы один владелец — `persistence`.

## 5. Строки, оставшиеся устаревшими

Нет. Это второй проход по той же карте: возможности не переименованы, не удалены и не добавлены, поэтому
строки «осталась с прежней возможностью» указывать не на что. Три исправленных утверждения — §2, каждое
сверено с кодом, а не с документом.

## 6. Что не названо возможностью и почему

| Что есть в коде | Почему не строка карты |
|---|---|
| OCR: `RecognizeImagesCommand`, `RecognizeImagesCommandImpl`, `ImageRecognitionOutputAdapter`, `OcrAiService` | реализовано, но нет входного адаптера и ни одного вызова (`ast-index refs` — только impl). Недостижимая возможность — фиксирую как неиспользуемый код, а не продукт |
| `/q/health*` | стандартное расширение `quarkus-smallrye-health`; собственного кода и точки входа в дереве нет, сейчас отдаёт 503 DOWN (описано в `docs/development/configuration.md`) |
| OpenTelemetry, observation брокера | только конфигурация расширений; кода с трейсами/метриками нет |
| logging-декораторы `Command`/`Port` | сквозная инфраструктура без пользовательского результата |
| канал `summary-ready-queue` | объявлен в `application.yml`, `@Incoming` для него нет — мёртвая конфигурация |
| мёртвые ключи `summary.system-prompt`, `circuit-breaker.vk-profile` | никем не читаются |
| `Message.forwardedContextString()` | нет ни одного вызова (`ast-index refs`) |
| `@QuarkusMain`-запуск, тестовые модули `test-fixtures`/`test-common`/`arch-tests` | деплой и инфраструктура тестов, не поведение продукта |
| сам чек карты (`scripts/check-capabilities.sh`, `capabilitiesCheck`) | гейт документации, а не продуктовая возможность |

## 7. Пробелы приёмки (тестов нет или они не доказывают поведение)

17 AC в 7 возможностях: `vk-webhook` 5, `vk-message-send` 3, `answer-decision` 2, `message-storage` 2,
`vk-profiles` 2, `event-dispatch` 2, `ai-answer` 1. У `conversation-summary` и `ai-tools-vane` AC нет.

| Возможность | Что не доказано тестом |
|---|---|
| `conversation-summary` | весь поток: включённые чаты, pending, порог, batch-size, 3 попытки LLM, `FAILED` и отсутствие retry, публикация `SUMMARY_READY`, forward-блок итога |
| `ai-answer` | сборка контекста (20 сообщений, профили, JSONB), форматирование промпта, текст ответа (сквозной тест проверяет только `peer_id` в `messages.send`) |
| `event-dispatch` | retry-лестница, DLQ, `x-retry-count`, неизвестный routing key, routing key = `Event.name` на публикации |
| `ai-tools-vane` | всё: выбор источника/режима, формат ответа, ошибка поиска, выбор провайдера, вызов инструмента моделью |
| `vk-profiles` | VK-запросы `users.get`/`groups.getById`, отрицательный id группы, REST `/api/profiles/*`, сохранение профилей |
| `message-storage` | запись в PostgreSQL, REST `/persistence/*`, публикация `SUMMARY_READY` |
| `answer-decision` | сами правила `IsRequireAnswerCommandImpl` (в тесте fake) |
| `vk-webhook` | DMZ-маппинг `MessageMapper`, 400/503 для `share.ValidationException` |
| `vk-message-send` | детерминированный `random_id`, forward для группового чата, retry при ошибке VK |

Причина общая: в модуле `ai` нет тестов, REST-контроллеры не покрыты, а `test-fixtures` подменяет
`IsRequireAnswerCommand`. Это работа следующей тестовой задачи, карта её называет, но не маскирует AC без теста.

## 8. Находки

1. **F1 (verify) закрыт стадией docs, поведение кода не менялось.** `SUMMARY_ENABLED_CHATS` документирован
   как обязательный непустой (`AGENTS.md` §5/§7, `README.md`, `docs/development/configuration.md:33-41`,
   `docs/development/deployment.md`), а `conversation-summary.md` теперь не обещает «выключено по
   умолчанию». Код остался прежним: `application.yml:14` `${SUMMARY_ENABLED_CHATS:}` и
   `ProcessSummaryCommandImpl.kt:18` `defaultValue = ""`; старт без env по-прежнему падает. Если решат
   убрать дефолт-заглушку или сделать потребитель терпимым к пустому — это кодовая задача, после которой
   строку карты надо будет переписать ещё раз.
2. **G1 (verify) закрыт стадией docs.** `AGENTS.md` §9 теперь документирует, что
   `share.ValidationException` — обычный `RuntimeException` и даёт 503, а не 400. Карта больше не называет
   это вопросом к архитектору; поведение не менялось, AC на 400 нет. Если продукт решит, что null-поле VO
   на вебхуке должно быть 400, меняются `ExceptionHandler` и AC `vk-webhook` — это кодовая задача.
3. **G2/G3 verify** касались `file:line` в `docs/architecture/**` и счётчиков в `deployment.md` — вне
   `docs/capabilities/`; в этой стадии их не перепроверял (зона архитектора). Диапазоны строк самой карты
   проверены отдельно (§3) и корректны.
4. **ADR 0005/0006 переписаны стадией docs без преемника.** Артефакты прошлого прохода имели другие sha
   (`0005` — `a93e48…`, `0006` — `9a3a61…`), сейчас — `39aa89…`/`4263db…`. Оба ADR созданы в этом же
   проходе настройки и не закоммичены (`git status`: `?? docs/adr/`), поэтому это правка ещё не
   опубликованного решения, а не потеря истории. Если правило репозитория требует преемника и для такого
   случая — архитектору нужен `0007` с `supersedes`; карте преемник не нужен: id и решения совпадают с кодом.
5. **Неиспользуемый код продукта:** OCR-команда без входа и `Message.forwardedContextString()` без вызовов
   (§6). Решение — удалить или подключить; это отдельная задача, а не строка карты.
6. **Мёртвая конфигурация** (`summary-ready-queue`, `summary.system-prompt`, `circuit-breaker.vk-profile`)
   уже описана в `docs/development/configuration.md`; карта возможностью её не называет.
7. **Непроверенное как догадка не записано.** Все отношения владения в §1/§4 проверены по коду; ничего из
   «кажется, владелец такой» в карте нет.

### Допущения (на вопросы не отвечали — решений человек не принимал)

- Допущение 1: продуктовая карта описывает только реализованное (`delivered`), без планируемых
  возможностей. Если человек ждёт `planned`-строки (roadmap), их надо добавить отдельной задачей —
  иначе карта останется рекордом кода.
- Допущение 2: вопросы 400/503 и судьба OCR — не блокеры подготовки среды, а кодовая/продуктовая работа
  после неё. Если они блокеры, маршрут — `implement`, а не `verify`.
- Допущение 3: ADR можно править в том же проходе до коммита. Если нельзя — находка 4 требует `0007`
  с `supersedes` (архитектор).
- Допущение 4: сетевой периметр деплоя закрывает REST `/persistence/*` и `/api/profiles/*` (в коде
  аутентификации нет). Если периметра нет, это дефект безопасности, и карта «Разрешения» вводит в
  заблуждение — тогда `vk-profiles`/`message-storage` нуждаются в отдельном решении.

## 9. Чек устаревания: контракт и доказательства

`scripts/check-capabilities.sh` (в `check` задачей `build.gradle.kts:124-132` `capabilitiesCheck`, в CI —
джоб `docs`, `.github/workflows/build-and-test.yml:83-89`) проверяет:

1. `docs/capabilities/index.md` существует и ссылается на существующие файлы;
2. каждый файл возможности указан в индексе, объявляет `id`, равный имени файла;
3. каждый путь `src/…`/`docs/…` в индексе и файлах существует (диапазон строк отбрасывается);
4. статус — только `delivered`/`planned`/`deprecated`;
5. каждый `ADR NNNN` резолвится в `docs/adr/NNNN-*.md`;
6. у каждого acceptance criterion в его блоке есть существующий тестовый путь `src/…`.

### Негативные прогоны этого прохода (сломано → FAIL → восстановлено → OK)

Мёртвый путь кода (`SummaryRequestedEventProcessor.kt` → `…Gone.kt` в `conversation-summary.md`):

```
$ ./scripts/check-capabilities.sh
FAIL: docs/capabilities/conversation-summary.md references src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessorGone.kt:12-28, which does not exist
exit=1
```

Acceptance criterion без теста (убран тестовый путь у `AC-vk-profiles-1`):

```
$ ./scripts/check-capabilities.sh
FAIL: docs/capabilities/vk-profiles.md:65: acceptance criterion without a test path
exit=1
```

Мёртвый ADR id (`ADR 0004` → `ADR 0099` в `event-dispatch.md`):

```
$ ./scripts/check-capabilities.sh
FAIL: docs/capabilities/event-dispatch.md references ADR 0099, but docs/adr/0099-*.md does not exist
exit=1
```

Все три правки откачены побайтно (sha файлов совпадают с исходными), после отката:

```
$ ./scripts/check-capabilities.sh
OK: capability map paths, test paths, statuses and ADR ids resolve
exit=0
$ ./gradlew capabilitiesCheck --console=plain --offline --rerun-tasks
> Task :capabilitiesCheck
OK: capability map paths, test paths, statuses and ADR ids resolve
BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
exit=0
$ ./gradlew check --dry-run --console=plain --offline | grep capabilitiesCheck
:capabilitiesCheck SKIPPED
```

## 10. Команды стадии

| Команда | Вывод | Exit |
|---|---|---|
| `./scripts/check-capabilities.sh` | `OK: capability map paths, test paths, statuses and ADR ids resolve` | 0 |
| `./gradlew capabilitiesCheck --console=plain --offline --rerun-tasks` | `BUILD SUCCESSFUL in 1s`, 1 task executed | 0 |
| `./gradlew check --dry-run --console=plain --offline` | `:capabilitiesCheck SKIPPED`, `:coverageVerification SKIPPED` | 0 |
| `ast-index map` | `215 files \| 11 modules`, все 10 каталогов | 0 |
| `ast-index search "@Path"` / `"@Incoming"` / `"@Tool"` / `"@QuarkusMain"` | 2 REST-контроллера + callback, 1 консьюмер, 3 инструмента, 1 main | 0 |
| `ast-index refs "RecognizeImagesCommand"` | только объявление и impl — вызовов нет | 0 |
| `ast-index refs "forwardedContextString"` | только определение — вызовов нет | 0 |
| проверка диапазонов `src/…:[N-M]` по длине файлов | пусто (все диапазоны в пределах файлов) | 0 |

Полная сборка и тесты в этой стадии не перезапускались: приложение не менялось, а гейты `build`/`test`
проверены стадией `verify` (`prepared-report.md` §1). Чек карты входит в `build`, прогнан отдельно и в
`--dry-run` списке — этого достаточно для единственной новой зависимости.

## 11. Что осталось

1. `verify`/`test-writer`: закрыть пробелы §7 — в первую очередь `conversation-summary` (нужен тест с
   `summary.enabled-chats`, включающим peerId сценария), retry/DLQ `EventDispatcher`, forward и `random_id`
   `vk-message-send`.
2. `implement` (если человек решит): 400 для `share.ValidationException`, судьба OCR-кода и
   `Message.forwardedContextString()`, удаление дефолта `${SUMMARY_ENABLED_CHATS:}`.
3. Эксплуатация: мёртвые ключи (`summary-ready-queue`, `summary.system-prompt`,
   `circuit-breaker.vk-profile`) удалить или подключить — карта их возможностями не называет.
4. `architect`: решить по находке 4 (править ADR 0005/0006 на месте или выпустить `0007` с `supersedes`).

## 12. Соответствие критериям выхода стадии

| Критерий | Факт |
|---|---|
| Каждая возможность кода имеет строку или названа неотображённой с причиной | 9 строк + §6 |
| Каждая точка входа и тестовый путь — существующие пути дерева | чек проверяет; негативные прогоны §9 |
| Каждый `ADR NNNN` резолвится | чек проверяет; негативный прогон §9 |
| Все `AC-…` — EARS с id и именованным тестом | 17 AC; у каждого существующий `src/…` тест, проверяет чек |
| Отношения владения записаны, непроверенное — вопрос, не догадка | §1, §4 и «Общие данные и контракты» в 9 файлах; §8 находки |
| Чек устаревания в своей сборке падает на сломанном пути и проходит сейчас | §9, процитировано |
| `capabilities.md`: написанные строки, устаревшие строки, неназванное с причиной | §2, §5, §6, §7 |
| Код приложения и архитектурные документы не менялись | изменения стадии: `docs/capabilities/**` (3 файла) и этот файл; `src/**`, `docs/architecture/**`, `docs/adr/**`, `docs/development/**` не тронуты |
