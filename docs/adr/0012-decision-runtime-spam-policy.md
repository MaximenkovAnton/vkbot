# ADR 0012: Decision-модель в runtime — спам-уведомление и третий вопрос `intent`

- **Статус:** accepted
- **Дата:** 2026-11-09
- **Supersedes:** —
- **Amends:** ADR 0010 — решение «Без вызова из runtime» (пункт «Без вызова из runtime» в разделе
  «Решение»)

## Контекст

Клиент decision-модели уже есть в `:src:processor` ([ADR 0010](0010-decision-model-langchain4j.md)):
порт `AnalyzeMessageOutputPort` → адаптер `DecisionModelOutputAdapter` над библиотечным
`dev.langchain4j.model.decision.DecisionModel` (два вопроса: `spam` yes/no и `class` choice
`spam`/`command`/`ham`), доменные `DecisionAnalysis`/`MessageClass` и `DecisionModelException`. Из
runtime он не вызывается: `MessageReceivedUsecaseInput` только решает `requiredAnswer` и публикует
`MESSAGE_REQUIRE_ANSWER`.

Нужно: (1) останавливать явный спам от человека в групповом чате обычным сообщением-уведомлением до
`requiredAnswer`, (2) начать вычислять намерение автора (`help`/`news`/`general`) третьим вопросом той
же модели — маршрутизация интентов будет следующей задачей. Решение «клиент не вызывается из runtime»
из ADR 0010 этим ADR заменяется; остальные решения ADR 0010 (библиотека, транспорт, ошибки, PII,
пустой вход) сохраняются.

## Решение

- **Decision-модель вызывается из runtime.** Единственная точка вызова — `MessageReceivedUsecaseInput`
  (`src/processor/src/main/kotlin/com/simarel/vkbot/processor/usecase/message/messageReceived/MessageReceivedUsecaseInput.kt`),
  до `requiredAnswer`. **Не более одного вызова на сообщение, для которого анализ запрошен.** В этом
  board-task анализ запрашивается только для сообщения человека (`fromId.isHuman()`) в групповом чате
  (`peerId.isGroupChat()`) при `decision.spam-reply.enabled = true`; для личных чатов и при выключенном
  kill-switch — ноль вызовов. Следующая задача (`intent-routing-help`) вызывает модель один раз, если
  анализа ещё нет (личный чат, kill-switch off, fail-open), и переиспользует готовый анализ иначе —
  инвариант «не более одного вызова» сохраняется. Поток слоёв не меняется:
  `adapter input → port input → usecase → command → port output → adapter output`.
- **Спам-политика.** Для применимого сообщения `spamProbability >= decision.spam-threshold` (дефолт
  `0.9`) даёт ровно один `ANSWER_MESSAGE` с `ResponseMessage(messageText, responseTo = исходное Message)`
  и дословным текстом `Сработал автодетект спама. Вероятность спама: N%.`, где `N =
  Math.round(spamProbability * 100)` (примеры: `0.9 → 90`, `0.908 → 91`, `0.95 → 95`, `0.999 → 100`).
  После публикации обработка останавливается: `MESSAGE_REQUIRE_ANSWER` и другие события не
  публикуются. Это только уведомление, не модерация: удаления, бана, скрытия и лимита ответов нет.
  В групповом чате `vk-facade` добавляет к ответу forward-блок исходного сообщения — существующее
  поведение `ANSWER_MESSAGE`, контракт события не меняется.
- **Kill-switch** `decision.spam-reply.enabled` (дефолт `true`) гасит **спам-этап** целиком: вызов
  decision-модели и ответ. При выключенном kill-switch текст сообщения не уходит во внешний System One.
  Будущий вызов для интента этим ключом не гасится (его политика — в `intent-routing-help`).
- **Fail-open.** Любой `RuntimeException` из порта (`DecisionModelException`,
  `DecisionModelResponseException` или другой) не пробрасывается в брокер: пишется ровно одна
  WARNING-запись, сообщение обрабатывается по прежним правилам (`requiredAnswer` → `MESSAGE_REQUIRE_ANSWER`,
  иначе — без событий). WARNING содержит только `peerId` и простое имя класса исключения
  (`Decision model failed for peerId=<peerId.value>: <e.javaClass.simpleName>`), без текста сообщения,
  без `e.message` и без throwable (PII). `InterruptedException` — исключение: флаг прерывания
  восстанавливается (`Thread.currentThread().interrupt()`) и исключение пробрасывается. Почему
  fail-open: недоступность decision-сервера не должна ломать ответы, а ретраи через брокер усилили бы
  PII-поток и нагрузку на сервер.
