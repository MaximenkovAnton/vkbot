# 2026-11-11: LLM-сводка профиля — событие, гейт, канонический блок фактов и свежесть

- **Статус:** accepted
- **Дата:** 2026-11-11
- **Supersedes:** —

## Контекст

Эпик «Знания о пользователе из чата» уже хранит факты (R1, ADR
[2026-11-11-user-knowledge-facts](2026-11-11-user-knowledge-facts.md)), наполняет их извлечением из чата
(R2a, ADR [2026-11-11-knowledge-extraction](2026-11-11-knowledge-extraction.md)) и отдаёт чтением
(R2b, ADR [2026-11-11-knowledge-read-model](2026-11-11-knowledge-read-model.md)). Сводка чата (R3b, ADR
[2026-11-11-chat-knowledge-summary](2026-11-11-chat-knowledge-summary.md)) — отдельный поток, из которого
взят шаблон вертикали.

R2a публикует `USER_KNOWLEDGE_SUMMARY_REQUESTED(userId)` после **каждого** успешного батча извлечения без
проверки порога; у события до R3a нет ни одного консюмера, поэтому `EventDispatcher` писал бы ERROR с
payload. Факты пользователя копятся, но связного текста о человеке нет: досье, ответ, новости и
поздравления (R4a–R5) читали бы список карточек, а не компактное описание. Нужен ровно один LLM-вызов на
«назревший» профиль, который сводит текущие факты, историю и конфликты в связный текст ≤ ~2000 символов,
и сохранение этого текста без изменения R1-схемы и замороженных контрактов.

## Решение

- **Один новый read-порт состояния, политика — в команде `ai`.** `FindUserKnowledgeSummaryStatePort`
  `Request(userId) → Response(summarizedAt: OffsetDateTime?, factsSinceSummary: Int)` в `share`;
  `persistence` владеет таблицами и отвечает на вопрос «сколько текущих карточек новее сохранённой
  сводки», а `GenerateUserKnowledgeSummaryCommandImpl` — владелец политики «пора тратить LLM» (порог и
  интервал — конфиг `ai`). `FindUserKnowledgePort`, `UserKnowledge`, шесть write-портов, `Event` и payload
  не меняются; версия контракта аддитивная.
- **Семантика состояния.** Нет строки `user_knowledge_summary` — `(null, 0)`; иначе `summarized_at` строки
  и `count(user_facts WHERE user_id = ? AND status <> 'HISTORICAL' AND last_confirmed_at > summarized_at)`.
  Неравенство строгое: свидетельство, пришедшее ровно в момент сводки, новым не считается. Счёт идёт по
  существующему индексу `(user_id, status)`, второй `SELECT` выполняется только при наличии строки
  сводки.
- **Гейт: первый прогон, порог или интервал.** Генерация выполняется, когда `summarizedAt == null`
  (первый прогон), `factsSinceSummary >= knowledge.summary-fact-threshold` (20) или
  `summarizedAt <= now − knowledge.summary-interval` (14 дней; равенство считается истечением, как R3b).
  Иначе — DEBUG только с `userId` и причиной, выход **до** чтения фактов, LLM и save. `now` —
  `OffsetDateTime.now(ZoneOffset.UTC)` в начале команды; `Duration` читается конвертером Quarkus без
  своего парсера.
- **Детерминированный блок фактов.** `UserKnowledgeSummaryFormatter` (чистый `object` в `ai/domain`)
  исключает `stale=true`, группирует по категориям в порядке
  `INTEREST→HABIT→KNOWLEDGE→PROFILE_ATTRIBUTE→SOCIAL→EVENT`, даёт русские заголовки и префиксы:
  `CONFIRMED` — без префикса; `CANDIDATE` — `возможно:`; `HISTORICAL` — `ранее:`; `conflict=true` —
  `возможно, противоречивые данные:` (приоритет). В текст не попадают `factKey`, `id`, `peerId`,
  `userId`, даты, источник, confidence и `evidenceCount`; пустой результат — LLM и save не вызываются
  (DEBUG `no fresh facts`).
