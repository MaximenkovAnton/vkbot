# Task report: Tests: ai module

- Task: `b3583b9a-6760-46b3-a1e2-b73370de05e6`
- Description: В модуле ai вообще нет тестов. Сделай их, по аналогии с теми, что уже есть в других модулях
- Workspace: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests
- Status: running-tasks

## Board tasks

### Фундамент тестов ai + юнит-тесты ai-answer (`ai-answer-unit-and-foundation`) — done

Рабочая директория: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests (ветка fix/ai-tests, не переключать). Сначала прочитай .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/spec.md (разделы 3, 5, 6, 8) и dod.md. Артефакты стадии — .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/ai-answer-unit-and-foundation/.

ЦЕЛЬ. Создать фундамент юнит-тестов модуля ai и закрыть тестами возможность ai-answer.

ОБЯЗАТЕЛЬНЫЕ AC: AC-1, AC-2, AC-3, AC-4, AC-5 (тексты — spec.md §5, строки таблицы — dod.md). AC строятся от DoD, а не от реализации.

ИЗМЕНЕНИЯ (ровно в этих зонах):
1. src/ai/build.gradle.kts — подключить плагин `java-test-fixtures` и test-конфигурации по образцу src/processor/build.gradle.kts:16-21: testFixturesImplementation(project(":src:share")), testFixturesImplementation(testFixtures(project(":src:testing:test-fixtures"))), testImplementation(testFixtures(project)), testImplementation(testFixtures(project(":src:testing:test-fixtures"))). Существующие зависимости не удалять, внешних библиотек не добавлять.
2. src/ai/src/testFixtures/kotlin/com/simarel/vkbot/ai/testfixtures/ — ЕДИНСТВЕННОЕ место прямого создания контрактных типов ai (TestDataFactoriesTest, src/testing/arch-tests/.../TestDataFactoriesTest.kt:29-45,60-73). Заведи `create*`-фабрики с реалистичными дефолтами у каждого параметра (иначе падает `every factory method parameter has a default`). Минимальный набор (эти фабрики переиспользуют задачи summary-unit-tests, vane-unit-tests, quarkus-ai-test — не дублируй их):
   - ответ: ChatMessage, GenerateAnswerOutputPortRequest/Response, ConversationContextRequest/Response, GenerateAnswerCommandRequest/Response, MessageRequireAnswerInputPortRequest (порт/команда — src/ai/src/main/kotlin/.../port/output/ai/GenerateAnswerOutputPort.kt, .../port/output/context/ConversationContextOutputPort.kt, .../command/answer/GenerateAnswerCommand.kt, .../port/input/messageRequireAnswer/MessageRequireAnswerInputPort.kt);
   - саммари: SummarizationRequest/Response, GenerateSummaryCommand.GenerateSummaryRequest/Response, ProcessSummaryInputPortRequest, GenerateSummaryInputPortRequest/Response (ProcessSummaryCommand.ProcessSummaryResponse — object, фабрика не нужна);
   - Vane: типы порта (VaneSearchRequest/Response, VaneSource) И типы REST-клиента из .../adapter/output/ai/tool/vane/VaneSearchClient.kt (VaneProvidersResponse, VaneProvider, VaneModel, клиентские SearchRequest/SearchResponse/Source/SourceMetadata). У порта и клиента одноимённые типы — имена фабрик обязаны различаться (например, createVanePortSearchRequest / createVaneClientSearchRequest).
   Данные бери через FakeVoProvider/FakeModelProvider из :src:testing:test-fixtures, где это уместно.
