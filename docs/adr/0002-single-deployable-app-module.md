# ADR 0002: Один деплой — модуль `:src:app`, корень — агрегатор

- **Статус:** accepted
- **Дата:** 2026-10-28
- **Supersedes:** —

## Контекст

До прохода применялись **два** Quarkus-раннера: корневой проект `vkbot` (`io.quarkus` в
`build.gradle.kts`) и `:src:app` (`src/app/build.gradle.kts:3`). Собирались два `quarkus-app`, при этом
корневой не содержал классов приложения; `quarkusDev` запускал обе задачи. Dockerfile'ы расходились:
JVM-образ копировал артефакт `src/app/build`, native/legacy — корневой `build/`. Канонический деплой не был
зафиксирован ни кодом, ни документами.

`@QuarkusMain` уже лежит в `src/app` (`src/app/src/main/kotlin/com/simarel/vkbot/app/Application.kt:6-12`),
и `:src:app` зависит от всех бизнес-модулей (`src/app/build.gradle.kts:16-22`).

## Решение

Единственный деплой — **`:src:app`**. Корневой проект — агрегатор и место конвенций:

- корень применяет `base`, `jacoco` и плагины с `apply false` (`build.gradle.kts:1-8`), `io.quarkus` в
  корне отсутствует; задача `:quarkusDev`/`:quarkusBuild` не создаётся (проверено `./gradlew tasks --all`).
- deployable-расширения собраны в `src/app/build.gradle.kts:25-36` (REST, REST client, health, OTel/micrometer,
  observability devservices, logging-json, config-yaml, info).
- артефакт деплоя — `src/app/build/quarkus-app/quarkus-run.jar`; корневого `build/quarkus-app/` после сборки нет.
- все четыре Dockerfile'а копируют артефакт из `src/app/build`: `src/docker/Dockerfile.jvm:87-90`,
  `Dockerfile.legacy-jar:86-87`, `Dockerfile.native:24`, `Dockerfile.native-micro:27`.
- dev-режим — `./gradlew :src:app:quarkusDev` (задача существует; dry-run перечисляет задачи модуля).
- CI-джоб `build` проверяет именно это: `test -f src/app/build/quarkus-app/quarkus-run.jar` и
  `test ! -e build/quarkus-app` (`.github/workflows/build-and-test.yml:23-27`).

## Последствия

- Один артефакт, один dev-режим, один набор Dockerfile'ов.
- Composition root — CDI Quarkus в `:src:app`: ArC валидирует injection points на этапе сборки, поэтому
  несвязанный порт (in-process адаптер без реализации) валит `./gradlew build`, а не прод.
- `./gradlew build` собирает все 11 модулей (`BUILD SUCCESSFUL`, 167 задач), включая тесты, линт и порог
  покрытия (ADR 0005); smoke: `BUILD SUCCESSFUL` + `src/app/build/quarkus-app/quarkus-run.jar` существует,
  корневого `build/quarkus-app` нет.
- Native/legacy-образы не собираются в CI (ADR 0005); `.dockerignore` в текущем виде допускает только
  `src/app/build/quarkus-app/*`, поэтому для native/legacy-сборок его белый список нужно расширить — это
  известное ограничение, зафиксированное в `docs/development/deployment.md`.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Оставить два раннера и описать оба | Два артефакта, два dev-режима, native/legacy-образы из другого источника; скрытый дефект навсегда |
| Сделать деплоем корень | `@QuarkusMain` и все зависимости уже в `:src:app`; перенос — churn без выгоды |
| Отдельный деплой на модуль | Нет требования независимого деплоя/масштабирования; см. ADR 0003 |
