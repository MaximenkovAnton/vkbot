# 2026-11-11: «Знания из чата» — асинхронное извлечение фактов без планировщика

- **Статус:** accepted
- **Дата:** 2026-11-11
- **Supersedes:** —

## Контекст

Эпик «Знания о пользователе из чата» собирает факты о людях из сообщений (интересы, привычки, знания,
атрибуты профиля, соцсвязи, события) с источником и уровнем доверия и использует их в досье, ответах,
новостях и поздравлениях. Человек решил (ledger задачи `a6f10aee`): извлечение идёт батчами внутри
консюмера сообщений (D6), триггер — на входящем сообщении (D8), без планировщика; знания живут глобально
по пользователю с источником-чатом (D3); `vk_user_profiles` знаниями не пишется (D9); без TTL (D13);
сводки профиля/чата запускаются из извлечения (D26), `R2` и `R3` — одним релизом.

R2a добавляет: три значения `Event` (`USER_KNOWLEDGE_REQUESTED`, `USER_KNOWLEDGE_SUMMARY_REQUESTED`,
`CHAT_KNOWLEDGE_SUMMARY_REQUESTED`), три payload-класса в `share/domain/event/`, триггер в `processor`,
вертикаль консюмера в `:src:ai` (консюмер → input port → usecase → command → LLM-адаптер),
`ProcessUserKnowledgeCommandImpl`, `UserKnowledgeValidator`, `UserKnowledgeBatchFormatter` и
`KnowledgeExtractionAiAdapter`. Предусловие — замороженные контракты R1a (8 доменных типов, 6 портов,
changeset `010`, jOOQ-объекты) — поставляется в этом же изменении: board-задача `knowledge-contracts`
была закрыта движком на `spec-review` без implementation, поэтому её байты реализованы по одобренной
спеке здесь (решение человека от 2026-11-11).

## Решение

- **Асинхронная вертикаль, никакого планировщика.** `MessageReceivedUsecaseInput` после `val message =
  request.message` и **до** `decisionStage`/`isRequireAnswer` вызывает
  `RequestKnowledgeExtractionCommandImpl`: `knowledge.enabled` → `FromId.isHuman()` → `fromId !=
  vk.bot.id` → `peerId !in knowledge.excluded-chat-ids` → `HasPendingKnowledgeBatchPort(peerId,
  batch-size, batch-interval)` → публикация `USER_KNOWLEDGE_REQUESTED(peerId,
  conversationMessageId)`. Позиция триггера — отклонение от буквы «после маршрутизации»: иначе батч
  видел бы только сообщения, на которые бот отвечает, и терял бы сообщения в тихих чатах (D6/D8).
  `excluded-chat-ids` парсится в инициализаторе — кривой id валит старт, а не проглатывается fail-open.
  Весь блок fail-open: `InterruptedException` возвращает interrupt-флаг и пробрасывается, `RuntimeException`
  даёт WARNING `Knowledge extraction trigger failed for peerId=<peerId>: <ClassName>`, обработка сообщения
  продолжается. Порог и интервал считает `HasPendingKnowledgeBatchPort` (Information Expert — состояние
  курсора в persistence), processor передаёт конфиг.
- **Один LLM-вызов на батч.** Консюмер читает `USER_KNOWLEDGE_REQUESTED`, команда запрашивает
  `FindPendingKnowledgeBatchPort(peerId, upToConversationMessageId, batch-size)`; пустой батч — курсор на
  `upTo`, LLM нет; все сообщения отфильтрованы (не человек, пустой текст, нет даты/id) — курсор на хвост
  батча, LLM нет; иначе один вызов `KnowledgeExtractionPort` со строками `[id|имя]: текст` (имена через
  `FindStoredUserProfilesByIdsPort`, fallback `Пользователь <id>`). Ответ — JSON-строка, которую
  `ObjectMapper` разбирает в `KnowledgeExtractionResponse`; выбран JSON-строка, а не POJO structured
  output: все AI-сервисы дерева возвращают `String`, а разбор и его отказ остаются под тестом
  (`{"facts":[]}` — успех, `{}`/не-JSON — три попытки и `KnowledgeExtractionException`). Адаптер делает
  три попытки, blank и отсутствие `facts` — неудача; WARNING только с номером попытки и классом ошибки.
- **Валидация — чистая функция до записи.** `UserKnowledgeValidator` отбрасывает черновик, если источник
  (`sourceConversationMessageId`) не найден среди обработанных сообщений; субъект не является участником
  батча (по id или по нормализованному имени, ровно одно совпадение); категория не из
  `UserFactCategory`; ключ не матчит `^[a-z0-9._-]{1,96}$`; значение пустое или длиннее 500; confidence
  не число. Confidence клампится в `[0,1]` и ограничивается источником (SELF_STATEMENT 1.0,
  OTHER_STATEMENT/CONGRATULATION 0.8, INFERENCE 0.6, неизвестный — INFERENCE); SELF_STATEMENT при
  другом авторе переклассифицируется в OTHER_STATEMENT. Дедуп — по каноническому
  `(userId, category, factKey, value.trim + \s+ → пробел + lowercase)`, остаётся первое вхождение.
  `value_hash` считает persistence (R1b), R2a его не вычисляет.
