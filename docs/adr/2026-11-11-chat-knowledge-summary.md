# 2026-11-11: Сводка чата — темы, манера общения, групповые факты и шутки

- **Статус:** accepted
- **Дата:** 2026-11-11
- **Supersedes:** —

## Контекст

Эпик «Знания о пользователе из чата» собирает знания из сообщений и использует их в досье, ответах,
новостях и поздравлениях. Человек решил (ledger задачи `a6f10aee`): агрегат «темы/манера общения/групповые
факты и шутки чата» — отдельный, это связный текст, а не список фактов (D27); он обновляется из извлечения
фактов без планировщика (D6/D8/D26); разбирается весь архив сообщений (D17); знания не пишутся в
`vk_user_profiles` (D9); без TTL/удаления (D13); REST для знаний не добавляется (D19).

R2a уже публикует `CHAT_KNOWLEDGE_SUMMARY_REQUESTED(peerId)` после успешного батча извлечения, но
консюмера у события нет: `EventDispatcher` пишет ERROR с payload и подтверждает доставку. R3b закрывает
разрыв и поставляет: два read-порта (`FindChatSummaryMessagesPort`, `FindChatKnowledgeSummaryPort`),
консюмер в `:src/ai`, `GenerateChatKnowledgeSummaryCommandImpl`, `ChatKnowledgeSummaryPort` ←
`ChatKnowledgeSummaryAiAdapter`/`ChatKnowledgeSummarizer`, `ChatKnowledgeSummaryException`; запись —
через замороженный `SaveChatKnowledgeSummaryPort` в `chat_knowledge_summary` (таблица и changeset `010`
созданы R1a, схема не меняется).

## Решение

- **Два read-порта, граница окна — в persistence.** `FindChatSummaryMessagesPort(peerId, limit)` отдаёт
  сообщения с `conversation_message_id > coalesce(last_conversation_message_id, 0)` по возрастанию, не
  больше `limit`, без верхней границы, `limit ≤ 0` → пусто; граница — скалярный подзапрос в
  `KnowledgeSummaryTable.findChatSummaryWindow` (Information Expert: курсор и таблица принадлежат
  persistence). `FindChatKnowledgeSummaryPort(peerId) → ChatKnowledge?` остаётся ровно
  эпик-формой `(peerId) → ChatKnowledge` (потребитель R4c), `ChatKnowledge` не расширяется. Второй
  вариант — один составной порт с `lastConversationMessageId` — смешал бы две причины изменения и
  поменял бы форму контракта для R4c; окно по `summarized_at` теряло бы поздно доставленные сообщения.
- **Порог и интервал — политика команды в `ai`.** `GenerateChatKnowledgeSummaryCommandImpl` читает
  `knowledge.chat-summary-message-threshold` (окно = `limit` и порог) и `knowledge.chat-summary-interval`
  (`java.time.Duration`, конвертер Quarkus понимает `7d` → `PT168H`). Гейт: пустое окно — DEBUG и выход
  **до** чтения сводки; иначе генерация, если `window.size >= threshold`, или сводки ещё нет, или
  `summarizedAt <= now − interval` (равенство считается истечением); иначе DEBUG и выход без LLM и save.
  Первый прогон (`summary == null`) генерирует при любом непустом окне — буквальный бриф «от
  `last_conversation_message_id` или с начала архива» (D17). Отдельный `HasPendingChatSummaryPort` не
  вводится: окно уже возвращает размер, а `summarizedAt` даёт второй read-порт.
