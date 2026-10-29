# Спецификация задачи «Tests: ai module»

- **Task id:** `b3583b9a-6760-46b3-a1e2-b73370de05e6`
- **Ветка:** `fix/ai-tests`, worktree `/Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests`
- **Тип работы:** тесты (test-authoring), production-код не меняется.
- **Исходная формулировка:** «В модуле ai вообще нет тестов. Сделай их, по аналогии с теми, что уже есть в других модулях».

## 1. Цель

Закрыть тестами пробелы приёмки, которые карта возможностей уже назвала для `ai-answer`,
`conversation-summary` и `ai-tools-vane`, в стиле существующих модулей (plain JUnit 5 + fake-объекты,
данные только из `*Provider`), плюс один Quarkus-уровневый тест в единственном деплое `:src:app`,
и привести продуктовую документацию (`docs/capabilities/`, `docs/development/testing.md`, `AGENTS.md`)
в соответствие с кодом.

## 2. Non-goals / не входит в задачу

- Любые изменения production-кода (`src/*/src/main/**`): тесты пишутся под текущее поведение, а не наоборот.
  Единственные правки вне тестов — build-файлы `src/ai/build.gradle.kts`, `src/app/build.gradle.kts`
  (test-конфигурации) и документы.
- OCR-путь (`RecognizeImagesCommandImpl`, `ImageRecognitionOutputAdapter`): в карте возможностей он
  помечен как недостижимый (`docs/capabilities/index.md`, раздел «Не отображено в карте»).
- `TransientChatMemoryProvider` (окно памяти 10) — внутреннее поведение LangChain4j без наблюдаемого выхода.
- Удаление или изменение неиспользуемого `Message.forwardedContextString()` — отдельная кодовая задача;
  он остаётся таким, как есть (карта это фиксирует в «Пробелах приёмки» `ai-answer`).
- Новые mocking-фреймворки и новые библиотеки; новые CI-джобы; второй Quarkus-модуль/деплой (ADR 0002).
- Повышение порога покрытия выше 0.40 в этой задаче (ратчет 0.60 — отдельная задача по ADR 0005).

## 3. Что прочитано и что это задаёт

| Документ/код | Что задаёт |
|---|---|
| `docs/capabilities/index.md`, `ai-answer.md`, `conversation-summary.md`, `ai-tools-vane.md` | Точный список пробелов: «В модуле `ai` нет ни одного теста: сборка контекста (20 сообщений, профили, JSONB), форматирование промпта, публикация `ANSWER_MESSAGE`…»; у `conversation-summary` и `ai-tools-vane` — «Нет AC с тестом» и перечни непокрытого (`ProcessSummaryCommandImpl`, `GenerateSummaryCommandImpl`, `SummarizationAiAdapter`, `VaneSearchCommand`, `VaneSearchOutputAdapter`). |
| `AGENTS.md` §6, §8, §11 | Команды и гейты: `./gradlew build` = тесты + detekt + ktlint + порог покрытия + `capabilitiesCheck`; §8 — «fakes, not mocks», данные только из `*Provider`; §11 — не добавлять второй runner/деплой. |
| `docs/development/testing.md` | Стиль: backtick-имена, Given/When/Then, `Recording*`-фейки, конвенция `create*` с дефолтами; текущее утверждение «в `ai` собственных тестов нет» станет ложным. |
| `docs/adr/0003` | Слои и запрет импортов; тесты не должны тянуть LangChain4j в `command`/`usecase`. |
| `docs/adr/0004` | Контекст и профили — in-process порты `share`, а не HTTP. |
| `docs/adr/0005` | Гейты и ратчет покрытия: «целевые пороги 0.40 и 0.60 — отдельные задачи с новыми тестами; `coverageMin` поднимается только вместе с измеренным L». |
| `docs/adr/0006` | Конфигурация (`summary.enabled-chats`, промпты) — значение по умолчанию для тестов задаётся конструктором/провайдером, не файлом. |
| `src/testing/arch-tests/.../TestDataFactoriesTest.kt:29-45,60-73,174-187` | В `src/*/src/test/**` запрещено прямое создание типов `share/domain/**`, а также классов из файлов `**/port/**` и `**/command/**`; данные — только из `create*`-фабрик; фабрики сканируются в `src/testing/test-fixtures/src/main` и `<module>/src/testFixtures`, у каждого параметра `create*` обязателен дефолт. |
| `src/testing/arch-tests/.../GradleDependenciesTest.kt:12-65` | Тестовые модули только в test-конфигурациях; production-граф не меняется; build-файлов ровно 12. |
| `src/testing/arch-tests/.../LayerDependenciesTest.kt` | ArchUnit сканирует production-классы; тестовые фикстуры не влияют, но production-импорты проверит `./gradlew build`. |
| `src/ai/src/main/kotlin/**` (32 файла, 1383 строки) | Поведение, которое нужно доказать: `GenerateAnswerUsecase.kt:23-40`, `GenerateAnswerCommand.kt:32-47`, `ConversationContextRestAdapter.kt:28-103` (лимит 20, профили по знаку id, JSONB пересланных), `GenerateAnswerOutputAdapter.kt:30-119` (формат промпта), `ProcessSummaryCommandImpl.kt:37-128`, `GenerateSummaryCommandImpl.kt:27-90`, `SummarizationAiAdapter.kt:19-42` (3 попытки), `VaneSearchCommand.kt:31-77`, `VaneSearchOutputAdapter.kt:15-58`, `VaneSearchToolAdapter.kt:16-31`. |
| `src/ai/src/test` | Отсутствует — тестов в модуле нет. |
| `src/processor`, `src/vk-facade`, `src/persistence`, `src/receiver` тесты | Образец стиля и раскладки: plain JUnit 5, local `Recording*`-фейки, module `testFixtures` с `*Provider`, `ObjectMapperProvider`. |