3. Юнит-тесты (plain JUnit 5, без Quarkus; запись вызовов — ConcurrentLinkedQueue; стиль — как src/processor/src/test/.../MessageReceivedUsecaseInputTest.kt и src/vk-facade/src/test/.../VkProfileInProcessAdapterTest.kt):
   - src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt — AC-1; использует существующий FakePublishEventCommand из :src:testing:test-fixtures; проверяет ANSWER_MESSAGE и ResponseMessage(text, responseTo=исходное).
   - .../command/answer/GenerateAnswerCommandImplTest.kt — AC-2; фейки портов могут быть приватными Recording-классами в тесте, но ЛЮБОЕ контрактное значение (ответы фейков) создавай только через фабрики из testFixtures.
   - .../adapter/output/context/ConversationContextRestAdapterTest.kt — AC-3; JSONB пересланных строй ObjectMapper'ом с KotlinModule и JavaTimeModule (как production infrastructure/config/MapperConfig.kt:12-16; ObjectMapperProvider.create() без jsr310 падает на OffsetDateTime). НЕ хардкодь JSON. Проверь лимит 20, null-текст → "", разделение профилей по знаку fromId, массив и одиночный объект, рекурсию.
   - .../adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt — AC-4; фейк UserAnswerAiService — приватный Recording-класс, записывает 4 отформатированные строки; проверь профили, историю, текущее сообщение, вложенные пересланные блоки, fallback idN, пустые профили.
   - .../adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt — AC-5; JSON из FakeVoProvider.createMessage() через ObjectMapperProvider.create().registerModule(JavaTimeModule()); фейк input port — приватный Recording.