- **Один LLM-вызов на окно, три попытки в адаптере.** В промпт уходят только строки общего
  `MessageAuthorFormatter` (`[имя]: текст`, без id); окно не фильтруется по авторам и пустым текстам —
  иначе окно из одних групп зациклило бы границу (save не двигает курсор). Прошлая сводка в промпт не
  идёт: одна строка `chat_knowledge_summary` — rolling snapshot. Промпт-контракт требует связный текст
  («не пересказывай сообщения по одному, не списком, не выдумывай»), но качество связности тестом не
  доказывается — named gap, проверка на проде.
  `ChatKnowledgeSummaryAiAdapter` делает ровно три попытки; пустой/пробельный ответ — неудача; WARNING
  только с номером попытки и классом ошибки. После третьей — `ChatKnowledgeSummaryException`
  (`EXTERNAL_FAILURE`) наружу, в retry-лестницу брокера; save не вызывается, прежняя сводка и курсор
  остаются, повторная доставка перечитает то же окно. Команда клипует ответ до 2000 символов (гарантия
  «≤~2000» для R4c) и пишет `summary_text`, `first/last_conversation_message_id` фактического окна и
  `messages_covered = window.size`; upsert по `peer_id` идемпотентен, поэтому отдельный outbox/дедуп не
  вводится (at-least-once, последняя запись побеждает).
- **PII.** `chat_knowledge_summary` — новый PII-сток (производный текст личных высказываний); текст уходит
  в LLM и в INFO/OTLP-логи провайдера — принятый риск ADR 2026-11-10-news-digest, новых инструментов и
  действий у сервиса нет, prompt injection не даёт действий. Логи R3b: INFO команды — только `peerId`,
  `messages`, `length`; DEBUG — только `peerId` и причина; WARNING адаптера — попытка и класс ошибки. Текст
  сообщений и авторы в логи не попадают.
- **Конфигурация — 3 ключа в `:src:app`** (ADR 2026-10-28-configuration-and-secrets):
  `knowledge.chat-summary-message-threshold` (`KNOWLEDGE_CHAT_SUMMARY_MESSAGE_THRESHOLD`, default 100),
  `knowledge.chat-summary-interval` (`KNOWLEDGE_CHAT_SUMMARY_INTERVAL`, default `7d`, `java.time.Duration`),
  top-level `aiChatKnowledgeSummarySystemprompt` (`AI_CHAT_KNOWLEDGE_SUMMARY_SYSTEM_PROMPT`); новых
  обязательных env нет.
- **Стоимость.** Два дешёвых SELECT на каждое событие и один LLM-вызов только на «назревшее» окно;
  «ядовитое» окно ≈ 54 POST (3 попытки адаптера × 3 провайдера × 6 доставок) — как R2a. Окно опирается
  на индекс `messages(peer_id, conversation_message_id)` из changeset `010`.
- **Отказы.** Окно/сводка не прочитались — исключение наружу, ничего не записано; LLM пустой/ошибка после
  трёх попыток — `ChatKnowledgeSummaryException`, прежняя сводка и курсор не меняются; save упал —
  исключение, повтор сгенерирует то же окно, upsert перезапишет; пустое окно/закрытый гейт — DEBUG,
  доставка подтверждена; дубль доставки — повторная генерация того же окна, upsert оставляет последнюю.

## Последствия

- Событие `CHAT_KNOWLEDGE_SUMMARY_REQUESTED` получает консюмера, разрыв «событие без консюмера» из R2a
  закрыт; R2 и R3 по-прежнему выходят одним релизом.
- Схема, миграции, `Event`/payload, 6 write-портов R1a и `Tables.kt` не меняются; откат — предыдущий
  образ (старый образ не публикует событие и не читает `chat_knowledge_summary`, восстановление БД не
  требуется).
- Первый прогон по большому архиву идёт от старых сообщений, по одному окну на событие извлечения:
  сводка какое-то время отражает старые темы, пока не догонит. Условие пересмотра — жалоба на
  нерелевантные новости (тогда первый прогон по последнему окну — решение человека).
- Клип 2000 в команде — тестируемая гарантия; если начнёт резать важное, отказ/смягчение переносится в
  адаптер.
- Потребитель `FindChatKnowledgeSummaryPort` — R4c (темы в новостях); форма `(peerId) → ChatKnowledge?`
  не расширяется.