Проверено опытным путём (пробные файлы удалены, дерево чистое):
- JSONB пересланных (`Message` с `@JvmInline value class`-полями) сериализуется/читается Jackson’ом
  в тестах: `jacksonObjectMapper() + KotlinModule + findAndRegisterModules()`; при этом `ObjectMapperProvider`
  без jsr310 падает на `OffsetDateTime`. Значит, JSON для теста `ConversationContextRestAdapter` строится
  mapper’ом с `JavaTimeModule` (как production: `infrastructure/config/MapperConfig.kt:12-16` + Quarkus).
- `@QuarkusTest` в `:src:ai` не запускается: `Task 'integrationTestClasses' not found in project ':src:ai'`
  (нет плагина `io.quarkus`; добавлять его — второй Quarkus-модуль, против ADR 0002).
  Поэтому Quarkus-уровень живёт в `:src:app` — единственном деплое.

## 4. Решения, согласованные с человеком

| Вопрос | Решение |
|---|---|
| Объём | Только пробелы карты возможностей: `ai-answer`, `conversation-summary`, `ai-tools-vane`. OCR и `TransientChatMemoryProvider` не трогаем. |
| Уровень | И юнит-тесты (plain JUnit 5 в `src/ai`), и Quarkus-уровень (`@QuarkusTest` в `:src:app`, т.к. в `src/ai` он не поднимается). |
| Документы | Да: AC в трёх capability-файлах, `docs/development/testing.md`, `AGENTS.md`. |
| Порог покрытия | Поднять `coverageMin` до 0.40, если измеренный агрегированный L ≥ 0.40; иначе оставить 0.25 и записать измеренный L. Рatchet 0.60 — отдельная задача. |
| Worktree | Уже создан: `fix/ai-tests` в `.worktrees/fix/ai-tests`; ветку не переключаем. |

## 5. Acceptance criteria (EARS)

### ai-answer (юнит)

- **AC-1** — КОГДА `MESSAGE_REQUIRE_ANSWER` обработан, СИСТЕМА ДОЛЖНА опубликовать `ANSWER_MESSAGE` с payload
  `ResponseMessage(messageText = сгенерированный ответ, responseTo = исходное сообщение)` и вернуть `ok`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt`
- **AC-2** — КОГДА `GenerateAnswerCommandImpl` выполняется, СИСТЕМА ДОЛЖНА передать сообщение в
  `ConversationContextOutputPort`, собрать `GenerateAnswerOutputPortRequest` из полученного контекста и вернуть
  ответ AI-порта как `MessageText`. Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommandImplTest.kt`
- **AC-3** — КОГДА собирается контекст разговора, СИСТЕМА ДОЛЖНА запросить не более 20 сообщений до текущего
  `conversationMessageId`, отобразить их в `ChatMessage` (null-текст → пустая строка) и разделить авторов
  по знаку `fromId`: неотрицательные — пользователи, отрицательные — группы; авторы пересланных сообщений
  (JSONB-массив или одиночный объект, рекурсивно) входят в те же выборки профилей.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapterTest.kt`
- **AC-4** — КОГДА адаптер ответа формирует промпт, СИСТЕМА ДОЛЖНА отформатировать профили пользователей и
  групп, историю чата блоками `[MESSAGE_START]…[MESSAGE_END]`, текущее сообщение и вложенные пересланные
  блоки с уровнями и отступами, а автора — как «Имя Фамилия (idN)» / «Название (idN)» либо `idN` при
  отсутствии профиля; пустые профили дают пустую строку.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt`
