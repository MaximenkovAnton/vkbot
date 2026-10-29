# review-summary.md — `ai-answer-unit-and-foundation`

- **Board task:** `ai-answer-unit-and-foundation` (board `test-authoring`, stage `review`, round 1)
- **Task:** `b3583b9a-6760-46b3-a1e2-b73370de05e6` «Tests: ai module»
- **Reviewers:** `test-reviewer` (AC + test quality), `code-reviewer` (code quality — both are required:
  review-stage agent + plan `reviewers`)
- **Диф ревью:** `M src/ai/build.gradle.kts` (+9), untracked `src/ai/src/test/` (5 классов, 18 тестов),
  `src/ai/src/testFixtures/` (4 провайдера). Production-код не изменён.
- **Итог:** **approve / approve**, 0 blocking, 2 nit. Board task готов к завершению; находки — polish,
  доработка не требуется.

## Доказательства гейтов (не перезапускались)

Ledger fresh на дереве `de9f3df3b9734a9debf6d1e514851117710bc6815b830499d0ddb931e3930629`; ни один
reviewer и lead не запускали Gradle повторно:

| criterion | command | exit | доказательство |
|---|---|---|---|
| tests | `./gradlew :src:ai:test` | 0 | `BUILD SUCCESSFUL in 11s`, JUnit XML 18/0 |
| architecture | `./gradlew :src:testing:arch-tests:test` | 0 | `TestDataFactoriesTest` 2/0, `GradleDependenciesTest` 3/0, `LayerDependenciesTest` 5/0, `ModuleDependenciesTest` 7/0, `DetektExcludesPathTest` 1/0 |
| lint | `./gradlew detekt ktlintCheck` | 0 | `:src:ai:ktlintTestFixturesSourceSetCheck`, `:src:ai:ktlintTestSourceSetCheck` |
| build | `./gradlew :src:ai:build` | 0 | `BUILD SUCCESSFUL` |

Независимо от ledger проверено `src/ai/build/test-results/test/*.xml`: 1+2+7+6+2 = **18 tests,
0 failures, 0 errors, 0 skipped** — набор реально исполнялся. `git diff --stat -- 'src/*/src/main'`
пуст, следов временных мутаций и лишних файлов в `git status` нет (только ожидаемые build-файл и новые
тестовые каталоги).

## Вердикты

| Reviewer | Решение | Находки |
|---|---|---|
| `test-reviewer` | **approve** | 1 nit (tests.md M10–M14) |
| `code-reviewer` | **approve** | 1 nit (`messageBlock()`) |

Обе находки имеют severity `nit`, не блокируют приёмку и не запускают раунд доработки.

## Находки

### N1 (nit) — tests.md §3: ссылки на строки M10–M14 сдвинуты на +3

- **Требование:** DoD «Правила доказательства» п.2 — цитата упавшего теста в tests.md должна
  соответствовать доставленным байтам класса.
- **Evidence:** `tests.md:257` (M10 `:38`), `:264-266` (M11 `:84/:53`), `:273` (M12 `:124`),
  `:280` (M13 `:147`), `:287` (M14 `:159`). В доставленном
  `GenerateAnswerOutputAdapterTest.kt` падающие `assertEquals` находятся на `:35` (`:38` — пустая
  строка), `:81` и `:50`, `:121`, `:144`, `:156`. Все пять ссылок сдвинуты ровно на +3: цитаты взяты
  из прогона до последней правки тестового класса (правка 18:40, откат production 18:38/18:39,
  финальный прогон 18:42). Содержательно доказательство сохраняется — чувствительность M10–M14 к
  доставленным assertion'ам перепроверена по байтам.
- **Required change (при следующем касании):** перезапустить пять мутаций на доставленном классе или
  поправить строки на `35 / 81,50 / 121 / 144 / 156`.

### N2 (nit) — `GenerateAnswerOutputAdapterTest.kt:159-164`: один формат ожидания выражен дважды

- **Требование:** один способ выразить ожидаемые байты промпта в одном файле.
- **Evidence:** приватный `messageBlock()` вызывается только на `:50`, тогда как тот же литерал блока
  `[MESSAGE_START] … [MESSAGE_END]` написан инлайном на `:81-95` и `:121-133`. Хелпер не выводится из
  production-кода, поведение не маскирует — только дублирование выражения. Не блокер.
- **Required change:** инлайнить четыре строки на `:50` либо пропустить все блочные ожидания через
  хелпер — один способ.

## Покрытие AC (проверено по доставленным байтам)