- **Третий вопрос `intent`** — расширение контракта ADR 0010 в том же единственном `decide`; отдельной
  версии и флага нет, потребитель один — код бота. Порядок вопросов в одном `DecisionRequest`:
  `spam`, `class`, `intent`. Дословный контракт:

  | # | Имя | Тип | Текст/критерии |
  |---|---|---|---|
  | 1 | `spam` | `YesNoQuestion` | `Это спам, реклама, накрутка или мошенничество?` |
  | 2 | `class` | `ChoiceQuestion`, опции `spam`/`command`/`ham` | `Определи тип сообщения в чате с ботом.`; критерии — из ADR 0010 |
  | 3 | `intent` | `ChoiceQuestion`, опции `help`/`news`/`general` | `Определи, что просит автор сообщения.`; `help` = `Просит справку: /help, /помощь, «что ты умеешь», «какие есть команды»`; `news` = `Спрашивает, что нового или что происходило в чате: «что нового», «что писали», «о чём говорили», «что было вчера», «что нового за последние 3 дня»`; `general` = `Всё остальное: обычный вопрос, сообщение или просьба не из списка` |

  Словарь ключей — ровно `{help, news, general}`; доменный `MessageIntent` — `HELP`/`NEWS`/`GENERAL`;
  `DecisionAnalysis.intent` — обязательное поле. Маппинг `help → HELP`, `news → NEWS`,
  `general → GENERAL`. Ошибки: невалидный ответ на `intent`, отвергнутый библиотечным валидатором
  (`InvalidDecisionResponseException` внутри `decide`), транслируется в
  `DecisionModelException("Decision model response is invalid", cause = InvalidDecisionResponseException)`;
  `RefusalAnswer` на `intent` — `DecisionModelException("Decision model call failed", cause =
  ContentFilteredException)`, как для `spam`/`class`; защитная ветка, если валидатор обойдён и до
  маппинга дошло значение вне словаря, — тот же литерал `Decision model response is invalid` с cause
  `DecisionModelResponseException("Decision model response is invalid: unknown message intent")`.
  Трансляция по имени вопроса в тексте библиотечного исключения — известноe ограничение (shortcut:
  апгрейд LangChain4j или структурная причина в исключении потребуют замены разбора `message`).
  Пустой нормализованный текст — `HAM`/`null`/пустая карта/`0.0`/`GENERAL` без вызова модели.
  `intent` вычисляется здесь, маршрутизация (`/help`, `/помощь`, «что нового») — в
  `intent-routing-help`; в этом board-task интент не влияет на ответ.
- **PII.** Decision-логи с нормализованным текстом включены наследованием глобальных
  `quarkus.langchain4j.log-requests/log-responses: true` — принятый риск ADR 0010, не пересматривается.
  Новый код текст сообщения не логирует; WARNING не несёт PII. Нормализованный текст теперь уходит во
  внешний System One на каждом применимом сообщении (человек в групповом чате при включённом
  kill-switch) — новый поток данных, принятый.
- **Конфигурация** (единственный конфиг `:src:app`, [ADR 0006](0006-configuration-and-secrets.md)):

  ```yaml
  decision:
    spam-threshold: ${DECISION_SPAM_THRESHOLD:0.9}
    spam-reply:
      enabled: ${DECISION_SPAM_REPLY_ENABLED:true}
  ```

  `decision.spam-threshold` читает `IsSpamCommandImpl` (`@ConfigProperty`), `decision.spam-reply.enabled`
  — `MessageReceivedUsecaseInput`. Валидации диапазона порога на старте нет: значение `>1` означает
  «никогда», `<0` — «всегда». Новых зависимостей, модулей, событий, payload'ов, схемы БД и Liquibase
  changeset'ов нет.
- **Совместимость и раскатка.** Сначала decision-сервер должен принимать и отвечать на третий вопрос,
  затем выкатывается образ; новые env опциональны (дефолты в `application.yml`). Старый сервер даёт
  отказ на каждом применимом вызове → fail-open WARNING, спам-детект и `intent` недоступны, ответы
  работают как раньше. `DECISION_URL` обязателен по контракту деплоя, но **поведение старта без него
  этой задачей не проверялось** — named gap ADR 0010 сохраняется, доки это не утверждают.