- **AC-5** — КОГДА приходит JSON-payload `MESSAGE_REQUIRE_ANSWER`, СИСТЕМА ДОЛЖНА разобрать его в `Message`,
  передать в `MessageRequireAnswerInputPort` и назвать событие `MESSAGE_REQUIRE_ANSWER`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt`

### conversation-summary (юнит)

- **AC-6** — КОГДА чат не входит в `summary.enabled-chats`, или у него есть `PENDING`-саммари, или последнее
  саммари зависло в `PENDING`, СИСТЕМА ДОЛЖНА пропустить прогон, не вызывая `GenerateSummaryCommand`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt`
- **AC-7** — КОГДА саммаризация продолжается, СИСТЕМА ДОЛЖНА взять окно сообщений размером `summary.batch-size`
  (`FindStoredMessagesBefore(peerId, current+1, batchSize)` без прошлого саммари; иначе
  `FindStoredMessagesBetween(peerId, lastMessageId ?: 0, current, batchSize)`), пропустить прогон при пустом
  окне и при `size < summary.threshold` вместе с прошлым саммари, а иначе вызвать `GenerateSummaryCommand`
  с first/last id из окна.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt`
- **AC-8** — КОГДА `GenerateSummaryCommandImpl` выполняется, СИСТЕМА ДОЛЖНА создать `PENDING`-запись,
  сохранить `COMPLETED` с short/full из LLM; при пустом диапазоне сохранить `COMPLETED` с текстом
  «Нет сообщений»; при исключении LLM пометить запись `FAILED` и пробросить исключение.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt`
- **AC-9** — КОГДА сообщения форматируются для LLM, СИСТЕМА ДОЛЖНА собрать `[автор]: текст` через `\n\n`,
  где автор — «Имя Фамилия» пользователя, имя группы (id берётся как `-fromId`), либо fallback
  «Пользователь N» / «Группа N»; профили запрашиваются раздельно для пользователей и групп.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt`
- **AC-10** — КОГДА LLM-саммаризация падает, СИСТЕМА ДОЛЖНА повторить попытку до 3 раз и после третьей
  пробросить последнее исключение (успех на 2-й попытке не пробрасывает и не зовёт LLM в третий раз).
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapterTest.kt`
- **AC-11** — КОГДА приходит JSON-payload `SUMMARY_REQUESTED`, СИСТЕМА ДОЛЖНА разобрать `peerId` и
  `conversationMessageId`, передать их в `ProcessSummaryInputPort` и назвать событие `SUMMARY_REQUESTED`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessorTest.kt`
- **AC-12** — КОГДА выполняются `ProcessSummaryUsecase` / `GenerateSummaryUsecase`, СИСТЕМА ДОЛЖНА передать
  id в соответствующую команду и вернуть её результат (`GenerateSummary` — `summaryId`).
  Tests: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/ProcessSummaryUsecaseTest.kt`,
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/GenerateSummaryUsecaseTest.kt`

### ai-tools-vane (юнит)

- **AC-13** — КОГДА выполняется поиск Vane, СИСТЕМА ДОЛЖНА отобразить инструмент в источники/режим/подпись:
  Web → `["web"]`/`balanced`/`Sources`, Academic → `["academic"]`/`quality`/`Academic Sources`,
  Discussion → `["discussions"]`/`balanced`/`Discussion Sources`; отформатировать нумерованные источники;
  при ошибке порта вернуть строку `Search failed: <message>`, не пробрасывая исключение.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommandImplTest.kt`
