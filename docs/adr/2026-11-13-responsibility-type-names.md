# 2026-11-13: Имена типов по ответственности — запрет `*Controller`, `*Service`, `*Repository`

- **Статус:** accepted
- **Дата:** 2026-11-13
- **Supersedes:** —
- **Amends:** ADR 2026-10-28-module-boundaries-and-layer-rules — добавляет правило L7 (имена типов)
  к правилам слоёв L1–L6

## Контекст

Имена `*Controller`, `*Service` и `*Repository` называют слой, а не работу класса. Такой класс становится
местом по умолчанию для всего, что относится к теме: REST-маппинг, валидация, SQL, кеш, бизнес-правила,
публикация событий. Дальше он растёт до «свалки», тест требует поднять всё окружение, а SRP и ISP
нарушаются молча. Договорённость «один класс — одна ответственность» в ревью не держится: агент или
человек воспроизводит привычный каркас, потому что имя уже подсказывает, куда сложить остальное.

На момент решения в production-коде было 22 таких типа: 9 LangChain4j-интерфейсов `*AiService`,
12 классов persistence (`Jooq*Repository`, `PersistenceDataController`, `PersistenceService`) и
`VkFacadeController`. Запрет, который живёт только в документации, нарушается; проверяемым его делает
падающее правило сборки.

## Решение

1. **Правило L7 (ArchUnit).** В production-коде (`com.simarel.vkbot..`) запрещены типы, simple name которых
   оканчивается на `Controller`, `Service` или `Repository`. Правило — `NamingConventionsTest`
   (`src/testing/arch-tests`): проверяет классы, интерфейсы, объекты и вложенные типы всех модулей,
   входит в `./gradlew build`, а сообщение о нарушении говорит, как назвать класс вместо этого.
2. **Имя отвечает на «что делает», а не «где лежит».** `XxxUsecase`, `XxxAdapter`, `XxxCommand`,
   `XxxProcessor`, `XxxTable`, `XxxReader`, `XxxWriter`, `XxxGenerator`, `XxxFormatter`, `XxxPolicy`,
   `XxxCalculator`, `XxxMapper` — это роли с одной ответственностью; имя слоя — нет.
3. **Одна операция — один порт.** Если классу нужно имя слоя, значит одной ответственности у него нет:
   операция описывается `OutputPort<REQ, RESP>`/`InputPort<REQ, RESP>` с единственным `execute` и
   реализуется адаптером. Однооперационные порты уже дают ISP и простой fake в тесте; правило не даёт
   вернуть «репозиторий на десять методов».
4. **Существующие типы переименованы тем же изменением:**

| Было | Стало |
|---|---|
| `ProfileAiService` | `ProfileAnswerGenerator` |
| `UserAnswerAiService` | `UserAnswerGenerator` |
| `GreetingAiService` | `GreetingTextGenerator` |
| `NewsDigestAiService` | `NewsDigestGenerator` |
| `OcrAiService` | `ImageDescriber` |
| `SummarizationAiService` | `ConversationSummarizer` |
| `KnowledgeExtractionAiService` | `KnowledgeFactExtractor` |
| `ChatKnowledgeSummaryAiService` | `ChatKnowledgeSummarizer` |
| `UserKnowledgeSummaryAiService` | `UserKnowledgeSummarizer` |
| `PersistenceService` | `PersistenceResource` |
| `PersistenceDataController` | `PersistenceDataResource` |
| `VkFacadeController` | `VkProfileResource` |
| `JooqMessageRepository` | `MessageTable` |
| `JooqSummaryRepository` | `SummaryTable` |
| `JooqUserStatisticsRepository` | `UserStatisticsReader` |
| `JooqUserStatisticsWriterRepository` | `UserStatisticsWriter` |
| `JooqUserKnowledgeRepository` | `UserKnowledgeTable` |
| `JooqKnowledgeSummaryRepository` | `KnowledgeSummaryTable` |
| `JooqVkUserProfileRepository` | `VkUserProfileTable` |
| `JooqVkGroupProfileRepository` | `VkGroupProfileTable` |
| `JooqLongPollPositionRepository` | `LongPollPositionTable` |
| `JooqGreetingLogRepository` | `GreetingLogTable` |

Тестовые fake'и переименованы вместе с контрактами: `Recording*AiService` → `Recording*Generator`/
`Recording*Summarizer`/`Recording*Extractor`, `FakeUserStatisticsWriterRepository` → `FakeUserStatisticsWriter`.

## Последствия

- Новый `*Controller`/`*Service`/`*Repository` не проходит `./gradlew build`; L7 стоит рядом с L1–L6 и
  проверяется на тех же импортированных типах.
- Переименование механическое и не меняет поведения: имена файлов, импорты, test-fixtures, ссылки в
  документации и capability-карте обновлены в том же изменении; содержимое файлов не менялось, поэтому
  line-номера в docs остались валидными.
- Правило не дробит существующие классы. `UserKnowledgeTable` (615 строк) по-прежнему табличный шлюз;
  новые SQL-операции добавляются отдельными адаптерами и портами, а не в него.
- `Impl`, `Manager`, `Helper`, `Util` механически не запрещены, но дух правила на них распространяется:
  `*Impl` остаётся только у реализаций `Command`, где он уже есть.
- Проверки: `NamingConventionsTest` в `src/testing/arch-tests`, документированное правило —
  `docs/architecture/patterns.md` (раздел «Имя типа — ответственность»), конституция — `AGENTS.md`.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Оставить запрет в документации и ревью | Уже не сработало: без падающего теста каркас воспроизводится заново |
| detekt `ForbiddenClassName` | Архитектурные правила и так живут в `arch-tests` и применяются к импортированным типам всех модулей сразу; detekt добавил бы второй механизм и отдельный baseline ради того же запрета |
| Allowlist существующих классов | Список пришлось бы расширять под каждый новый «сервис», и правило стало бы формальностью |
| Разрешить `*ServiceImpl`/`*Manager` | Тот же слой под другим именем: класс снова становится свалкой, а правило обходится |
| Запретить любые технические суффиксы (`*Adapter`, `*Table`, `*Command`) | Адаптер, команда и табличный шлюз — реальные роли с одной ответственностью; их имена и есть осмысленные |