ОГРАНИЧЕНИЯ: production-код (src/*/src/main/**) не менять; docs/** не трогать (это задача docs-and-coverage-record); не трогать src/app/**, src/testing/test-fixtures/**, чужие тесты и board-task артефакты; комментариев не добавлять (AGENTS.md §10); новых внешних зависимостей нет.

НЕГАТИВНОЕ ДОКАЗАТЕЛЬСТВО (в tests.md): по одной временной мутации production-кода на тестовый класс (например, лимит 20 → 19, убрать публикацию ANSWER_MESSAGE, убрать responseTo, испортить формат автора, заменить readValue на пустой Message), для каждой — процитированный FAILED, затем полный откат. В конце `git diff --stat -- 'src/*/src/main'` пуст, `git status` без временных файлов.

ПРОВЕРКИ (цитаты вывода и exit code в tests.md): ./gradlew :src:ai:test  # зелёный; ./gradlew :src:ai:test --tests "*<ClassName>*" для каждого класса; ./gradlew :src:testing:arch-tests:test  # PASSED (TestDataFactoriesTest, GradleDependenciesTest); ./gradlew detekt ktlintCheck  # exit 0; ./gradlew :src:ai:build  # exit 0.

ИТОГ: тесты не вакуумные (каждый падал на мутации), AC-1…AC-5 покрыты, фундамент для параллельных задач готов.

Board: test-authoring; stage: review; path: write → review
Reviewers: code-reviewer
Artifacts: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/ai-answer-unit-and-foundation/tests.md, /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/ai-answer-unit-and-foundation/review-summary.md
Verdicts (latest round of each role):
- code-reviewer (round 1): approve — 1 nit(s): Each abstraction is load-bearing and one expectation has one expression: a private helper with a single call site is indirection, and the same prompt block format should not be expressed two ways in one file
- test-reviewer (round 1): approve — 1 nit(s): DoD «Правила доказательства» п.2: цитата упавшего теста в tests.md §3 должна соответствовать доставленным байтам тестового класса (M10–M14, GenerateAnswerOutputAdapterTest).
Gates:
- tests [write]: exit 0 — `./gradlew :src:ai:test` — stale: the tree changed after this run
- architecture [write]: exit 0 — `./gradlew :src:testing:arch-tests:test` — stale: the tree changed after this run
- lint [write]: exit 0 — `./gradlew detekt ktlintCheck` — stale: the tree changed after this run
- build [write]: exit 0 — `./gradlew :src:ai:build` — stale: the tree changed after this run

### Карта возможностей, testing.md, AGENTS.md и ратчет покрытия (`docs-and-coverage-record`) — done

Рабочая директория: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests (ветка fix/ai-tests, не переключать). Сначала прочитай .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/spec.md (разделы 5, 8, 9) и dod.md, затем docs/capabilities/{index.md,ai-answer.md,conversation-summary.md,ai-tools-vane.md}, docs/development/testing.md, AGENTS.md §6/§8, docs/adr/0005-quality-gates-and-ci.md, scripts/check-capabilities.sh. Артефакты — .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/docs-and-coverage-record/.

ЦЕЛЬ. Записать тесты в продуктовый рекорд и поднять порог покрытия по решению человека. Все тесты уже в дереве (задачи ai-answer-unit-and-foundation, summary-unit-tests, vane-unit-tests, quarkus-ai-test завершены) — документы описывают ФАКТИЧЕСКИЕ имена файлов и числа прогонов, а не ожидания.

ОБЯЗАТЕЛЬНЫЕ AC: AC-17, AC-18, AC-19 (spec.md §5, dod.md).

ИЗМЕНЕНИЯ:
1. docs/capabilities/ai-answer.md — оставить AC-ai-answer-1 (e2e), добавить AC-ai-answer-2…6 с id и путями юнит-тестов (`src/ai/src/test/kotlin/...`); раздел «Пробелы приёмки» переписать: сборка контекста/промпт/публикация покрыты, неиспользуемый `Message.forwardedContextString()` оставить в пробелах.
2. docs/capabilities/conversation-summary.md — заменить «Нет AC с тестом» на AC-conversation-summary-1…7 с путями тестов; переписать «Пробелы приёмки».
3. docs/capabilities/ai-tools-vane.md — AC-ai-tools-vane-1…3 с путями тестов; переписать пробелы. У всех AC блок обязан содержать путь вида `src/.../src/(test|testFixtures)/...`, иначе check-capabilities.sh падает (scripts/check-capabilities.sh:100-106).
4. docs/development/testing.md — обновить: распределение/число тестов после прогона (включая ai и AiIntegrationTest), таблицу «Где лежат тесты» (строка ai; app + AiIntegrationTest), раскладку фикстур (src/ai/src/testFixtures), раздел «Ограничения» (убрать «Юнит-тестов ai нет» и утверждение, что ai проверяется только e2e), «Покрытие» (измеренный L, порог, ратчет). Команды для ai — добавить в раздел «Команды».
5. AGENTS.md — §6: строка `./gradlew test` с новым числом тестов и exit 0; строка `coverageVerification` с новым измеренным L/порогом. §8: фраза о пороге покрытия (0.40 сегодня, ратчет 0.60 — отдельная задача). Если coverageMin не поднят — цифры оставить прежними и записать измеренный L в testing.md.
6. docs/adr/0005-quality-gates-and-ci.md — в разделе «Покрытие» обновить измеренные значения (coverageMin, measuredLineCoverage, покрытые строки) только если порог поднят; решение ADR не переписывать, добавить строку «Ратчет 0.40 выполнен в задаче tests: ai module, измерено L=…».
7. build.gradle.kts (корень): выполнить ./gradlew coverageReport, взять измеренный LINE ratio L из build/reports/jacoco/coverageReport/jacocoTestReport.xml или вывода. Если L ≥ 0.40 — coverageMin = 0.40 и measuredLineCoverage = L; иначе оба значения не менять (0.25/0.2796) и записать L в testing.md. Порог выше L ставить нельзя.

ОГРАНИЧЕНИЯ: не менять src/** (ни production, ни тесты), src/ai/build.gradle.kts и src/app/build.gradle.kts; не удалять и не переписывать принятые ADR (только фактические числа); русский язык и стиль соседних разделов; комментариев в build.gradle.kts не добавлять. Не выдумывать имена тестов и числа — брать из дерева и прогонов.

НЕГАТИВНЫЕ ПРОВЕРКИ (в impl.md): временно испортить один путь теста в capability-файле → ./scripts/check-capabilities.sh FAILED с внятным сообщением, откатить → OK; временно поставить coverageMin = 0.99 → ./gradlew coverageVerification FAILED (`Rule violated ...`), откатить. В конце git diff не содержит временных правок.

ПРОВЕРКИ (цитаты и exit code в impl.md): ./gradlew test; ./gradlew coverageReport; ./gradlew coverageVerification; ./scripts/check-capabilities.sh; ./gradlew build; git diff --stat -- 'src/*/src/main' (пусто).

Board: quick-fix; stage: review; path: implementation → review → implementation → review
Depends on: ai-answer-unit-and-foundation, summary-unit-tests, vane-unit-tests, quarkus-ai-test
Reason: Dependency summary-unit-tests is now approved (round 2). Implement AC-17..AC-19 per the spec: add AC ids with real test paths to the three capability files, update docs/development/testing.md and AGENTS.md §6/§8, measure L with ./gradlew coverageReport (the aggregate XML currently reports LINE 1057/2096 = 0.5043 — verify yourself), raise coverageMin/measuredLineCoverage to 0.40/measured L if L >= 0.40, and record the ratchet in ADR 0005. Do not touch src/**.
Artifacts: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/docs-and-coverage-record/impl.md, /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/docs-and-coverage-record/review-summary.md
Verdicts (latest round of each role):
- code-reviewer (round 2): approve
- security-auditor (round 2): approve
- test-reviewer (round 2): approve
Gates:
- tests [implementation]: exit 0 — `./gradlew test --rerun-tasks` — stale: the tree changed after this run
- coverage [implementation]: exit 0 — `./gradlew coverageReport` — stale: the tree changed after this run
- coverage [implementation]: exit 0 — `./gradlew coverageVerification` — stale: the tree changed after this run
- capabilities [implementation]: exit 0 — `./scripts/check-capabilities.sh` — stale: the tree changed after this run
- tests [implementation]: exit 0 — `./gradlew test` — stale: the tree changed after this run
- build [implementation]: exit 0 — `./gradlew build` — stale: the tree changed after this run
- capabilities [implementation]: exit 1 — `./scripts/check-capabilities.sh` — stale: the tree changed after this run
- coverage [implementation]: exit 1 — `./gradlew coverageVerification` — stale: the tree changed after this run
- tests [implementation]: exit 0 — `./gradlew test --rerun-tasks` — fresh
- coverage [implementation]: exit 0 — `./gradlew coverageReport` — fresh
- coverage [implementation]: exit 0 — `./gradlew coverageVerification` — fresh
- capabilities [implementation]: exit 0 — `./scripts/check-capabilities.sh` — fresh
- build [implementation]: exit 0 — `./gradlew build` — fresh
- tests [implementation]: exit 0 — `./gradlew test` — fresh

### Quarkus-тест проводки ai в :src:app (`quarkus-ai-test`) — done

Рабочая директория: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests (ветка fix/ai-tests, не переключать). Сначала прочитай .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/spec.md (разделы 3, 5, 6, 9) и dod.md, затем src/app/src/test/kotlin/com/simarel/vkbot/integration/{VkWebhookIntegrationTest.kt,WireMockTestResource.kt,IntegrationTestProfile.kt}, src/app/src/test/resources/application.yaml, src/app/build.gradle.kts, src/ai/src/main/kotlin/.../adapter/output/ai/{answer/UserAnswerAiService.kt,summarization/SummarizationAiService.kt}, .../tool/vane/VaneSearchClient.kt, src/infrastructure/config/MapperConfig.kt. Артефакты — .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/quarkus-ai-test/.

ЦЕЛЬ. Доказать реальную проводку ai на Quarkus-уровне. @QuarkusTest в :src:ai не запускается (проверено: `Task 'integrationTestClasses' not found in project ':src:ai'`; плагин io.quarkus в ai запрещён ADR 0002), поэтому тест живёт в единственном деплое :src:app.

ОБЯЗАТЕЛЬНЫЙ AC: AC-16 (spec.md §5, dod.md).

ИЗМЕНЕНИЯ (ровно в этих зонах):
1. src/app/build.gradle.kts — добавить testImplementation(testFixtures(project(":src:ai"))) (test-конфигурация; production-граф не меняется).
2. src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt — @QuarkusTest + @TestProfile(IntegrationTestProfile::class) + @QuarkusTestResource(WireMockTestResource::class), @BeforeEach resetToDefaultStubs. Инжектируй бины GenerateAnswerOutputPort, SummarizationOutputPort, VaneSearchOutputPort. Запросы строй ТОЛЬКО фабриками из src/ai/src/testFixtures (провайдеры задачи ai-answer-unit-and-foundation). Тесты (один @Test на поведение):
   - ответ: заглушка POST /api/chat (как в VkWebhookIntegrationTest.kt:42-72) → execute(GenerateAnswerOutputPortRequest) возвращает текст модели; verify(postRequestedFor(urlPathEqualTo("/api/chat")).withRequestBody(containing(...))) доказывает, что отформатированные профили/история/текущее сообщение ушли в модель;
   - саммари: /api/chat возвращает assistant-content с JSON {"shortSummary":...,"fullSummary":...} → SummarizationOutputPort парсит и отдаёт short/full;
   - retry: сценарий WireMock 2 раза 500, затем 200 → результат получен, ровно 3 запроса (MAX_RETRIES=3 в SummarizationAiAdapter.kt:10);
   - Vane: заглушки GET /api/providers (один провайдер с chatModels и embeddingModels) и POST /api/search (message + sources) → VaneSearchOutputPort отдаёт message/sources (content/title/url); verify тела поиска (query/sources/optimizationMode); провайдер без одной из моделей → IllegalStateException.
3. src/app/src/test/kotlin/com/simarel/vkbot/integration/WireMockTestResource.kt — в start() добавить переопределение "quarkus.rest-client.vane.url" на server.baseUrl() (остальные переопределения сохранить); если Vane-тест не нужен для прохождения AC — не расширяй ресурс без причины.

ОГРАНИЧЕНИЯ: production-код не менять; docs/** не трогать; src/ai/** и чужие тесты не менять (провайдеры уже есть); комментариев нет; новых библиотек нет (WireMock уже в app). Тесту нужен Docker (DevServices PostgreSQL/RabbitMQ) — это ожидаемо; если Docker недоступен, зафиксируй блокер честно, не подменяй доказательство.

НЕГАТИВНОЕ ДОКАЗАТЕЛЬСТВО (в tests.md): временные мутации production-кода с откатом — например, MAX_RETRIES 3 → 1 (retry-тест должен упасть), убрать formatAuthor/formatCurrentMessage (verify промпта), убрать фильтр провайдеров Vane; для каждой процитировать FAILED и откатить. В конце `git diff --stat -- 'src/*/src/main'` пуст.

ПРОВЕРКИ (цитаты и exit code): ./gradlew :src:app:test --tests "*AiIntegrationTest*"; ./gradlew test; ./gradlew detekt ktlintCheck; ./gradlew build (если долго — минимум :src:app:test + build для AC-16; полный build докажет задача docs-and-coverage-record).

Board: test-authoring; stage: review; path: write → review
Depends on: ai-answer-unit-and-foundation
Artifacts: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/quarkus-ai-test/tests.md, /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/quarkus-ai-test/review-summary.md
Verdicts (latest round of each role):
- test-reviewer (round 1): approve
Gates:
- lint [write]: exit 0 — `./gradlew detekt ktlintCheck` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:app:test --tests "*AiIntegrationTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew test` — stale: the tree changed after this run
- build [write]: exit 0 — `./gradlew build` — stale: the tree changed after this run

### Юнит-тесты conversation-summary (`summary-unit-tests`) — done

Рабочая директория: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests (ветка fix/ai-tests, не переключать). Сначала прочитай .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/spec.md (разделы 3, 5, 6, 8) и dod.md, затем src/ai/src/main/kotlin/.../command/summary/ProcessSummaryCommandImpl.kt:37-128, GenerateSummaryCommandImpl.kt:27-90, adapter/output/ai/summarization/SummarizationAiAdapter.kt:17-42, adapter/input/event/SummaryRequestedEventProcessor.kt:17-28, usecase/summary/*. Артефакты — .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/summary-unit-tests/.

ЦЕЛЬ. Закрыть юнит-тестами правила и lifecycle саммаризации (возможность conversation-summary). Задача зависит от ai-answer-unit-and-foundation: в src/ai/src/testFixtures уже есть create*-фабрики контрактных типов ai и подключён java-test-fixtures — переиспользуй их, не дублируй.

ОБЯЗАТЕЛЬНЫЕ AC: AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-12 (тексты — spec.md §5, строки — dod.md).

ИЗМЕНЕНИЯ (ровно в этих зонах):
1. Тесты (plain JUnit 5, без Quarkus, стиль как у соседей):
   - src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt — AC-6, AC-7: чат не в enabled-chats; hasPending=true; последнее саммари PENDING; пустое окно; size < threshold при наличии прошлого саммари; ветка «без прошлого саммари» (before, current+1, batchSize); ветка «с прошлым саммари» (between, lastMessageId ?: 0, current, batchSize); успех → GenerateSummaryCommand с first/last id; исключение из команды проглатывается (ответ возвращается, не пробрасывается). Конструктор принимает конфиг строками — передавай значения прямо (в т.ч. проверь парсинг " 1, 2 ,3 ").
   - .../command/summary/GenerateSummaryCommandImplTest.kt — AC-8, AC-9: PENDING создаётся первым; пустой диапазон → COMPLETED с "Нет сообщений"/"Нет сообщений для суммаризации", LLM не зовётся; успех → COMPLETED с short/full; исключение LLM → markFailed + rethrow; формат `[автор]: текст` через "\n\n", автор — имя пользователя, имя группы (id = -fromId), fallback "Пользователь N"/"Группа N"; профили пользователей и групп запрошены раздельно; limit=1000.
   - .../adapter/output/ai/summarization/SummarizationAiAdapterTest.kt — AC-10: успех с первой попытки (1 вызов); успех со второй (2 вызова, третьего нет); три падения → проброшено последнее исключение, 3 вызова. Фейк SummarizationAiService — приватный Recording-класс.
   - .../adapter/input/event/SummaryRequestedEventProcessorTest.kt — AC-11: JSON SummaryRequestedPayload → ProcessSummaryInputPortRequest(peerId, conversationMessageId), event() == SUMMARY_REQUESTED. JSON строй ObjectMapperProvider.create() из payload-фабрики.
   - .../usecase/summary/ProcessSummaryUsecaseTest.kt и GenerateSummaryUsecaseTest.kt — AC-12: id переданы в команду, response.summaryId возвращён; фейки команд — приватные Recording-классы.
2. src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/ — добавь фабрику SummaryRequestedPayload (тип из share/domain/event, прямое создание в src/test запрещено): новый FakeEventProvider.kt с createSummaryRequestedPayload(peerId = реалистичный, conversationMessageId = реалистичный). Существующие фабрики не переписывай.
3. src/ai/src/testFixtures/** — только если не хватает фабрики из фундамента; добавляй, не дублируя, и сохраняй правило дефолтов.

ОГРАНИЧЕНИЯ: production-код не менять; docs/** и чужие board-task артефакты не трогать; src/app/** не трогать; комментариев нет; внешних зависимостей нет. Фейки одноразовые — приватные Recording-классы в тесте; используемые двумя классами — в testFixtures.

НЕГАТИВНОЕ ДОКАЗАТЕЛЬСТВО (в tests.md): временные мутации production-кода — например, убрать проверку hasPending, заменить threshold-условие, MAX_RETRIES 3 → 1, убрать markSummaryAsFailed, поменять "\n\n" на "\n"; для каждой процитировать FAILED и откатить. В конце `git diff --stat -- 'src/*/src/main'` пуст.

ПРОВЕРКИ (цитаты и exit code в tests.md): ./gradlew :src:ai:test; ./gradlew :src:ai:test --tests "*<ClassName>*" по каждому классу; ./gradlew :src:testing:arch-tests:test; ./gradlew detekt ktlintCheck; ./gradlew :src:ai:build.

Board: test-authoring; stage: review; path: write → review → write → review
Depends on: ai-answer-unit-and-foundation
Reason: Phase session was lost on restart; all six test classes, fixtures and the write-stage tests.md are in the tree and every declared write gate is fresh. Resume at review so test-reviewer can judge the existing write artifacts instead of rewriting them.
Artifacts: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/summary-unit-tests/tests.md, /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/summary-unit-tests/review-summary.md
Verdicts (latest round of each role):
- test-reviewer (round 2): approve
Gates:
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*SummaryUsecaseTest*"` — stale: the tree changed after this run
- architecture [write]: exit 0 — `./gradlew :src:testing:arch-tests:test` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*ProcessSummaryCommandImplTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*GenerateSummaryCommandImplTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*SummarizationAiAdapterTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*SummaryRequestedEventProcessorTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*ProcessSummaryUsecaseTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*GenerateSummaryUsecaseTest*"` — stale: the tree changed after this run
- architecture [write]: exit 0 — `./gradlew :src:testing:arch-tests:cleanTest :src:testing:arch-tests:test` — stale: the tree changed after this run
- lint [write]: exit 0 — `./gradlew detekt ktlintCheck` — stale: the tree changed after this run
- build [write]: exit 0 — `./gradlew :src:ai:build` — stale: the tree changed after this run

### Юнит-тесты ai-tools-vane (`vane-unit-tests`) — done

Рабочая директория: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests (ветка fix/ai-tests, не переключать). Сначала прочитай .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/spec.md (разделы 3, 5, 6, 8) и dod.md, затем src/ai/src/main/kotlin/.../command/vane/VaneSearchCommand.kt:31-77, adapter/output/ai/tool/VaneSearchToolAdapter.kt:10-31, adapter/output/ai/tool/vane/VaneSearchOutputAdapter.kt:15-58 и VaneSearchClient.kt. Артефакты — .agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/vane-unit-tests/.

ЦЕЛЬ. Закрыть юнит-тестами веб-поиск Vane (возможность ai-tools-vane). Зависит от ai-answer-unit-and-foundation: java-test-fixtures подключён, create*-фабрики контрактных типов ai есть — переиспользуй их.

ОБЯЗАТЕЛЬНЫЕ AC: AC-13, AC-14, AC-15 (spec.md §5, dod.md).

ИЗМЕНЕНИЯ (ровно в этих зонах):
1. src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommandImplTest.kt — AC-13: Web → sources ["web"], optimizationMode "balanced", подпись "Sources"; Academic → ["academic"], "quality", "Academic Sources"; Discussion → ["discussions"], "balanced", "Discussion Sources"; формат ответа (message, пустая строка, "--- <label> ---", "[n] title: url"); пустые sources → без блока; исключение порта → строка "Search failed: <message>" без проброса. Фейк VaneSearchOutputPort — приватный Recording-класс, возвращает значение из фабрики.
2. .../adapter/output/ai/tool/vane/VaneSearchOutputAdapterTest.kt — AC-14: первый провайдер с chat+embedding выбирается; провайдер без одной из моделей отфильтровывается; ключи первых моделей уходят в запрос; маппинг message/sources (content/title/url); sources=null → пустой список; нет провайдеров → IllegalStateException. Фейк VaneSearchClient — приватный Recording-класс (интерфейс, два метода). ВНИМАНИЕ: у порта и REST-клиента одноимённые VaneSearchRequest/Response/Source — используй алиасы импортов и фабрики с разными именами (createVanePortSearchRequest / createVaneClientSearchRequest и т.п.).
3. .../adapter/output/ai/tool/VaneSearchToolAdapterTest.kt — AC-15: каждый из search_web/search_academic/search_discussions делегирует в VaneSearchCommand с WebSearch/AcademicSearch/DiscussionSearch(query) и возвращает result; фейк команды — приватный Recording-класс.
4. src/ai/src/testFixtures/** — только если фабрик Vane из фундамента не хватает; добавляй без дублей, с дефолтами.

ОГРАНИЧЕНИЯ: production-код не менять; docs/**, src/app/**, src/testing/test-fixtures/** и чужие board-task артефакты не трогать; комментариев нет; внешних зависимостей нет.

НЕГАТИВНОЕ ДОКАЗАТЕЛЬСТВО (в tests.md): временные мутации — например, у Academic подменить sources на ["web"], убрать try/catch вокруг поиска, убрать фильтр `chatModels.isNotEmpty() && embeddingModels.isNotEmpty()`, убрать маппинг sources; для каждой процитировать FAILED и откатить. В конце `git diff --stat -- 'src/*/src/main'` пуст.

ПРОВЕРКИ (цитаты и exit code в tests.md): ./gradlew :src:ai:test; ./gradlew :src:ai:test --tests "*<ClassName>*" по каждому классу; ./gradlew :src:testing:arch-tests:test; ./gradlew detekt ktlintCheck; ./gradlew :src:ai:build.

Board: test-authoring; stage: review; path: write → review
Depends on: ai-answer-unit-and-foundation
Artifacts: /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/vane-unit-tests/tests.md, /Users/asmaksime1/IdeaProjects/test/vk/.worktrees/fix/ai-tests/.agile/tasks/b3583b9a-6760-46b3-a1e2-b73370de05e6/board-tasks/vane-unit-tests/review-summary.md
Verdicts (latest round of each role):
- test-reviewer (round 1): approve — 2 nit(s): Parameterized tables should not carry a hidden/unused column; each row should mean exactly what the test declares.; Capability record: the ai-tools-vane AC section must not state something false about the delivered tree.
Gates:
- tests [write]: exit 0 — `./gradlew :src:ai:cleanTest :src:ai:test` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` — stale: the tree changed after this run
- tests [write]: exit 0 — `./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"` — stale: the tree changed after this run
- architecture [write]: exit 0 — `./gradlew :src:testing:arch-tests:cleanTest :src:testing:arch-tests:test` — stale: the tree changed after this run
- lint [write]: exit 0 — `./gradlew detekt ktlintCheck` — stale: the tree changed after this run
- build [write]: exit 0 — `./gradlew :src:ai:build --rerun-tasks` — stale: the tree changed after this run
