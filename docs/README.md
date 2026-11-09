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
| [0001](adr/0001-stack-kotlin-quarkus-gradle.md) | Kotlin 2.3.0 + Gradle 9.3.0 + Quarkus 3.40.1.1 + JDK 21; один источник версии Quarkus | accepted |
| [0002](adr/0002-single-deployable-app-module.md) | Один деплой `:src:app`; корень — агрегатор | accepted |
| [0003](adr/0003-module-boundaries-and-layer-rules.md) | 12 модулей, правила слоёв L1–L6, CDI как composition root | accepted |
| [0004](adr/0004-in-process-ports-instead-of-loopback-rest.md) | In-process порты вместо loopback-REST; события — RabbitMQ | accepted |
| [0005](adr/0005-quality-gates-and-ci.md) | detekt/ktlint/JaCoCo-порог/ArchUnit и пять джобов CI | accepted |
| [0006](adr/0006-configuration-and-secrets.md) | Один конфиг в `:src:app`, env для секретов; дубли конфига и loopback-ключи удалены, оставшиеся мёртвые ключи — долг | accepted |
| [0007](adr/0007-llm-provider-vllm-openai.md) | LLM-провайдер — vLLM через OpenAI-совместимый API (`quarkus-langchain4j-openai`), env `AI_BASE_URL`/`AI_CHAT_MODEL`/`AI_API_KEY` | accepted |
| [0008](adr/0008-bots-long-poll-transport.md) | Приём событий VK — Bots Long Poll (`groups.getLongPollServer` + `a_check`), позиция `ts` в PostgreSQL; Callback, `vk.secret`/`vk.confirmation-code` удалены | accepted |
| [0009](adr/0009-daily-greetings.md) | Ежедневные поздравления по cron-расписанию Quarkus (`greetings.cron`/`greetings.timezone`), отдельное сообщение на праздник и одно на именинников чата, at-most-once через `greeting_log`, шаблон-фолбэк, известные ограничения | accepted |
| [0010](adr/0010-decision-model-langchain4j.md) | Клиент decision-модели — библиотечный `DecisionModel` из `quarkus-langchain4j-ollama`, System One API `POST /v1/systemone`, нормализация и типизированный разбор; второй LangChain4j-провайдер только для decision, без вызова из runtime | accepted |
| [0011](adr/0011-exception-hierarchy.md) | Единая иерархия исключений: в production-коде бросаются только `VkBotAppException` и наследники, валидация строится цепочкой от value object к конкретной проверке, `require`/`requireNotNull` запрещены архитектурными тестами | accepted |

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
