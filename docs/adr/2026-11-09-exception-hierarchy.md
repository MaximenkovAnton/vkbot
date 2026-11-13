# 2026-11-09: Единая иерархия исключений и запрет `require`

- **Статус:** accepted
- **Дата:** 2026-11-09
- **Supersedes:** —

## Контекст

Production-код бросал `IllegalArgumentException`, `IllegalStateException` и `UnsupportedOperationException`,
а собственные классы `VkApiException`, `VkEventPublishException` и `VkLongPollServerException` наследовали
`RuntimeException` напрямую. `ValidationException`, задуманная как доменная ошибка валидации, тоже
наследовалась от `RuntimeException`, а не от `VkBotAppException`. Инварианты value objects проверялись
`require { ... }`: Kotlin инлайнит его в `throw IllegalArgumentException(...)`, поэтому по типу ошибки
нельзя ни отличить одну проверку от другой, ни точечно перехватить сбой, ни понять, какой объект и
какое значение не прошли проверку.

## Решение

- **Одна база.** В production-коде бросаются только `VkBotAppException` и его наследники. База хранит
  `message` и `Throwable`-cause и требует реализовать `status(): ExceptionStatus`; для отсутствующих
  сущностей добавлен статус `NOT_FOUND`.
- **Иерархия валидации.** `ValidationException` — наследник `VkBotAppException` со статусом
  `VALIDATION_FAILED`. Ошибка строится от общего к конкретному:
  `VkBotAppException → ValidationException → DecisionAnalysisValidationException →
  DecisionAnalysisSpamProbabilityValidationException`. Промежуточный класс — на value object или
  доменную модель, листовой — на конкретную проверку; лист бросается из `init` через
  `if (!invariant) throw ...`, а не через `require`.
- **Специфичные ошибки адаптеров.** Внешние и конфигурационные сбои получают свой класс:
  `SummaryGenerationException` и `VaneProviderNotConfiguredException` (`ai`),
  `DecisionModelResponseException` (`processor`), `SummaryNotFoundException` (`persistence`),
  `MessageEmitterNotConfiguredException` (`infrastructure`), `VkApiException`, `VkEventPublishException`,
  `VkLongPollServerException`. Придуманные `IllegalArgumentException`-cause удалены.
- **Архитектурные тесты.** `ExceptionHandlingTest` в [`src/testing/arch-tests`](../../src/testing/arch-tests):
  1. ArchUnit-правило по конструкторам `Throwable` в импортированных main-классах: разрешены
     наследники `VkBotAppException`, super-вызовы `RuntimeException`/`Exception`/`Throwable` внутри
     самой иерархии и исключения, которые генерирует Kotlin (`NoWhenBranchMatchedException`,
     `UnsupportedOperationException` в `$default`-методах). Тестовые классы исключены: fake-объекты
     намеренно бросают generic-исключения, чтобы воспроизводить отказы.
  2. Скан `src/*/src/main/kotlin` на `require(`/`requireNotNull(`; находка валит сборку с файлом,
     строкой и текстом проверки.
- **Границы.** Правило ловит создание исключений — `throw X(...)`, `?: throw X(...)` и инлайненный
  `require`/`check`/`error`, — но не `throw e`. Проброс внешнего исключения остаётся ответственностью
  адаптера: на своей границе он оборачивает отказ в `VkBotAppException`.

## Последствия

- Новый инвариант стоит двух классов (промежуточный и листовой) и строки `throw`; взамен тип ошибки
  читается без разбора сообщения, а `catch` можно делать по конкретному листу.
- Список компиляторных исключений в правиле узкий: новый сгенерированный тип упадёт на правиле и
  потребует осознанного решения.
- `require` и `requireNotNull` в `src/main` больше не пройдут сборку; в тестах и `testFixtures` они не
  запрещены.
