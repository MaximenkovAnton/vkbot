# Документация VK Bot

Индекс документации проекта. Код — источник истины: если документ расходится с кодом, правится документ,
а не наоборот. Конституция для агентов — [AGENTS.md](../AGENTS.md).

## Архитектура

| Документ | Что отвечает |
|---|---|
| [Обзор архитектуры](architecture/overview.md) | Стиль, слои, ADR id, runtime-топология, точки входа |
| [Модульная структура](architecture/modules.md) | Каждый модуль: ответственность, публичный API, зависимости |
| [Архитектурные паттерны](architecture/patterns.md) | Паттерны в коде с файлами и строками |
| [Поток данных](architecture/data-flow.md) | Реальные пути событий и вызовов, отказоустойчивость, ограничения |

## Разработка

| Документ | Что отвечает |
|---|---|
| [Тестирование](development/testing.md) | Стратегия, fake-объекты, интеграционные тесты, покрытие |
| [Конфигурация](development/configuration.md) | Каждый ключ конфигурации, его значение по умолчанию и эффект |
| [Деплой](development/deployment.md) | Артефакт, образы, обязательные env, миграции, откат |

## Архитектурные решения (ADR)

| # | Решение | Статус |
|---|---|---|
| [0001](adr/0001-stack-kotlin-quarkus-gradle.md) | Kotlin 2.3.0 + Gradle 9.3.0 + Quarkus 3.34.2 + JDK 21; один источник версии Quarkus | accepted |
| [0002](adr/0002-single-deployable-app-module.md) | Один деплой `:src:app`; корень — агрегатор | accepted |
| [0003](adr/0003-module-boundaries-and-layer-rules.md) | 11 модулей, правила слоёв L1–L6, CDI как composition root | accepted |
| [0004](adr/0004-in-process-ports-instead-of-loopback-rest.md) | In-process порты вместо loopback-REST; события — RabbitMQ | accepted |
| [0005](adr/0005-quality-gates-and-ci.md) | detekt/ktlint/JaCoCo-порог/ArchUnit и пять джобов CI | accepted |
| [0006](adr/0006-configuration-and-secrets.md) | Один конфиг в `:src:app`, env для секретов; дубли конфига и loopback-ключи удалены, оставшиеся мёртвые ключи — долг | accepted |

## Продуктовая карта

- [Карта возможностей](capabilities/index.md) — по строке на каждую возможность: точки входа в коде,
  общие данные, ADR. Пишется стадией `capabilities` из кода; чек `scripts/check-capabilities.sh` валит
  сборку на несуществующем пути или ADR id.

## Быстрая навигация

- Новый разработчик: [обзор архитектуры](architecture/overview.md) → [модули](architecture/modules.md) →
  [тестирование](development/testing.md).
- DevOps: [конфигурация](development/configuration.md) → [деплой](development/deployment.md).
- Архитектор: [паттерны](architecture/patterns.md) → [поток данных](architecture/data-flow.md) →
  [ADR](adr/).
