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

Формат имени и заголовка: `YYYY-MM-DD-<тема>.md` и `YYYY-MM-DD: тема`. Числовых id у ADR нет:
дата сохраняет порядок, тема делает имя уникальным, поэтому параллельные задачи не резервируют
номера и не конфликтуют на файлах. Старые номера 0001–0014 заменены этим форматом; переписка
«номер → дата-тема» — внизу раздела.

| Дата | Решение | Статус |
|---|---|---|
| [2026-10-28](adr/2026-10-28-stack-kotlin-quarkus-gradle.md) | Kotlin 2.3.0 + Gradle 9.3.0 + Quarkus 3.40.1.1 + JDK 21; один источник версии Quarkus | accepted |
| [2026-10-28](adr/2026-10-28-single-deployable-app-module.md) | Один деплой `:src:app`; корень — агрегатор | accepted |
| [2026-10-28](adr/2026-10-28-module-boundaries-and-layer-rules.md) | 12 модулей, правила слоёв L1–L6, CDI как composition root | accepted |
| [2026-10-28](adr/2026-10-28-in-process-ports-instead-of-loopback-rest.md) | In-process порты вместо loopback-REST; события — RabbitMQ | accepted |
| [2026-10-28](adr/2026-10-28-quality-gates-and-ci.md) | detekt/ktlint/JaCoCo-порог/ArchUnit и пять джобов CI | accepted |
| [2026-10-28](adr/2026-10-28-configuration-and-secrets.md) | Один конфиг в `:src:app`, env для секретов; дубли конфига и loopback-ключи удалены, оставшиеся мёртвые ключи — долг | accepted |
| [2026-11-05](adr/2026-11-05-llm-provider-vllm-openai.md) | LLM-провайдер — vLLM через OpenAI-совместимый API (`quarkus-langchain4j-openai`), env `AI_BASE_URL`/`AI_CHAT_MODEL`/`AI_API_KEY` | accepted |
| [2026-11-05](adr/2026-11-05-bots-long-poll-transport.md) | Приём событий VK — Bots Long Poll (`groups.getLongPollServer` + `a_check`), позиция `ts` в PostgreSQL; Callback, `vk.secret`/`vk.confirmation-code` удалены | accepted |
| [2026-11-07](adr/2026-11-07-daily-greetings.md) | Ежедневные поздравления по cron-расписанию Quarkus (`greetings.cron`/`greetings.timezone`), отдельное сообщение на праздник и одно на именинников чата, at-most-once через `greeting_log`, шаблон-фолбэк, известные ограничения | accepted |
| [2026-11-08](adr/2026-11-08-decision-model-langchain4j.md) | Клиент decision-модели — библиотечный `DecisionModel` из `quarkus-langchain4j-ollama`, System One API `POST /v1/systemone`, нормализация и типизированный разбор; второй LangChain4j-провайдер только для decision (клиент подключён ADR 2026-11-09-decision-runtime-spam-policy) | accepted |
| [2026-11-09](adr/2026-11-09-exception-hierarchy.md) | Единая иерархия исключений: в production-коде бросаются только `VkBotAppException` и наследники, валидация строится цепочкой от value object к конкретной проверке, `require`/`requireNotNull` запрещены архитектурными тестами | accepted |
| [2026-11-09](adr/2026-11-09-decision-runtime-spam-policy.md) | Decision-модель вызывается из runtime: спам-уведомление до `requiredAnswer` (порог `decision.spam-threshold`, kill-switch `decision.spam-reply.enabled`, fail-open WARNING без PII), третий вопрос `intent` (`help`/`news`/`general`) в том же `decide`; маршрутизация интентов доставлена в `answer-decision` | accepted |
| [2026-11-10](adr/2026-11-10-news-digest.md) | «Что нового»: консюмер `NEWS_DIGEST_REQUESTED` в `:src:ai`, период из текста запроса (явный срок > 7 суток — отказ, без срока — от последнего сообщения автора), три read-only in-process порта к `messages`/`summaries`, LLM-сводка с тремя попытками и фолбэком, один `ANSWER_MESSAGE` публикует команда; в `summaries` не пишет | accepted |
| [2026-11-10](adr/2026-11-10-decision-jailbreak-detection.md) | Детект попытки взлома ИИ четвёртым вопросом `jailbreak` в том же `decide` (порядок `spam`, `class`, `intent`, `jailbreak`), отдельные `decision.jailbreak-threshold`/`decision.jailbreak-reply.enabled`, приоритет спама, fail-open; `Amends` ADR 2026-11-09-decision-runtime-spam-policy (состав/порядок вопросов; вызов спам/jailbreak-этапа и его PII-egress останавливают только оба ключа `false`, интент-вызов после `requiredAnswer=true` не гасится) | accepted |
| [2026-11-10](adr/2026-11-10-jailbreak-rubric-and-threshold.md) | Доработка фильтра взлома ИИ: рубрика из 24 формулировок (включая «что ты за модель», промпт, создателя, обучение, обход ограничений) и дефолт `decision.jailbreak-threshold` 0.85; `Amends` ADR 2026-11-10-decision-jailbreak-detection | accepted |
| [2026-11-10](adr/2026-11-10-jailbreak-false-positives.md) | Устранение ложных срабатываний jailbreak на бытовых просьбах: рубрика перечисляет дословные сообщения вместо инфинитивных требований, удалено «сделать другое», добавлен отрицательный пример для бытовых просьб; порог `0.85` и область (все сообщения чата) не меняются; `Amends` ADR 2026-11-10-jailbreak-rubric-and-threshold | accepted |
| [2026-11-10](adr/2026-11-10-vk-profile-history-and-statistics.md) | Полный профиль VK одним `users.get` (все поля, кроме фото/аватарки) в JSONB `profile_data`; история изменений `vk_user_profile_history` как JSONB-diff `{field: {old, new}}`; статистика автора (число сообщений, средняя длина, чаты, дни/часы) считается на чтении из `messages` в зоне `statistics.timezone`; REST `/persistence/statistics/users/{id}` и `/persistence/profiles/users/{id}/history` | accepted |
| [2026-11-10](adr/2026-11-10-vk-profile-human-details.md) | Профиль VK в человеческих значениях: один JSONB `profile_details` (русские подписи enum, title города/страны, объекты без VK-id, без пустых строк и волатильных полей; `USER_FIELDS` 54 → 35) вместо сырого `profile_data`; `ProfileFieldsMerger` сливает `details` и diff-ит typed-колонки и `details` по именам ключей (`birth_date`); changeset 008 add→purge→drop; `Amends` ADR 2026-11-10-vk-profile-history-and-statistics | accepted |
| [2026-11-10](adr/2026-11-10-user-statistics-materialization.md) | Статистика автора материализуется при сохранении сообщения: три таблицы `user_statistics`/`user_chat_statistics`/`user_hour_statistics`, три upsert'а в транзакции сообщения, UTC-бакеты конвертируются в `statistics.timezone` на чтении; `009-create-user-statistics-tables` (только DDL) и идемпотентный `backfill-user-statistics.sql` (остановка писателя, `ACCESS EXCLUSIVE` + `lock_timeout`, `ANALYZE`); REST-формат не меняется; `Amends` ADR 2026-11-10-vk-profile-history-and-statistics | accepted |
| [2026-11-10](adr/2026-11-10-profile-dossier.md) | «Профиль»: консюмер `PROFILE_REQUESTED` в `:src:ai`, детерминированный резолв адресата (mention → пересылка → «обо мне» → имя среди авторов чата), досье из сохранённого профиля VK, материализованной статистики и истории изменений, один `ANSWER_MESSAGE` с LLM-текстом публикует команда; в БД не пишет, always-on, PII-egress в LLM — принятый риск | accepted |

Исторические номера (для старых ссылок в git-истории и отчётах задач):

| Было | Стало |
|---|---|
| 0001 | `2026-10-28-stack-kotlin-quarkus-gradle` |
| 0002 | `2026-10-28-single-deployable-app-module` |
| 0003 | `2026-10-28-module-boundaries-and-layer-rules` |
| 0004 | `2026-10-28-in-process-ports-instead-of-loopback-rest` |
| 0005 | `2026-10-28-quality-gates-and-ci` |
| 0006 | `2026-10-28-configuration-and-secrets` |
| 0007 | `2026-11-05-llm-provider-vllm-openai` |
| 0008 | `2026-11-05-bots-long-poll-transport` |
| 0009 | `2026-11-07-daily-greetings` |
| 0010 | `2026-11-08-decision-model-langchain4j` |
| 0011 | `2026-11-09-exception-hierarchy` |
| 0012 | `2026-11-09-decision-runtime-spam-policy` |
| 0013 | `2026-11-10-news-digest` |
| 0014 | `2026-11-10-decision-jailbreak-detection` |
| 0015 | `2026-11-10-profile-dossier` |

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
