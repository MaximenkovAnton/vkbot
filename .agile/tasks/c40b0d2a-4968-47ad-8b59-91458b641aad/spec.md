# Спецификация: «Бага github lint» — гейт detekt и скрытые находки

- **id:** c40b0d2a-4968-47ad-8b59-91458b641aad
- **Рабочая директория:** `/Users/asmaksime1/IdeaProjects/test/vk`
- **Тип:** bugfix (причина найдена и воспроизведена до плана)

## 1. Симптом

CI job `lint` (`./gradlew detekt ktlintCheck`) падает на `:src:ai:detekt` с 8 находками
(`TooGenericExceptionCaught` ×4, `FunctionNaming` ×3, `MagicNumber` ×1). Локально
`./gradlew detekt`, `./gradlew ktlintCheck` и `./gradlew build` зелёные, то есть проблемы
локально не видны и решаются только падением CI.

## 2. Диагноз (воспроизведён)

`detekt-config.yml` у 15 правил содержит `excludes: ['**/test/**', '**/androidTest/**', …]`
(примеры: `TooGenericExceptionCaught` — `detekt-config.yml:287-289`, `FunctionNaming` —
`detekt-config.yml:333-335`, `MagicNumber` — `detekt-config.yml:622-624`, `TooManyFunctions` —
`detekt-config.yml:168-170`). Detekt сопоставляет эти глобы с **абсолютным** путём файла.

- Локальный чек-аут лежит в `/Users/asmaksime1/IdeaProjects/test/vk`: сегмент `test` попадает
  под `**/test/**`, поэтому для этих правил исключены **все** файлы, включая `src/main`.
- На CI путь `/home/runner/work/vkbot/vkbot` такого сегмента не имеет — правила работают.

Доказательства:

| Проверка | Результат |
|---|---|
| `./gradlew :src:ai:detekt` локально | `BUILD SUCCESSFUL`; отчёт `src/ai/build/reports/detekt/detekt.md` — 32 kt-файла, 0 находок |
| `./gradlew detekt ktlintCheck` в копии `/tmp/vk-ci-sim` (путь без `/test/`) | `:src:ai:detekt` — те же 8 находок, что в CI; плюс `:src:app:detekt` 1, `:src:infrastructure:detekt` 1, `:src:persistence:detekt` 2, `:src:testing:test-fixtures:detekt` 4 — **итого 16** скрытых локально находок |
| detekt CLI на локальном пути с `--base-path` | 11 находок (`--base-path` не лечит) |
| detekt CLI на локальном пути с анкоренными шаблонами (`**/src/test/**`) | 19 находок = 11 baseline + 8 скрытых, то есть локаль становится равна CI |

Следствие: `src/ai/detekt-baseline.xml` (как и остальные 7 baseline) сгенерирован в этом же
каталоге, поэтому 8 «скрытых» находок `ai` в baseline не попали. В baseline попали только
правила без широкого исключения (`LongMethod`, `LongParameterList`, `MaxLineLength`,
`UseCheckOrError`).

`ktlintCheck` от пути не зависит: `.editorconfig` не содержит исключений по каталогам.

### Полный список скрытых находок (16)

| Модуль | Файл:строка | Правило |
|---|---|---|
| `src:ai` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/GenerateSummaryCommandImpl.kt:59` | TooGenericExceptionCaught |
| `src:ai` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/summary/ProcessSummaryCommandImpl.kt:97` | TooGenericExceptionCaught |
| `src:ai` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/vane/VaneSearchCommand.kt:64` | TooGenericExceptionCaught |
| `src:ai` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/summarization/SummarizationAiAdapter.kt:29` | TooGenericExceptionCaught |
| `src:ai` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapter.kt:16,22,28` | FunctionNaming ×3 |
| `src:ai` | `src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/TransientChatMemoryProvider.kt:15` | MagicNumber |
| `src:app` | `src/app/src/main/kotlin/com/simarel/vkbot/app/Application.kt:10` | SpreadOperator |
| `src:infrastructure` | `src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt:79` | TooGenericExceptionCaught |
| `src:persistence` | `src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/command/fetchuserprofile/FetchUserProfileCommandImpl.kt:72,89` | TooGenericExceptionCaught ×2 |
| `src:testing:test-fixtures` | `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt:14` | TooManyFunctions |
| `src:testing:test-fixtures` | `src/testing/test-fixtures/src/main/kotlin/com/simarel/vkbot/testfixtures/domain/FakeVoProvider.kt:17,18,19` | MagicNumber ×3 |

## 3. Цель и не-цели

**Цель:**

1. Устранить все 16 находок, не меняя поведение приложения.
2. Сделать гейт detekt независимым от пути чек-аута: локально видны те же находки, что в CI,
   и регрессия этого класса ловится тестом-сторожем.

**Не-цели:** апгрейд detekt/ktlint/Java; удаление или пересборка существующих baseline (кроме
нового baseline `test-fixtures`); написание тестов на `ai` (покрытие — отдельная задача);
изменение состава CI-джобов; рефакторинг кода вне 16 находок; переименование @Tool-функций.

## 4. Принятые решения (согласованы с человеком)

| Вопрос | Решение |
|---|---|
| 7 намеренных `catch (Exception)` | точечный `@Suppress("TooGenericExceptionCaught")` на функции, поведение не меняется |
| Имена `search_web` / `search_academic` / `search_discussions` | оставить; расширить существующий `@file:Suppress` на detekt-правило `FunctionNaming` |
| Регрессионный сторож | да, тест в `:src:testing:arch-tests` |
| `FakeVoProvider` TooManyFunctions | baseline модуля `src/testing/test-fixtures` по конвенции ADR 0005 |

## 5. Acceptance criteria (EARS)

- **AC-1.** КОГДА `./gradlew detekt ktlintCheck` выполняется в чек-ауте, путь которого не содержит
  каталога с именем `test` (CI-эквивалент, `/tmp/vk-lint-check`), СИСТЕМА ДОЛЖНА завершиться с
  кодом 0 без находок.
  *Тест:* `rsync`-копия репозитория в `/tmp/vk-lint-check` + `./gradlew detekt ktlintCheck`; вывод
  и exit code — в артефакте `fix.md`.
- **AC-2.** КОГДА `./gradlew detekt` выполняется из `/Users/asmaksime1/IdeaProjects/test/vk`,
  СИСТЕМА ДОЛЖНА применять к main-исходникам тот же набор правил, что и из нейтрального пути.
  *Тест:* `DetektExcludesPathTest` (падает на текущем конфиге, проходит после анкоринга) и прогон
  `./gradlew detekt` в локальном и нейтральном путях с одинаковым результатом.
- **AC-3.** КОГДА глоб `excludes` в `detekt-config.yml` матчит main-исходник чек-аута, лежащего в
  каталоге с именем `test`, СИСТЕМА ДОЛЖНА упасть на `:src:testing:arch-tests:test` и назвать
  виновный глоб.
  *Тест:* негативный прогон `./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*"`
  с временно возвращённым `'**/test/**'` → FAILED, после отката → PASSED.