- **Границы курсора и отказы.** Порядок строгий: save → advance → summary-события. LLM-отказ —
  исключение наружу, `SaveUserFactsPort`/`AdvanceExtractionCursorPort` не вызываются, курсор не
  двигается; падение save после валидного ответа — курсор не двигается; падение advance после save —
  дубли отсекает R1b; падение публикации сводки — WARNING, извлечение и курсор уже зафиксированы.
  Курсор двигается на `lastConversationMessageId` последнего сообщения батча (не `processable`), в том
  числе при нуле валидных фактов. Идемпотентность целиком делегирована R1b (`UNIQUE` карточки и
  свидетельств), своего состояния дедупа R2a не вводит.
- **Сводки — только события.** После успешного батча публикуется по одному
  `USER_KNOWLEDGE_SUMMARY_REQUESTED(userId)` на затронутого пользователя и одно
  `CHAT_KNOWLEDGE_SUMMARY_REQUESTED(peerId)`; пороги/интервалы и сами сводки — R3a/R3b, поэтому у двух
  событий нет консюмера до R3 и R2+R3 выходят одним релизом (иначе `EventDispatcher` пишет ERROR с
  payload). Публикация каждого события в своём `try/catch RuntimeException`.
- **PII.** Новый код не логирует текст сообщений и значения фактов: INFO команды — `peerId`,
  `messages`, `saved`, `skipped`; WARNING триггера/адаптера/публикации — `peerId`/`userId` и класс
  ошибки; DEBUG пустых веток — только `peerId`. В БД знаний нет текста сообщения (D18), свидетельство —
  ссылка `message_id`/`conversation_message_id`/`peer_id`/автор/дата. Глобальные INFO-логи LangChain4j
  с телами запросов/ответов — принятый риск ADR 2026-11-10-news-digest; новых инструментов и действий у
  сервиса нет, prompt injection не даёт действий.
- **Конфигурация — 5 ключей в `:src:app`** (ADR 2026-10-28-configuration-and-secrets):
  `knowledge.enabled` (`KNOWLEDGE_ENABLED`, true), `knowledge.batch-size` (`KNOWLEDGE_BATCH_SIZE`, 25),
  `knowledge.batch-interval` (`KNOWLEDGE_BATCH_INTERVAL`, `30m`, `java.time.Duration`),
  `knowledge.excluded-chat-ids` (`KNOWLEDGE_EXCLUDED_CHAT_IDS`, пусто) и top-level
  `aiKnowledgeSystemprompt` (`AI_KNOWLEDGE_SYSTEM_PROMPT`); новых обязательных env нет.
- **Контракты R1a в этом же изменении.** 8 доменных типов `share/domain/model/knowledge/`, 6 портов
  `share/port/output/persistence/`, changeset [`010-create-user-knowledge-tables.yaml`](../../src/persistence/src/main/resources/db/changelog/changes/010-create-user-knowledge-tables.yaml) (5 таблиц, UNIQUE,
  индексы, FK, CHECK; второй нетранзакционный changeset с `DROP INDEX CONCURRENTLY IF EXISTS` + `CREATE
  INDEX CONCURRENTLY` для `messages(peer_id, conversation_message_id)`), 5 объектов в [`Tables.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/Tables.kt), 4
  теста контрактов. Схема аддитивна, `001–009` не меняются (байты пиннятся тестом), откат — предыдущий
  образ; удаление знаний после начала записи — только из бэкапа (Liquibase forward-only).

## Последствия

- R2a не добавляет production-зависимостей, модулей, REST и второго деплоя; processor/ai зависят только
  от `share`.
- `HasPendingKnowledgeBatchPort`, `FindPendingKnowledgeBatchPort`, `SaveUserFactsPort`,
  `AdvanceExtractionCursorPort` объявлены, но их реализации — `knowledge-store` (R1b); до неё `:src:app`
  не поднимается (unsatisfied CDI), app-тест и `./gradlew build` ждут R1b. Unit-слой `share`/
  `persistence`/`processor`/`ai` идёт на фейках.
- «Ядовитый» батч стоит до ~54 POST (3 попытки адаптера × 3 провайдера × 6 доставок) — цена ограничена
  размером батча; если станет заметно, нужен лимит попыток и сдвиг курсора с записью «пропущено»
  (отдельное решение с миграцией).