- **Откат.** Предыдущий образ; данные и схема не меняются. Операционный откат без образа —
  `DECISION_SPAM_REPLY_ENABLED=false`.
- **Надёжность.** Доставка событий at-least-once: повторная доставка `MESSAGE_RECEIVED` может дать
  повторный спам-ответ (как и повторный AI-ответ сегодня), своей дедупликации нет. Повторов
  decision-вызова нет, кроме жёстко заданного библиотекой `maxRetries(1)` (ADR 0010); сбой публикации
  `ANSWER_MESSAGE` обрабатывается существующей retry-лестницей `event-dispatch`.

## Известные пробелы (named gaps)

- `intent` вычисляется, но не маршрутизируется до board-task `intent-routing-help`; `/help`, `/помощь`,
  «что нового» и событие `NEWS_DIGEST_REQUESTED` в этом дереве не появляются.
- Личные чаты decision-модель в этом board-task не вызывает; появятся в `intent-routing-help`.
- Поведение старта без `DECISION_URL` не проверено этой задачей (named gap ADR 0010); доки пишут только
  «переменная обязательна» и «недоступность сервера в runtime → fail-open».
- Порог вне диапазона `[0,1]` не валидируется на старте.
- At-least-once: повторная доставка `MESSAGE_RECEIVED` может дать повторный спам-ответ; дедупликации нет.
- Амплификация: спам-ответ отправляется без упоминания бота, по одному на сообщение, без лимита.
- Модерации спама нет — только уведомление.
- Невалидный `intent` отличается от невалидного `class`/`spam` по тексту библиотечного
  `InvalidDecisionResponseException` (содержит имя вопроса); условие апгрейда — структурная причина в
  исключении или апгрейд LangChain4j.
- Документы вне allowlist этой задачи (`docs/development/testing.md`, `docs/architecture/overview.md`,
  `AGENTS.md`, `docs/capabilities/ai-answer.md`, `docs/capabilities/vk-message-send.md`) остаются
  неточными и перечислены в отчёте MITM.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Fail-closed: отказ decision-модели блокирует обработку сообщения | Недоступность внешнего сервера ломала бы обычные ответы и копила retry-нагрузку; уведомление о спаме — вспомогательная функция |
| Ретраи decision-отказов через брокер | Каждый ретрай снова отправляет текст в System One (PII) и усиливает нагрузку на упавший сервер; доставка `MESSAGE_RECEIVED` уже at-least-once |
| Эвристика вместо модели для интента | Решение человека: третий вопрос decision-модели, а не правила по ключевым словам; `/help` и `/помощь` детерминированно распознаются в следующей задаче как страховка, а не как основной механизм |
| Отдельное событие/консьюмер для decision-анализа | Новое (седьмое) событие, routing key, очередь и консьюмер, гонка с `MESSAGE_REQUIRE_ANSWER` и двойные ответы; порядок «до requiredAnswer» стал бы недетерминированным |
| Per-chat/per-user пороги | Нет требования; новый конфиг/таблица — отдельная задача |
| Кэш/батчинг/rate limit decision-вызовов | Латентность/квота не измерены; преждевременная оптимизация |
| Валидация порога на старте | Нет требования; вне диапазона означает «никогда/всегда» |
| Переписать таксономию ошибок ADR 0010 | Существующие литералы `call failed`/`response is invalid` для `spam`/`class` сохраняются; для `intent` добавлен только `response is invalid` на production-пути |

## Последствия

- Появился второй producer `ANSWER_MESSAGE` (первый — `ai`); контракт события и payload'а не менялся.
- Текст сообщения человека в групповом чате уходит во внешний System One на каждом применимом
  сообщении при включённом kill-switch; логи провайдера содержат нормализованный текст (PII, ADR 0010).
- Отказ decision-сервера наблюдаем как WARNING без PII на каждое применимое сообщение; анти-флуда нет
  (условие апгрейда — неприемлемый поток WARNING в OTLP).
- Деплой: сначала сервер с третьим вопросом, затем образ; `DECISION_SPAM_THRESHOLD` и
  `DECISION_SPAM_REPLY_ENABLED` опциональны. Откат — предыдущий образ или kill-switch.