- **AC-14** — КОГДА выполняется `VaneSearchOutputAdapter`, СИСТЕМА ДОЛЖНА выбрать первый провайдер, у которого
  есть и chat-, и embedding-модель, отправить поиск с его ключами, смапить `message` и `sources`
  (content/title/url); при отсутствии подходящего провайдера — бросить `IllegalStateException`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchOutputAdapterTest.kt`
- **AC-15** — КОГДА вызывается любой `@Tool`-метод, СИСТЕМА ДОЛЖНА делегировать в `VaneSearchCommand` с
  соответствующим типом запроса и вернуть его `result`.
  Test: `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapterTest.kt`

### Quarkus-уровень (`:src:app`)

- **AC-16** — КОГДА приложение поднято с HTTP-заглушками Ollama и Vane, СИСТЕМА ДОЛЖНА провести реальную
  проводку CDI/LangChain4j/Quarkus REST: `GenerateAnswerOutputPort` возвращает ответ модели, а запрос к
  Ollama содержит отформатированные профили/историю/текущее сообщение; `SummarizationOutputPort` парсит
  JSON-ответ и повторяет запрос при 5xx (3 попытки), а `VaneSearchOutputPort` мапит реальный HTTP-ответ
  провайдера/поиска.
  Test: `src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt`

### Документы и гейты

- **AC-17** — КОГДА работа сдана, СИСТЕМА ДОЛЖНА содержать в `docs/capabilities/ai-answer.md`,
  `conversation-summary.md`, `ai-tools-vane.md` AC с id и существующими путями тестов, обновлённые
  `docs/development/testing.md` и `AGENTS.md`, и `./scripts/check-capabilities.sh` должен завершиться
  `OK: ... resolve` (exit 0). Test: сам скрипт + перечисленные тесты.
- **AC-18** — КОГДА изменения завершены, `./gradlew build` ДОЛЖЕН пройти (тесты, detekt, ktlint, покрытие,
  `capabilitiesCheck`), и в дереве не должно остаться временных мутаций production-кода.
  Test: `./gradlew build` (exit 0, `BUILD SUCCESSFUL`), `git diff --stat -- src/*/src/main` пуст.
- **AC-19** — КОГДА измеренный агрегированный L ≥ 0.40, `coverageMin` ДОЛЖЕН быть поднят до 0.40,
  `measuredLineCoverage` и числа в `docs/development/testing.md`, `AGENTS.md` и ADR 0005 обновлены,
  а `./gradlew coverageVerification` проходит с новым порогом; иначе `coverageMin` остаётся 0.25,
  измеренный L записан.
  Test: `./gradlew coverageReport`, `./gradlew coverageVerification`, `./gradlew build`.

## 6. Контракты и совместимость

- Production-контракты не меняются: ни один `port`/`command`/событие/схема/конфиг не правится.
- Совместимость с клиентами и данными: не затрагивается (тесты + документы).
- Тестовые данные: только `create*`-фабрики. Новые фабрики — в `src/ai/src/testFixtures` для типов модуля
  `ai`; для типов `share` (`SummaryRequestedPayload`) — в `src/testing/test-fixtures`. У каждого параметра
  фабрики обязан быть реалистичный дефолт (`TestDataFactoriesTest`).
- Новые зависимости: только test-конфигурации (`java-test-fixtures`, `testFixtures(project(...))`,
  `testFixtures(project(":src:testing:test-fixtures"))`); внешних библиотек не добавляем — WireMock уже есть
  в `:src:app` (`src/app/build.gradle.kts:52`).

## 7. План тестов (команды)

| Команда | Что доказывает |
|---|---|
| `./gradlew :src:ai:test` | все юнит-тесты модуля `ai` |
| `./gradlew :src:app:test --tests "*AiIntegrationTest*"` | Quarkus-проводка ai в деплое (нужен Docker) |
| `./gradlew test` | весь набор, включая старые тесты (регресса нет) |
| `./gradlew detekt ktlintCheck` | стиль новых тестов и фикстур |
| `./gradlew :src:testing:arch-tests:test` | `TestDataFactoriesTest`, `GradleDependenciesTest`, `LayerDependenciesTest` |
| `./gradlew coverageReport` / `coverageVerification` | измеренный L и порог (ратчет по AC-19) |
| `./scripts/check-capabilities.sh` | AC-пути и ADR в карте возможностей резолвятся |
| `./gradlew build` | единый гейт AC-18 |

## 8. Board tasks (DAG)

| id | Board | Agent | dependsOn | Что делает | Файлы (май-тач) |
|---|---|---|---|---|---|
| `ai-answer-unit-and-foundation` | `test-authoring` | test-writer | — | Фундамент тестов `ai`: `java-test-fixtures` + test-зависимости в `src/ai/build.gradle.kts`; все `create*`-провайдеры контрактных типов `ai` в `src/ai/src/testFixtures`; 5 юнит-тестов ai-answer (AC-1…AC-5). | `src/ai/build.gradle.kts`, `src/ai/src/testFixtures/**`, `src/ai/src/test/kotlin/com/simarel/vkbot/ai/{usecase/message,command/answer,adapter/output/context,adapter/output/ai/answer,adapter/input/event}/**` |
| `summary-unit-tests` | `test-authoring` | test-writer | `ai-answer-unit-and-foundation` | 6 юнит-тестов conversation-summary (AC-6…AC-12); при необходимости добавляет провайдер `SummaryRequestedPayload` в `src/testing/test-fixtures` и дополняет `src/ai/src/testFixtures`. | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/{command/summary,adapter/output/ai/summarization,adapter/input/event,usecase/summary}/**`, `src/ai/src/testFixtures/**`, `src/testing/test-fixtures/src/main/**` |
| `vane-unit-tests` | `test-authoring` | test-writer | `ai-answer-unit-and-foundation` | 3 юнит-теста ai-tools-vane (AC-13…AC-15); при необходимости дополняет `src/ai/src/testFixtures` (Vane-провайдеры: типы порта и REST-клиента различаются — имена фабрик обязаны это отражать). | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/{command/vane,adapter/output/ai/tool}/**`, `src/ai/src/testFixtures/**` |
| `quarkus-ai-test` | `test-authoring` | test-writer | `ai-answer-unit-and-foundation` | Quarkus-уровень (AC-16) в `:src:app`: `AiIntegrationTest` (@QuarkusTest + WireMock Ollama/Vane), `testFixtures(project(":src:ai"))` в `src/app/build.gradle.kts`, при необходимости — переопределение `quarkus.rest-client.vane.url` в `WireMockTestResource`. | `src/app/build.gradle.kts`, `src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt`, `src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt` |
| `docs-and-coverage-record` | `quick-fix` | `specialist` | `ai-answer-unit-and-foundation`, `summary-unit-tests`, `vane-unit-tests`, `quarkus-ai-test` | AC с путями тестов в трёх capability-файлах; обновление `docs/development/testing.md`, `AGENTS.md`, ADR 0005; ратчет `coverageMin` до 0.40 при измеренном L ≥ 0.40 (AC-17…AC-19); финальные прогоны гейтов. | `docs/capabilities/{ai-answer,conversation-summary,ai-tools-vane}.md`, `docs/development/testing.md`, `AGENTS.md`, `docs/adr/0005-quality-gates-and-ci.md`, `build.gradle.kts` |

Правила DAG: `summary-unit-tests` и `vane-unit-tests` и `quarkus-ai-test` независимы и идут параллельно;
`docs-and-coverage-record` запускается только после всех тестовых задач.

## 9. Риски

1. **Docker/DevServices.** AC-16 требует `@QuarkusTest` в `:src:app`, а он поднимает PostgreSQL/RabbitMQ
   DevServices (как существующий `VkWebhookIntegrationTest`). В окружении без Docker тест не пройдёт —
   это блокер с честной фиксацией, а не подмена доказательства. В CI и на этой машине Docker есть
   (прошлые проходы зелёные).
2. **Хрупкость формата промпта.** AC-4 фиксирует текущий формат `[MESSAGE_START]…`; будущая правка промпта
   потребует правки теста. Это осознанно: формат — контракт с моделью.
3. **JSONB-формат.** Тест контекста должен строить JSON тем же набором модулей, что production
   (`KotlinModule` + `JavaTimeModule`), иначе он проверит формат, которого приложение не пишет.
4. **Ратчет покрытия.** Если измеренный L < 0.40 (маловероятно при ~13 классах тестов на непокрытый модуль),
   `coverageMin` остаётся 0.25; нельзя ставить порог «на глаз» выше L (гейт упадёт).
5. **Временные мутации для негативного доказательства.** Каждая тестовая задача обязана доказать
   невакуумность теста временной поломкой production-кода и откатом; мутации не должны попасть в итоговый
   diff (`git diff --stat -- src/*/src/main` пуст, `git status` без лишнего).
6. **Стоимость.** 5 board tasks, 3 волны, ~13 юнит-классов + 1 Quarkus-класс + 6 документов; полный
   `./gradlew build` выполняется несколько раз (включая Docker-тесты). Это осознанная цена выбранного
   «и юнит, и Quarkus».

## 10. Провенанс и артефакты

- Задача: `.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/spec.md` (этот файл) и `dod.md` (таблица
  AC → тест → команда) в той же директории.
- Board task артефакты пишутся в `.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/<id>/`.
- Ветка/дерево: `fix/ai-tests` в `.worktrees/fix/ai-tests`; ветку не переключаем, коммит/пуш — в MITM после
  приёмки человеком.
