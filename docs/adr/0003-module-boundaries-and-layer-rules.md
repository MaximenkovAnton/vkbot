# ADR 0003: Границы модулей и правила слоёв

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

Проект — гексагональный монолит в одном деплое (ADR 0002). Границы двух уровней:

1. **Модули Gradle** — горизонтальные бизнес-модули вокруг общего ядра `share`.
2. **Слои внутри модуля** — `domain`, `port/input`, `port/output`, `usecase`, `command`, `adapter/input`,
   `adapter/output`, `config`.

До прохода границы были только декларацией: `GradleDependenciesTest` не мог упасть (фильтр
`!it.path.contains("build")` отбрасывал все build-файлы), правило ArchUnit для `app` было вакуумным
(пакет `app..` не существовал), `domain` persistence импортировал jOOQ, а use case — `io.quarkus.logging.Log`.
Каркас и детали реализации протекали в бизнес-слой.

## Решение

### 11 модулей

`share`, `receiver`, `persistence`, `processor`, `ai`, `vk-facade`, `infrastructure`, `app`,
`testing:test-common`, `testing:test-fixtures`, `testing:arch-tests` (`settings.gradle.kts:22-32`).

Разрешённый граф production-зависимостей (allowlist в `GradleDependenciesTest.kt:35-65`):

```
share            -> (никого)
receiver         -> share
processor        -> share
ai               -> share
persistence      -> share
vk-facade        -> share
infrastructure   -> share
app              -> все 6 бизнес-модулей + share
testing/test-fixtures -> share, vk-facade          (только тесты)
testing/test-common   -> (никого)
testing/arch-tests    -> все модули + app          (только тесты)
```

`share` — общее ядро: контракты (`Port`, `Command`, `EventProcessor`), доменные модели и value objects,
события. От него зависят все, он — ни от кого.

### Почему модули, а не пакеты

Gradle-модуль делает неправильный импорт **ошибкой компиляции**, а не находкой ревью: `ai` физически не
видит `persistence` (ребро `ai -> persistence` удалено). Это дешевле в обслуживании, чем architecture
test, и не создаёт деплоев на фичу — деплой один.

### Правила слоёв (ArchUnit, один `@Test` на правило)

`src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/LayerDependenciesTest.kt`:

| # | Правило | Что запрещено |
|---|---|---|
| L1 | `domain` чист | `..domain..` не зависит от `io.quarkus`, `jakarta`, `org.jooq`, `com.fasterxml`, `io.smallrye`, `org.eclipse`, `dev.langchain4j` и от слоёв `adapter`, `usecase`, `command`, `port`, `config` (`LayerDependenciesTest.kt:51-60`) |
| L2 | `port` — только контракты | `..port..` не зависит от `adapter`, `usecase`, `command`, `config` и тех же фреймворков (`:62-73`) |
| L3 | `usecase`/`command` не знают адаптеров и транспорта | нет `adapter.*`, `io.quarkus`, `org.jooq`, `com.fasterxml`, `jakarta.ws.rs`, `jakarta.json`, `dev.langchain4j`, `io.smallrye` (`:75-94`); DI-аннотации `jakarta.inject`/`jakarta.enterprise`/`jakarta.transaction`/`org.eclipse.microprofile.config.inject` разрешены |
| L4 | входной адаптер зовёт только порт | `..adapter.input..` не зависит от `command`, `usecase`, `adapter.output` (`:96-105`) |
| L5 | выходной адаптер не ходит во входной и в usecase | `..adapter.output..` не зависит от `adapter.input`, `usecase` (`:107-116`) |
| L6 | `share` — ядро | `share` не зависит от пакетов других модулей (`ModuleDependenciesTest.kt:47-58`) |

Плюс модульные правила: каждый бизнес-модуль зависит только от `share` (`ModuleDependenciesTest.kt:60-124`)
и build-граф соответствует allowlist (`GradleDependenciesTest.kt:82-112`).

### Composition root

`@QuarkusMain` — `:src:app`. Связывание — CDI: реализации помечены `@ApplicationScoped`/`@Singleton`,
порты `share` инжектятся по интерфейсу. Явные `@Produces` — только там, где нужна ручная сборка:
`receiver/config/UsecaseConfiguration.kt:12-25` (`ReceiveMessageUsecase` с двумя ответами),
`receiver/config/ConstantConfiguration.kt:9-15` (`ConfirmationCode`), `persistence/config/JooqConfig.kt:11-20`
(`DSLContext`), `infrastructure/config/MapperConfig.kt:9-18` (Jackson Kotlin module).

## Последствия

- Нарушение направления зависимости валит `./gradlew build` (L1–L6 + allowlist входят в `check`).
- Новый модуль обязан войти в allowlist `GradleDependenciesTest` и в `ModuleDependenciesTest`.
- `domain` persistence — это entity без фреймворка; jOOQ-типы живут в `adapter/output/persistence/jooq`
  (`Tables.kt`, `Jooq*Repository.kt`). Сериализация `forwardedMessages` ушла за порт
  `persistence/port/output/ForwardedMessagesSerializationPort.kt`, реализация —
  `persistence/adapter/output/json/JacksonForwardedMessagesAdapter.kt`.
- Логирование в бизнес-слое — `java.lang.System.Logger` (JDK), а не `io.quarkus.logging.Log`
  (`ReceiveMessageUsecase.kt:15`, `ProcessSummaryCommandImpl.kt:26`, `FetchUserProfileCommandImpl.kt:24`);
  Quarkus-логирование осталось только в адаптерах и инфраструктуре.
- Слои остаются пакетами внутри модуля: семь отдельных layer-модулей умножили бы build-файлы без компиляторной
  выгоды сверх уже существующей модульной границы.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Семь layer-модулей (`domain`, `port.in`, …) | 11 модулей уже дают компиляторную границу для фич; layer-модули дали бы 40+ build-файлов ради того же эффекта |
| Модуль или деплой на фичу | Нет требования независимого деплоя/масштабирования; один деплой (ADR 0002) |
| Явные `@Produces` на каждый порт | Десятки файлов без функциональной выгоды; правило «бизнес-слой видит только порты» уже выполняется |
| Оставить `domain` с jOOQ и `Log` в use case | L1/L3 валятся, и каркас снова становится архитектурой |