- **Один LLM-вызов, три попытки, клип 2000.** `UserKnowledgeSummaryPort` (Request — готовый блок,
  Response — текст) реализует `UserKnowledgeSummaryAiAdapter`: system-промпт
  `{config:aiKnowledgeSummarySystemprompt}`, ровно три попытки, `blank` — неудача, WARNING только с
  номером попытки и классом ошибки, после третьей — `UserKnowledgeSummaryException` (`EXTERNAL_FAILURE`).
  Команда клипует ответ до 2000 символов и вызывает `SaveUserKnowledgeSummaryPort(userId, text)`.
  Прошлая сводка в промпт не идёт: rolling snapshot, как у сводки чата.
- **Свежесть считает SQL upsert, порт не расширяется.** `SaveUserKnowledgeSummaryPort.Request` остаётся
  `(userId, summaryText)`; `KnowledgeSummaryTable.upsertUserSummary` сам считает
  `facts_covered` (текущие карточки) и `last_fact_at = coalesce(max(last_confirmed_at), now)` (решение
  человека «Считать в SQL при upsert»). Читатели видят `summaryFresh = summarized_at >= last_fact_at`
  через R2b.
- **Стоимость.** На большинство событий — один дешёвый `SELECT` состояния и выход; LLM только на
  назревшее: первый прогон на пользователя, затем ≥20 новых карточек или 14 дней. «Ядовитое» событие —
  до 3 попыток адаптера × 3 попытки провайдера × 6 доставок брокера, как у R2a/R3b.
- **PII.** `user_knowledge_summary.summary_text` — производный текст о человеке, впервые заполняемый
  R3a; это PII-сток наравне с `chat_knowledge_summary`. Код R3a логирует выше DEBUG только `userId`,
  число фактов, длину, номер попытки и класс ошибки; ни значений фактов, ни текста сводки. Промпт и
  ответ модели попадают в глобальные INFO-логи и OTLP-трассировку LangChain4j — принятый риск ADR
  [2026-11-10-news-digest](2026-11-10-news-digest.md). Инструментов и памяти у AI-сервиса нет, поэтому
  prompt injection не даёт действий.
- **Совместимость и релиз.** Схема, changeset `010`, [`Tables.kt`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/output/persistence/jooq/Tables.kt), `Event`, payload и R1-контракты не
  меняются; три ключа конфига имеют дефолты в [`application.yml`](../../src/app/src/main/resources/application.yml)/аннотациях, новых обязательных env нет.
  Откат на предыдущий образ безопасен (новые строки сводки старый образ не читает), но частичный откат
  «R2a есть, R3a нет» не поддержан: событие останется без консюмера, поэтому R2+R3 выходят одним
  релизом (ADR [2026-11-11-knowledge-extraction](2026-11-11-knowledge-extraction.md)).

## Последствия

- Решение «пора ли обновлять сводку» доказуемо unit-тестом `ai` там, где назван AC-KNOW-11: порог и
  интервал — параметры команды, а не SQL; смена смысла «нового факта» — правка одного SQL без миграции.
- Сводка — производная read-модель, а не источник истины: `user_facts` остаются данными, текст можно
  перегенерировать; R4a–R5 читают и сводку, и карточки.
- Первый прогон идёт только по событию извлечения: у пользователя без новых батчей сводка не появится
  до следующего сообщения; планировщик запрещён (решение человека D6), backfill — отдельная задача.
- Гейт и чтение фактов — два независимых вызова без snapshot: между ними факт может появиться, следующий
  батч это исправит; «ровно одна сводка на набор фактов» потребует outbox/уникального ключа и миграции
  (вне R3a).
- Качество связного текста, «синонимы сведены» и «гипотезы помечены» — свойство модели; unit-тесты
  доказывают контракт промпта и сохранение текста, полная проверка — e2e R6 (named gap).
- Если клип 2000 начнёт резать важное, смягчение/отказ — отдельное решение в адаптере; если потребуется
  преемственность формулировок — `{previousSummary}` из `FindUserKnowledgePort` без миграции.