| AC | Тест | Покрытые фразы / почему тест может упасть |
|---|---|---|
| AC-1 | `GenerateAnswerUsecaseTest.kt:30-39` | `ok`; запрос в команду; ровно один `ANSWER_MESSAGE`; `ResponseMessage(text, responseTo=исходное)`. Пустая очередь/смена event валит тест (M1). |
| AC-2 | `GenerateAnswerCommandImplTest.kt:36-45,57` | контекст запрошен для исходного `Message`; `GenerateAnswerOutputPortRequest` собран из полей контекста; ответ AI → `MessageText` (M2/M3). |
| AC-3 | `ConversationContextRestAdapterTest.kt` | `limit=20`/`before=current id` `:39-46`; `StoredMessage→ChatMessage` `:62-73`; null-текст → `""` `:83`; разделение по знаку `fromId` `:102-103`; `associateBy` `:117-118`; JSONB-массив + вложенный автор `:147-151`; одиночный объект `:166-169`. JSONB строится `ObjectMapperProvider.create().registerModule(JavaTimeModule())`, не хардкод. |
| AC-4 | `GenerateAnswerOutputAdapterTest.kt` | профили `:35-36`; история блоками `:50`; текущее + вложенные уровни/отступы `:81-97`; fallback `idN` `:121-133`; пустые профили → `""` `:144-146`; ответ AI `:156`. Строка `Текст:` уровня 2 без отступа `:91` — ровно реальные байты production (`GenerateAnswerOutputAdapter.kt:84` `trimIndent()`), это characterization, а не опечатка. |
| AC-5 | `MessageRequireAnswerEventProcessorTest.kt:38-40,47` | JSON из `FakeVoProvider.createMessage()` mapper'ом с `JavaTimeModule`; полный `Message` (включая вложенные forwarded) доходит до input port; `event()` = `MESSAGE_REQUIRE_ANSWER` (M15/M16). |

Непокрытых фраз AC-1…AC-5 нет. Проверенные и признанные чистыми классы: все 5 тестов и 4 провайдера,
production-под-тестом (`GenerateAnswerUsecase`, `GenerateAnswerCommandImpl`,
`ConversationContextRestAdapter`, `GenerateAnswerOutputAdapter`, `MessageRequireAnswerEventProcessor`,
`UserAnswerAiService`, контракты портов/команд, `VaneSearchClient`/`VaneSearchOutputAdapter`), общие
`FakeVoProvider`/`FakeModelProvider`/`FakePersistencePortProvider`/`FakePublishEventCommand`,
`TestDataFactoriesTest` (сканирует `<module>/src/testFixtures`), соседние тесты-образцы
(`MessageReceivedUsecaseInputTest`, `VkProfileInProcessAdapterTest`), `docs/development/testing.md`.

Театральность не найдена: нет ассертов на моки тестируемого, нет ассертов, которые не могут упасть
(каждый класс падал на мутации M1–M16), нет sleep/сети/порядкозависимости, нет нечитаемых снапшотов;
набор не зелёный при удалении поведения (пустые `ConcurrentLinkedQueue.single()` падают); данные —
только `create*`-фабрики, arch-гейт fresh. Негативное доказательство: 16 мутаций, 15 ссылок совпадают с
байтами, M10–M14 — сдвиг из N1.

## Capability record

Изменение не добавляет и не меняет наблюдаемого поведения (только тесты и test-конфигурация), поэтому
строки `docs/capabilities/` в этом диффе не требуются и не менялись. Существующие строки `ai-answer`
валидны: точки входа и e2e-тест на месте, ничего не удалено. Обновление AC/путей тестов в
`docs/capabilities/{ai-answer,conversation-summary,ai-tools-vane}.md`, `docs/development/testing.md`,
`AGENTS.md` и ратчет покрытия — отдельная board task `docs-and-coverage-record`, стоящая в DAG после этой
(её spec явно это фиксирует).

## Открытые риски (не блокируют)

1. **Mapper-фиделити (test-reviewer).** Тестовый mapper оставляет Jackson-дефолты
   `WRITE_DATES_AS_TIMESTAMPS=true` / `FAIL_ON_UNKNOWN_PROPERTIES=true`, тогда как Quarkus выставляет
   `false/false`; JSONB в тесте не байт-в-байт production JSONB (ISO-8601). На утверждения AC-3/AC-5 не
   влияет (обе формы парсятся). Точное соответствие — `.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)`
   в тестовом mapper.
2. **Слабые по-классовые evidence-строки.** `tests.md` §4.2 даёт `BUILD SUCCESSFUL` без числа тестов, а
   при `failOnNoDiscoveredTests = false` пустой фильтр тоже был бы зелёным. Прикрыто полным JUnit XML
   набора (18/0) и цитатами падений, но при следующем касании — добавить счётчик к по-классовым прогонам.
3. **Фундамент может протухнуть.** `FakeSummaryProvider`/`FakeVaneProvider` пока без call sites; параллельные
   задачи обязаны править их под реальные контракты, а не оставлять как есть — иначе гейт этого не заметит.
4. **Раскладка share-портов.** `FakeConversationContextPortProvider` строит Response-типы share-портов в
   модуле `ai`, тогда как `docs/development/testing.md` (Провайдеры п.2) и spec.md §6 относят share-типы к
   `:src:testing:test-fixtures`. tests.md §1.2 выбрал `ai` осознанно, потребитель один; AC-17 должен
   назвать это в testing.md, иначе документ и дерево разойдутся.
5. **Characterization-асимметрия.** `GenerateAnswerOutputAdapterTest.kt:89-91` фиксирует отсутствие
   отступа у `Текст:` уровня 2 при отступах у `[FORWARDED_BLOCK]`/`Уровень: 2` — это поведение production
   (`trimIndent()`), не дефект теста; если асимметрия признаётся багом, это отдельная кодовая задача.