- **AC-4.** КОГДА после правок запускается полный набор тестов, СИСТЕМА ДОЛЖНА сохранить все
  существующие тесты зелёными.
  *Тест:* `./gradlew test` (и `./gradlew build`) — exit 0.
- **AC-5.** КОГДА изменения влиты, СИСТЕМА ДОЛЖНА сохранять карту возможностей и ADR в
  соответствии с кодом.
  *Тест:* `./scripts/check-capabilities.sh` и `./gradlew capabilitiesCheck` — exit 0.
- **AC-6.** КОГДА изменения влиты, СИСТЕМА ДОЛЖНА сохранять имена инструментов LLM
  `search_web`, `search_academic`, `search_discussions`.
  *Тест:* `grep -n "fun search_" src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/tool/VaneSearchToolAdapter.kt`
  возвращает три строки; дифф не меняет имена.

## 6. План: две задачи доски quick-fix, последовательно

Порядок важен: пока конфиг не исправлен, локальный detekt слеп, поэтому сначала чинятся находки и
проверяются в CI-эквивалентной копии (T2), затем правится конфиг и добавляется сторож (T1), после
чего зелёный локальный прогон уже что-то доказывает.

| id | Что делает | Зависит от | Проверка |
|---|---|---|---|
| `resolve-hidden-findings` | чинит 16 находок: 7 подавлений `TooGenericExceptionCaught`, `SpreadOperator`, `FunctionNaming`, 4 `MagicNumber` константами, baseline `test-fixtures`; правит затронутые docs | — | `detekt ktlintCheck` и `build` в нейтральной копии |
| `make-lint-path-independent` | анкорит тестовые `excludes` в `detekt-config.yml` на `**/src/test/**`, добавляет `DetektExcludesPathTest`, обновляет ADR 0005 и AGENTS.md | `resolve-hidden-findings` | `detekt ktlintCheck` локально и в нейтральной копии, негативный прогон сторожа |

## 7. Влияние, контракты, риски

- **Поведение:** не меняется. Все правки — подавления правила, именованные константы (значения
  те же) и baseline. Единственная видимая снаружи сущность — имена @Tool-функций, они остаются.
- **Данные/клиенты:** нет изменений схемы, событий, REST-контрактов, конфигурации приложения.
- **Безопасность:** роста поверхности нет; generic-catch остаются там, где их смысл — «любая
  ошибка»: retry-лестница, mark-FAILED, деградация поиска, необязательный fetch профилей.
- **Риск 1:** baseline `test-fixtures` нельзя генерировать локально (detekt слеп) — только в
  нейтральной копии, иначе baseline получится пустым/неполным.
- **Риск 2:** добавление строк-аннотаций сдвигает номера строк, но не ломает существующие
  baseline-подписи — проверено: после вставки `@Suppress` в `GenerateSummaryCommandImpl` 8 находок
  `ai` уменьшились до 7, а 11 baseline-находок остались подавленными.
- **Риск 3:** CI-эквивалентная копия — обязательный оракул для T2; локальный зелёный прогон до
  T1 ничего не доказывает.
- **Необратимость/деплой/стоимость:** нет; изменения только в исходниках, конфиге линта, тесте и
  документации, время прогона гейтов не меняется.

## 8. Команды проверки

```bash
# CI-эквивалент (путь без сегмента "test")
rsync -a --exclude build --exclude .git --exclude .gradle --exclude .idea \
  --exclude .ast-index --exclude .kotlin --exclude .agile --exclude .DS_Store \
  ./ /tmp/vk-lint-check/
(cd /tmp/vk-lint-check && ./gradlew detekt ktlintCheck && ./gradlew build)

# локально (путь содержит /test/)
./gradlew detekt ktlintCheck
./gradlew build

# сторож: позитивный и негативный прогон
./gradlew :src:testing:arch-tests:test --tests "*DetektExcludesPathTest*"

# документы
./scripts/check-capabilities.sh
```

## 9. Вне скоупа

- Повышение порога покрытия и тесты модуля `ai`.
- Пересборка остальных 8 baseline и снятие существующего долга.
- Апгрейд detekt/ktlint или переход на Spotless.
- Изменение CI workflow: джобы остаются как есть, чинится причина, а не падение билда.
