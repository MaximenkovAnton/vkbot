# DoD — Tests: ai module (`b3583b9a-6760-46b3-a1e2-b73370de05e6`)

Одна строка = одно наблюдаемое поведение. Колонка «Test» — файл, который обязан существовать и проходить;
колонка «Негативная проверка» — временная мутация, на которой тест обязан упасть (откатить, в итоговом diff
мутаций нет).

| AC | Условие (EARS) | Test | Команда | Board task |
|---|---|---|---|---|
| AC-1 | КОГДА `MESSAGE_REQUIRE_ANSWER` обработан, публикуется `ANSWER_MESSAGE` с `ResponseMessage(text, responseTo = исходное)` | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/message/requireAnswer/GenerateAnswerUsecaseTest.kt` | `./gradlew :src:ai:test --tests "*GenerateAnswerUsecaseTest*"` | `ai-answer-unit-and-foundation` |
| AC-2 | КОГДА `GenerateAnswerCommandImpl` выполняется, контекст запрошен, AI-запрос собран, ответ обёрнут в `MessageText` | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/answer/GenerateAnswerCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*GenerateAnswerCommandImplTest*"` | `ai-answer-unit-and-foundation` |
| AC-3 | КОГДА собирается контекст, берутся ≤20 сообщений до текущего, профили разделены по знаку id, JSONB пересланных разобран (массив/объект, рекурсивно) | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/context/ConversationContextRestAdapterTest.kt` | `./gradlew :src:ai:test --tests "*ConversationContextRestAdapterTest*"` | `ai-answer-unit-and-foundation` |
| AC-4 | КОГДА строится промпт, профили/история/текущее сообщение/пересланные блоки отформатированы с fallback `idN` | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/answer/GenerateAnswerOutputAdapterTest.kt` | `./gradlew :src:ai:test --tests "*GenerateAnswerOutputAdapterTest*"` | `ai-answer-unit-and-foundation` |
| AC-5 | КОГДА приходит JSON `MESSAGE_REQUIRE_ANSWER`, он разобран в `Message` и передан в input port | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/messageRequireAnswer/MessageRequireAnswerEventProcessorTest.kt` | `./gradlew :src:ai:test --tests "*MessageRequireAnswerEventProcessorTest*"` | `ai-answer-unit-and-foundation` |
| AC-6 | КОГДА чат выключен / есть pending / последнее саммари PENDING — прогон пропускается без `GenerateSummaryCommand` | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*ProcessSummaryCommandImplTest*"` | `summary-unit-tests` |
| AC-7 | КОГДА прогон идёт, окно сообщений берётся по batch-size (before/current+1 или between/last ?: 0), пустое/короткое окно пропускается, команда получает first/last id | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*ProcessSummaryCommandImplTest*"` | `summary-unit-tests` |
| AC-8 | КОГДА `GenerateSummaryCommandImpl` выполняется, создаётся PENDING, сохраняется COMPLETED (или «Нет сообщений»), ошибка LLM → FAILED + rethrow | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*GenerateSummaryCommandImplTest*"` | `summary-unit-tests` |
| AC-9 | КОГДА сообщения форматируются, автор — имя пользователя/группы с fallback «Пользователь N» / «Группа N», профили запрошены раздельно | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*GenerateSummaryCommandImplTest*"` | `summary-unit-tests` |
| AC-10 | КОГДА LLM падает, 3 попытки и проброс последнего исключения; успех на 2-й — без третьей попытки | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapterTest.kt` | `./gradlew :src:ai:test --tests "*SummarizationAiAdapterTest*"` | `summary-unit-tests` |
| AC-11 | КОГДА приходит JSON `SUMMARY_REQUESTED`, peerId/conversationMessageId переданы в input port, event() = SUMMARY_REQUESTED | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/SummaryRequestedEventProcessorTest.kt` | `./gradlew :src:ai:test --tests "*SummaryRequestedEventProcessorTest*"` | `summary-unit-tests` |
| AC-12 | КОГДА выполняются usecase’ы саммари, id переданы в команду, результат возвращён | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/usecase/summary/ProcessSummaryUsecaseTest.kt`, `.../GenerateSummaryUsecaseTest.kt` | `./gradlew :src:ai:test --tests "*SummaryUsecaseTest*"` | `summary-unit-tests` |
| AC-13 | КОГДА Vane-инструмент ищет, источники/режим/подпись мапятся по типу, источники нумеруются, ошибка → `Search failed: …` | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommandImplTest.kt` | `./gradlew :src:ai:test --tests "*VaneSearchCommandImplTest*"` | `vane-unit-tests` |
| AC-14 | КОГДА Vane-адаптер выполняется, выбран первый провайдер с chat+embedding, ответ смаплен, без провайдера — `IllegalStateException` | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/vane/VaneSearchOutputAdapterTest.kt` | `./gradlew :src:ai:test --tests "*VaneSearchOutputAdapterTest*"` | `vane-unit-tests` |
| AC-15 | КОГДА вызывается `@Tool`-метод, он делегирует в `VaneSearchCommand` с нужным типом запроса | `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapterTest.kt` | `./gradlew :src:ai:test --tests "*VaneSearchToolAdapterTest*"` | `vane-unit-tests` |
| AC-16 | КОГДА приложение поднято с заглушками Ollama/Vane, реальная проводка отдаёт ответ модели, парсит JSON саммари с retry и мапит HTTP-ответ Vane | `src/app/src/test/kotlin/com/simarel/vkbot/integration/AiIntegrationTest.kt` | `./gradlew :src:app:test --tests "*AiIntegrationTest*"` | `quarkus-ai-test` |
| AC-17 | КОГДА работа сдана, в карте возможностей AC с id и путями тестов, `testing.md`/`AGENTS.md` обновлены, `check-capabilities.sh` → OK | `docs/capabilities/ai-answer.md`, `conversation-summary.md`, `ai-tools-vane.md`, `docs/development/testing.md`, `AGENTS.md` | `./scripts/check-capabilities.sh` | `docs-and-coverage-record` |
| AC-18 | КОГДА изменения завершены, `./gradlew build` зелёный, временных мутаций в `src/*/src/main` нет | все тесты + гейты | `./gradlew build`; `git diff --stat -- 'src/*/src/main'` | `docs-and-coverage-record` |
| AC-19 | КОГДА измеренный L ≥ 0.40, `coverageMin` = 0.40, измеренные числа записаны, `coverageVerification` зелёный; иначе порог 0.25 и L записан | `build.gradle.kts`, `docs/development/testing.md`, `AGENTS.md`, `docs/adr/0005-quality-gates-and-ci.md` | `./gradlew coverageReport coverageVerification` | `docs-and-coverage-record` |

## Правила доказательства

1. Для каждой строки — вывод команды с exit code в артефакте стадии; пересказ без вывода не считается.
2. Негативная проверка: по одной мутации на тестовый класс (например, `limit = 20` → `19` в
   `ConversationContextRestAdapter`, `MAX_RETRIES = 3` → `1` в `SummarizationAiAdapter`, удаление публикации
   `ANSWER_MESSAGE` в usecase), падение процитировано, мутация откатана, `git status` чист.
   Для AC-17…AC-19 негативная проверка — сломанный путь в карте (`check-capabilities.sh` → FAIL) и
   `coverageMin`, заведомо больший L (`coverageVerification` → `Rule violated`).
3. Тесты пишутся от DoD (эта таблица + `spec.md`), а не от реализации; проверяют наблюдаемое поведение.
4. Фикстуры и данные — только через `create*`-провайдеры; повторно используемые фейки не копируются.
