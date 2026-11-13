# 2026-10-28: Стек и версии — Kotlin 2.3.0, Gradle 9.3.0, Quarkus 3.40.1.2, JDK 21

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

Проект `vkbot` — Kotlin/Quarkus-монолит. Версии уже зафиксированы сборкой, но в трёх местах расходились:
плавающая версия `jackson-module-kotlin:latest` в четырёх модулях и BOM Quarkus, продублированный числом
в десяти модульных build-файлах. Локально и в CI стоит JDK 21; профиль проекта просит «текущий JDK LTS».

Источники истины после стабилизации:

| Слой | Версия | Где зафиксирована |
|---|---|---|
| JDK | 21 (Temurin 21.0.5) | `kotlin { jvmToolchain(21) }` во всех 12 модулях; `.sdkmanrc`; [`.github/workflows/build-and-test.yml:16-20`](../../.github/workflows/build-and-test.yml) |
| Gradle | 9.3.0 (wrapper, Kotlin DSL) | [`gradle/wrapper/gradle-wrapper.properties:3`](../../gradle/wrapper/gradle-wrapper.properties) |
| Kotlin | 2.3.0 | [`settings.gradle.kts:14`](../../settings.gradle.kts) |
| Quarkus | 3.40.1.2 (`quarkus-bom`) | [`gradle.properties:3-7`](../../gradle.properties); единственный `enforcedPlatform` в [`build.gradle.kts:44-51`](../../build.gradle.kts) |
| LangChain4j | `quarkus-langchain4j` 1.15.0 (LangChain4j 1.23.0) | [`gradle.properties:8`](../../gradle.properties); [`src/app/build.gradle.kts:40`](../../src/app/build.gradle.kts) |
| Хранилище | PostgreSQL + Liquibase, jOOQ 3.19.10 | [`src/persistence/build.gradle.kts:41,47,55`](../../src/persistence/build.gradle.kts) |
| Брокер | RabbitMQ (`quarkus-messaging-rabbitmq`) | [`src/infrastructure/build.gradle.kts:18-20`](../../src/infrastructure/build.gradle.kts) |
| Тесты | JUnit 5, ArchUnit 1.3.0, WireMock 3.9.1, Awaitility 4.2.0 | [`src/testing/arch-tests/build.gradle.kts:11`](../../src/testing/arch-tests/build.gradle.kts), [`src/app/build.gradle.kts:46-59`](../../src/app/build.gradle.kts) |
| Линт / формат | detekt 1.23.8, ktlint Gradle plugin 13.1.0 | [`gradle.properties:9-10`](../../gradle.properties), [`build.gradle.kts:5-6`](../../build.gradle.kts) |
| Покрытие | JaCoCo 0.8.14 (плагин Gradle) | отчёт `build/reports/jacoco/coverageReport/html/index.html` («Created with JaCoCo 0.8.14») |

Версии платформы подняты отдельной задачей 2026-11-07 — Quarkus `3.40.1.1` и `quarkus-langchain4j` `1.15.0`
(LangChain4j `1.23.0`). Причина — decision-модели LangChain4j: decision-API есть в `quarkus-langchain4j`
`1.15.0`, а он требует Quarkus ≥ 3.40. Источник версий прежний — [`gradle.properties`](../../gradle.properties); полный
`./gradlew build` после апгрейда зелёный, код не менялся.

**Обновление 2026-11-13:** патч-релиз Quarkus `3.40.1.1` → `3.40.1.2`; `quarkusPluginVersion` и
`quarkusPlatformVersion` подняты вместе, `quarkus-langchain4j` остаётся `1.15.0`, код не менялся. Полный
`./gradlew build` после апгрейда зелёный. Строка выше — история решения 2026-11-07, а не текущая версия.

## Решение

Берём текущий набор версий как есть и делаем его воспроизводимым:

- Один `enforcedPlatform(quarkus-bom)` на все подпроекты — только [`build.gradle.kts:44-51`](../../build.gradle.kts); строки BOM
  удалены из модульных build-файлов. Второй точки изменения версии Quarkus нет.
- Плавающих версий нет: `jackson-module-kotlin` без `:latest` (версия из BOM) во всех четырёх модулях;
  `grep -rn ":latest"` по build-файлам пуст.
- Version catalog `gradle/libs.versions.toml` в этом проходе не вводится. Тот же эффект даёт централизация
  в [`gradle.properties`](../../gradle.properties) + конвенционный блок; каталог потребовал бы переписать 12 build-файлов без выгоды
  для целей прохода.
- JDK 21 остаётся осознанным отклонением от «текущего LTS»: локально и в CI стоит 21, Quarkus 3.40.1.2
  на более новом JDK в этом проходе не проверялся. Условие пересмотра: отдельная задача «апгрейд JDK»,
  где сначала проверяется совместимость, затем меняются toolchain, CI и `.sdkmanrc` одновременно.

## Последствия

- Сборка воспроизводима: все версии разрешаются из [`gradle.properties`](../../gradle.properties) и wrapper.
- Обновление Quarkus — одна правка [`gradle.properties`](../../gradle.properties); обновление Jackson — из BOM.
- JDK 21 — предыдущий LTS; при выходе обновления Quarkus с поддержкой более нового JDK нужна отдельная
  задача с прогоном всех гейтов (ADR 2026-10-28-quality-gates-and-ci).
- `org.gradle.jvmargs = --add-opens=java.base/java.lang=ALL-UNNAMED` ([`gradle.properties:11`](../../gradle.properties)) сохранён как
  рабочее требование текущего окружения.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Maven | Gradle-сборка существует и работает; смена сборщика — churn без выгоды для DoD |
| Spring Boot / Ktor вместо Quarkus | Миграция фреймворка не входит в задачу «подготовить проект» и ломает весь код |
| Апгрейд JDK/Kotlin сейчас | Непроверенная совместимость; риск сломать зелёные гейты. Quarkus и LangChain4j из этой альтернативы подняты позже — отдельной задачей с зелёным полным build («Контекст») |
| Version catalog `libs.versions.toml` | Переписывание 12 файлов; тот же результат уже даёт один `enforcedPlatform` |
| `jackson-module-kotlin:latest` | Сборка невоспроизводима: результат зависит от даты прогона |
