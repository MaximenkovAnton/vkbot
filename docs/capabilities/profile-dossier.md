# profile-dossier — досье об участнике чата

- **id:** `profile-dossier`
- **Статус:** delivered

## Что делает

Досье об участнике чата — ветка шага 3 `journey-answer` после `requiredAnswer=true`, когда итоговый интент
`profile`: `answer-decision` публикует `PROFILE_REQUESTED` с доменным `Message`, `profile-dossier`
детерминированно определяет адресата, читает сохранённые данные, один раз просит LLM русский текст досье и
публикует ровно один `ANSWER_MESSAGE` с `ResponseMessage(messageText, responseTo = исходное Message)`;
доставляет его `vk-message-send`. В БД ничего не пишется: у команды нет ни одного write-порта.

1. **Адресат определяется детерминированным доменом**, не моделью. Порядок строгий, первый сработавший шаг
   выигрывает:

   | Шаг | Что ищется | Цель |
   |---|---|---|
   | 1. Упоминания | `[idN\|label]`, `[clubN\|label]`, `[publicN\|label]` и отдельные токены `idN`/`clubN`/`publicN` (N > 0) | `User(N)` для `id`, `Group(-N)` для `club`/`public`; упоминание самого бота (его id, id сообщества или `vk.bot.mention`) отбрасывается; один id — цель, несколько — уточнение |
   | 2. Пересылка | `forwardedMessages` с конца списка | первое пересланное сообщение с `fromId ≠ vk.bot.id`: `User` при `fromId > 0`, `Group` при `fromId < 0`; все пересылки от бота — следующий шаг |
   | 3. «Обо мне» | нормализованный текст содержит `обо мне` или `про меня` | `User(message.fromId)` |
   | 4. Имя | остаток текста после вырезания триггеров и служебных слов | ровно один участник этого `peer_id` по сохранённому профилю — цель; несколько — уточнение; ноль — «не нашёл» |

2. **Досье собирается из сохранённых источников** и передаётся LLM детерминированным русским текстом:
   профиль VK (typed-поля и человеческие `details`), знания о человеке из чата, сводка этого чата
   (`Темы чата:`), профиль и знания того, кто спрашивает (`Кто запрашивает:`, `Знания запрашивающего:`),
   материализованная статистика (глобальные агрегаты и разбивка по чатам, включая другие чаты), последние
   20 записей истории изменений профиля и число сообщений цели в этом чате. Секции идут строго в порядке
   `Профиль:`, `Из общения:`, `Темы чата:`, `Кто запрашивает:`, `Знания запрашивающего:`, `Статистика:`,
   `История изменений:`, `В этом чате:`; пустые секции не пишутся. Группа получает профиль группы, сводку
   чата, контекст запрашивающего и счётчик в этом чате — статистики, истории и знаний о самой группе у
   групп нет.
3. **Знания из чата — overlay на чтении.** После резолва профиля команда один раз читает знания цели
   через `FindUserKnowledgePort` и кладёт их в `ProfileDossier.UserDossier.knowledge`. Знания глобальны
   по пользователю: порт не фильтрует по `peer_id`, `peer_id` карточки — чат первого свидетельства,
   поэтому факт из другого чата виден в досье из этого чата (ADR 2026-11-13-profile-dossier-context). Секция
   `Из общения:` строится так: свежая сводка (`summaryFresh=true`, текст непустой) — связный сохранённый
   текст как есть; иначе — компактный список фактов, который рендерит единственный
   `UserKnowledgeSummaryFormatter` (`CONFIRMED` обычной строкой, `CANDIDATE` — «возможно», `HISTORICAL` —
   «ранее:», `conflict` — «возможно, противоречивые данные», `stale` не печатается); источники, `factKey`,
   id и даты в секцию не попадают. Пустые знания (нет фактов и нет свежей сводки) — секции нет, промпт
   прежний. Конфликт VK↔чат отдаётся LLM двумя версиями: VK-значение остаётся в `Профиль:`, значение из
   чата — в `Из общения:`; `vk_user_profiles` знаниями не пишется. Сбой этого опционального чтения
   (`RuntimeException`) — один WARNING с `peerId`, `targetId` и простым именем класса исключения (без
   значений) и досье без секции; ответ публикуется.
4. **Контекст чата и автора запроса — тоже overlay на чтении.** Команда читает сводку чата
   `FindChatKnowledgeSummaryPort(peerId)` в `chatTopics` и, если `message.fromId` — человек, не бот и не
   цель, читает его сохранённый профиль (`FindStoredUserProfilesByIdsPort`) и знания
   (`FindUserKnowledgePort`) в `requesterProfile`/`requesterKnowledge`. `Темы чата:` рендерятся как есть,
   `Кто запрашивает:` — тем же allowlist-рендером профиля, `Знания запрашивающего:` — тем же правилом
   «свежая сводка либо список фактов», что и `Из общения:`. Каждое из этих чтений fail-open: сбой —
   один WARNING без значений (`Profile chat knowledge read failed for peerId=<peerId>`,
   `Profile requester read failed for peerId=<peerId> requesterId=<id>`) и досье без секции; при
   отсутствии новых данных промпт байт-идентичен прежнему.
5. **Форматтер — allowlist.** Рендерятся только известные ключи `details` с русскими подписями; телефоны
   (`mobile_phone`, `home_phone`), `counters`, `connections`, неизвестные ключи и VK-id не рендерятся нигде,
   кроме списка кандидатов уточнения. Записи истории без известных полей пропускаются.
6. **LLM пишет только текст.** Один вызов `ProfileAnswerGenerationPort` с вопросом (исходный текст
   сообщения) и готовым текстом досье; system-сообщение — из `aiProfileSystemprompt`. Адаптер делает три
   попытки (пустой или пробельный ответ — неудачная попытка), после третьей бросает
   `ProfileGenerationException` со статусом `EXTERNAL_FAILURE`; команда ловит только её, публикует
   фиксированный фолбэк и пишет ERROR только с `peerId` и простым именем класса исключения.
7. **Запись отсутствует.** Live-профиль, запрошенный через `GetUserProfilesBatchPort`/
   `GetGroupProfilesBatchPort`, не сохраняется; `messages`, профили, история, статистика и знания только
   читаются. `SUMMARY_REQUESTED`/`SUMMARY_READY` не публикуются.

Неоднозначность решается одним сообщением, состояние «жду уточнение» не запоминается: следующий запрос
резолвится заново. Уточнение перечисляет не больше 10 кандидатов в порядке первого появления или
возрастания `fromId` и не вызывает LLM.

| Случай | Дословный текст (без LLM) |
|---|---|
| Несколько кандидатов | `Уточните, о ком речь: 1) Имя Фамилия (@screen, id7600001); 2) …. Напишите полное имя или упоминание.` — не больше 10; без профиля — `N) id<fromId>` |
| Никого не нашли | `Среди участников этого чата не нашёл «<фраза>». Напишите полное имя, @упоминание, id или перешлите сообщение.` |
| Цель не определена | `Уточните, о ком речь: имя, @упоминание, id или пересланное сообщение.` |
| Профиль недоступен | `Профиль недоступен.` |
| LLM отказал | `Не удалось собрать профиль. Попробуйте позже.` |

## Точки входа

| Точка входа | Где в коде |
|---|---|
| `PROFILE_REQUESTED` из `all-events-queue`: разбор `Message` и вызов входного порта | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/ProfileRequestedEventProcessor.kt:12-22`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/input/event/ProfileRequestedEventProcessor.kt#L12-L22) |
| сценарий, резолв адресата, сборка досье и ровно одна публикация `ANSWER_MESSAGE` | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImpl.kt:52-247`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImpl.kt#L52-L247) |
| входной порт и тонкий use case | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/port/input/profile/ProcessProfileInputPort.kt:8-18`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/port/input/profile/ProcessProfileInputPort.kt#L8-L18), [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/profile/ProcessProfileUsecase.kt:10-20`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/usecase/profile/ProcessProfileUsecase.kt#L10-L20) |
| резолв адресата: mentions, пересылка, «обо мне» | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileTargetResolver.kt:15-111`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileTargetResolver.kt#L15-L111) |
| вырезание фразы имени и сопоставление с участниками | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileNameNormalizer.kt:3-59`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileNameNormalizer.kt#L3-L59), [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileNameMatcher.kt:11-45`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileNameMatcher.kt#L11-L45) |
| досье и детерминированный русский текст, allowlist `details` и истории | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossier.kt:8-20`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossier.kt#L8-L20), [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatter.kt:60-221`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatter.kt#L60-L221) |
| LLM: `@RegisterAiService` без инструментов и памяти + 3 попытки и фолбэк-исключение | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/profile/ProfileAnswerGenerator.kt:8-26`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/profile/ProfileAnswerGenerator.kt#L8-L26), [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/exception/ProfileGenerationException.kt:6-8`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/domain/exception/ProfileGenerationException.kt#L6-L8), [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/profile/ProfileAiAdapter.kt:14-47`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/adapter/output/ai/profile/ProfileAiAdapter.kt#L14-L47) |
| новый output-порт `ProfileAnswerGenerationPort` | [`src/ai/src/main/kotlin/com/simarel/vkbot/ai/port/output/profile/ProfileAnswerGenerationPort.kt:7-16`](../../src/ai/src/main/kotlin/com/simarel/vkbot/ai/port/output/profile/ProfileAnswerGenerationPort.kt#L7-L16) |
| три read-only `share`-порта чтения | [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindChatAuthorsPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindChatAuthorsPort.kt), [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredUserStatisticsPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredUserStatisticsPort.kt), [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredUserProfileHistoryPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindStoredUserProfileHistoryPort.kt) |
| live-профили при отсутствии сохранённого | [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetUserProfilesBatchPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetUserProfilesBatchPort.kt), [`src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetGroupProfilesBatchPort.kt`](../../src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/vk/GetGroupProfilesBatchPort.kt) |
| in-process реализация чтений в `persistence` | [`src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt:284-318`](../../src/persistence/src/main/kotlin/com/simarel/vkbot/persistence/adapter/input/inprocess/PersistenceDataInProcessAdapter.kt#L284-L318) |
| доставка ответа в VK | [`src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SendMessageEventProcessor.kt`](../../src/vk-facade/src/main/kotlin/com/simarel/vkbot/vkFacade/adapter/input/event/SendMessageEventProcessor.kt) |

## Конфигурация

| Ключ | Значение / env | Эффект |
|---|---|---|
| `aiProfileSystemprompt` | `${AI_PROFILE_SYSTEM_PROMPT:Ты — помощник, который собирает короткое человекочитаемое досье об участнике чата по данным ниже. Пиши по-русски, только по переданным данным, не выдумывай и не раскрывай служебные id, телефоны и контакты.}` | system-сообщение `ProfileAnswerGenerator`; ключ объявлен в [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml), поэтому старт не зависит от env |
| `vk.bot.id`, `vk.bot.mention`, `vk.group.id` | `${VK_BOT_ID}`, `${VK_BOT_MENTION:@simarel}`, `${VK_GROUP_ID}` | отбрасывание упоминания бота и его пересылок при резолве |
| `statistics.timezone` | `${STATISTICS_TIMEZONE:Europe/Minsk}` | зона дат в тексте досье (существующий ключ; тот же, что у статистики) |

Новых обязательных env нет. Kill-switch у возможности нет: она always-on, как `chat-news-digest`; откат —
предыдущий образ.

## Данные

Только читает. Досье собирается из `messages` (число сообщений цели в чате через `FindChatAuthorsPort`),
`vk_user_profiles` и `vk_group_profiles` (typed-поля и `profile_details`), `vk_user_profile_history`
(последние 20 записей, newest-first), трёх таблиц материализованной статистики
(`user_statistics`, `user_chat_statistics`, `user_hour_statistics`) и знаний о человеке из чата
(`user_facts`, `user_knowledge_summary`) одним вызовом `FindUserKnowledgePort`; сводку чата
(`chat_knowledge_summary`) — `FindChatKnowledgeSummaryPort`; профиль и знания запрашивающего —
`FindStoredUserProfilesByIdsPort`/`FindUserKnowledgePort`. Схема, jOOQ и Liquibase
остаются у `message-storage`; новых изменений схемы нет. Live-профиль, полученный через
`GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort`, **не сохраняется**; знания не пишутся в
`vk_user_profiles` никогда.

Источник `Темы чата:` — `chat_knowledge_summary` через `FindChatKnowledgeSummaryPort(peerId)`; контекст
запрашивающего — `vk_user_profiles` и `user_facts`/`user_knowledge_summary` через
`FindStoredUserProfilesByIdsPort`/`FindUserKnowledgePort`. Все четыре чтения только читают.

Данные профиля, истории, статистики, знаний (значения фактов и текст сводки), сводки чата, профиля и
знаний запрашивающего, а также исходный вопрос уходят в LLM-промпт — общий PII-контур (§«PII и логи» в
[`docs/development/configuration.md`](../development/configuration.md); ADR 2026-11-10-profile-dossier, ADR 2026-11-05-llm-provider-vllm-openai).
Новый код значения не логирует: логи — ERROR при отказе LLM и WARNING при сбое чтения знаний (только
`peerId`, `targetId` и простое имя класса исключения). Собственных метрик и спанов нет.

## Journey

`journey-profile`, шаг `profile-dossier`: триггер — ветка `profile` шага `answer-decision` (событие
`PROFILE_REQUESTED` после `requiredAnswer=true`), дальше — `vk-message-send`; транспорт событий —
`event-dispatch`. В групповом чате запрос достижим только после `requiredAnswer=true` (упоминание бота или
forward от бота) — named gap, а не дефект досье. В `journey-answer` ветка ответа не перестраивается.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| событие `PROFILE_REQUESTED` и payload — доменный `Message` | producer — `answer-decision`; имя и routing key — `event-dispatch` | `answer-decision` (producer), `event-dispatch` (каталог `Event`) |
| событие `ANSWER_MESSAGE`, payload `ResponseMessage` | consumer — `vk-message-send`; producers — `ai-answer`, `answer-decision`, `chat-news-digest`, `profile-dossier` | `ai-answer` (контракт/payload не меняется) |
| `PublishEventCommand`/`PublishEventOutputPort` | все публикующие возможности | `event-dispatch` |
| `FindChatAuthorsPort`, `FindStoredUserStatisticsPort`, `FindStoredUserProfileHistoryPort` | реализация — `persistence`, потребитель — `ai` | `message-storage` (реализация и схема) |
| `ChatAuthor`, `UserStatistics`, `ProfileHistoryEntry` | контракт чтения для `ai` | `message-storage` (модели в `share`) |
| `FindStoredUserProfilesByIdsPort`, `FindStoredGroupProfilesByIdsPort` | `message-storage`, `ai-answer`, `conversation-summary` | `message-storage` |
| `FindUserKnowledgePort`, `UserKnowledge` | read-модель знаний (`knowledge-confidence`); потребители — `profile-dossier` (`ProcessProfileCommandImpl`, для цели и для запрашивающего), `ai-answer` (`GenerateAnswerCommand`), `chat-news-digest` (`ProcessNewsDigestCommandImpl`), `user-knowledge` (`GenerateUserKnowledgeSummaryCommandImpl`), `daily-greetings` (`GenerateGreetingTextCommandImpl`) | `knowledge-contracts` (форма), `knowledge-confidence` (чтение) |
| `FindChatKnowledgeSummaryPort`, `ChatKnowledge` | `profile-dossier` (темы чата), `chat-news-digest`, `daily-greetings`, `ai-answer` (по решению) | `knowledge-contracts` (форма), `knowledge-confidence` (чтение) |
| `UserKnowledgeSummaryFormatter` | единственная реализация маркеров знаний; рендерит список фактов в секции `Из общения:` (`knowledge-summary`) | `knowledge-summary` |
| `FindUserBirthdayFactsPort` | birthday-ветка поздравлений (`daily-greetings`) | `knowledge-confidence` |
| `GetUserProfilesBatchPort`, `GetGroupProfilesBatchPort` | `message-storage` (актуализация профилей), `profile-dossier` (досье без сохранённого профиля) | `vk-profiles` |
| `ProfileAnswerGenerationPort` и `ProfileAnswerGenerator`/`ProfileAiAdapter` | потребитель — только `ProcessProfileCommandImpl` | `ai` |
| `ProfileGenerationException` | ловится только `ProcessProfileCommandImpl` | `ai` (единая иерархия `VkBotAppException`, ADR 2026-11-09-exception-hierarchy) |
| `aiProfileSystemprompt` | читает `ProfileAnswerGenerator` | composition root `:src:app` (ADR 2026-10-28-configuration-and-secrets) |

## Зависит от

- `answer-decision` — producer события `PROFILE_REQUESTED` (ветка `profile` после `requiredAnswer=true`).
- `message-storage` — in-process чтение авторов чата, статистики и истории профиля; схема и данные.
- `user-knowledge` — `FindUserKnowledgePort`, `FindChatKnowledgeSummaryPort` и
  `UserKnowledgeSummaryFormatter` для секций `Из общения:`, `Темы чата:`, `Знания запрашивающего:` (знания
  и сводка только читаются, в VK-профиль не пишутся).
- `vk-profiles` — live-порты `GetUserProfilesBatchPort`/`GetGroupProfilesBatchPort` для отсутствующего
  сохранённого профиля (ответ не сохраняется).
- `event-dispatch` — доставка события и публикация `ANSWER_MESSAGE`.
- внешний LLM — через LangChain4j AI-сервис (ADR 2026-11-05-llm-provider-vllm-openai).

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — command/usecase без фреймворка, вход только через `port.input`.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — шаг в событийном потоке, чтение данных через in-process `share`-порты.
- ADR 2026-10-28-single-deployable-app-module — своя вертикаль внутри `:src:ai`, без нового модуля и деплоя.
- ADR 2026-11-05-llm-provider-vllm-openai — LLM через OpenAI-совместимый API, PII-риск провайдерских логов.
- ADR 2026-11-09-exception-hierarchy — `ProfileGenerationException` в единой иерархии `VkBotAppException`.
- ADR 2026-11-10-news-digest — образец формы вертикали и поведения «одна публикация из команды, фолбэк вместо retry брокера».
- ADR 2026-11-10-vk-profile-human-details — `VkUserProfile.details` — человеческие значения VK-полей, allowlist рендера.
- ADR 2026-11-10-user-statistics-materialization — материализованные агрегаты статистики, читаемые досье.
- ADR 2026-11-10-profile-dossier — решение «досье»: детерминированный резолв, LLM только текст, один новый output-порт, PII-egress, always-on.
- ADR 2026-11-11-knowledge-read-model — форма `FindUserKnowledgePort` и `summaryFresh`: досье читает карточки и сводку одним вызовом.
- ADR 2026-11-11-profile-knowledge-overlay — overlay на чтении: секция `Из общения:`, приоритет свежей сводки, fail-open опционального чтения; `Amends` ADR 2026-11-10-profile-dossier.
- ADR 2026-11-13-profile-dossier-context — знания цели глобальны по пользователю, `Темы чата:` и контекст
  запрашивающего (`Кто запрашивает:`, `Знания запрашивающего:`) с правилом «только человек, не бот, не
  цель» и fail-open каждого чтения; `Amends` ADR 2026-11-10-profile-dossier и ADR
  2026-11-11-profile-knowledge-overlay.

## Acceptance criteria

- **AC-profile-dossier-1** — КОГДА в тексте ровно один пользовательский mention `[idN|label]` (N > 0,
  N ≠ `vk.bot.id`, label ≠ `vk.bot.mention`) и нет пересылки и «обо мне», СИСТЕМА ДОЛЖНА взять
  `ProfileTarget.User(N)`, собрать досье, вызвать LLM один раз и опубликовать ровно один `ANSWER_MESSAGE` с
  текстом модели и `responseTo = исходное Message`. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`resolves a single user mention and publishes one answer`),
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileTargetResolverTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileTargetResolverTest.kt)
  (`resolves a bare mention token`, `ignores the zero id token`, `collapses duplicate mentions of the same id`).
- **AC-profile-dossier-2** — КОГДА в тексте ровно один групповой mention `[clubN|label]` или
  `[publicN|label]` (N ≠ `vk.group.id`, label ≠ `vk.bot.mention`) и нет пересылки и «обо мне», СИСТЕМА
  ДОЛЖНА взять `ProfileTarget.Group(-N)`, собрать досье о группе (без статистики и истории) и опубликовать
  ровно один `ANSWER_MESSAGE`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`resolves a group mention to a negative group id` — обе формы, `club` и `public`).
- **AC-profile-dossier-3** — КОГДА явного mention нет и `forwardedMessages` не пуст, СИСТЕМА ДОЛЖНА взять
  последнее по списку пересланное сообщение с `fromId ≠ vk.bot.id` (`User` при `fromId > 0`, `Group` при
  `fromId < 0`); если все пересылки от бота — перейти к следующим шагам. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`uses the top forwarded message author when no mention is present`,
  `uses the last forwarded message author`,
  `uses a forwarded group message author as a group`,
  `skips a forwarded bot message`).
- **AC-profile-dossier-4** — КОГДА цели ещё нет и нормализованный текст содержит `обо мне` или
  `про меня`, СИСТЕМА ДОЛЖНА взять `ProfileTarget.User(message.fromId)`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`resolves the phrase about me to the message author`).
- **AC-profile-dossier-5** — КОГДА после вырезания триггеров остаётся фраза имени и она однозначно
  совпадает с одним участником (полное имя, падежная форма, `screen_name` без `@`), СИСТЕМА ДОЛЖНА взять
  этого `User`. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`matches a unique participant by full name and case form`),
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileNameMatcherTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileNameMatcherTest.kt)
  (`matches a full name written in another case form`, `matches the screen name without at sign`,
  `does not match a diminutive outside the prefix`).
- **AC-profile-dossier-6** — КОГДА фраза имени совпадает с 2 и более участниками, СИСТЕМА ДОЛЖНА
  опубликовать ровно одно уточнение со списком не более 10 кандидатов в формате
  `N) Имя Фамилия (@screen_name, id<fromId>)` и НЕ вызывать LLM; КОГДА фраза имени не совпала ни с кем —
  «Среди участников этого чата не нашёл «<фраза>». Напишите полное имя, @упоминание, id или перешлите
  сообщение.»; КОГДА цели нет вообще — «Уточните, о ком речь: имя, @упоминание, id или пересланное
  сообщение.»; оба раза без LLM. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`asks to clarify when several participants match`, `lists at most ten candidates`,
  `reports not found for an unknown name`, `asks for a target when nothing is resolvable`).
- **AC-profile-dossier-7** — КОГДА цель определена и профиль есть, СИСТЕМА ДОЛЖНА собрать LLM-вход из
  профиля (typed + `details`), статистики, последних 20 записей истории и числа сообщений цели в этом чате,
  вызвать LLM один раз, опубликовать ровно один `ANSWER_MESSAGE` и не писать в БД (в команде нет
  write-порта). Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`feeds profile, statistics and history into the model and publishes its answer`,
  `the command constructor has no write port`),
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt)
  (`answer is built from the stored dossier and writes nothing`).
- **AC-profile-dossier-8** — КОГДА LLM-порт отказал после 3 попыток (включая пустой ответ), СИСТЕМА
  ДОЛЖНА опубликовать «Не удалось собрать профиль. Попробуйте позже.», записать ERROR только с `peerId` и
  простым именем класса и не пробрасывать исключение в брокер; `ProfileAiAdapter` после 3 неудач ДОЛЖЕН
  бросить `ProfileGenerationException` со статусом `EXTERNAL_FAILURE`. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`publishes the fallback when the model fails and logs no PII`),
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/profile/ProfileAiAdapterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/profile/ProfileAiAdapterTest.kt)
  (`execute retries three times and rethrows`, `execute treats a blank answer as a failed attempt`).
- **AC-profile-dossier-9** — КОГДА у определённого пользователя нет сохранённого профиля, СИСТЕМА ДОЛЖНА
  запросить `GetUserProfilesBatchPort` и не сохранять ответ; КОГДА профиль не получен и оттуда —
  «Профиль недоступен.» без LLM; то же для группы через `GetGroupProfilesBatchPort`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`fetches a missing profile through the vk port and reports when it is unavailable`,
  `fetches a missing group profile through the vk port`).
- **AC-profile-dossier-10** — КОГДА конверт `PROFILE_REQUESTED` получен из очереди, СИСТЕМА ДОЛЖНА
  разобрать JSON-payload в доменный `Message` и ровно один раз вызвать `ProcessProfileInputPort`;
  `event()` ДОЛЖЕН вернуть `Event.PROFILE_REQUESTED`. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/ProfileRequestedEventProcessorTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/input/event/ProfileRequestedEventProcessorTest.kt)
  (`parses the message payload and calls the input port`, `declares the PROFILE_REQUESTED event`).
- **AC-profile-dossier-11** — КОГДА единственный mention в тексте — собственный mention бота (label равен
  `vk.bot.mention`, или id равен `vk.bot.id`, или `-id` равен `vk.group.id`), СИСТЕМА ДОЛЖНА отбросить его
  как цель и опубликовать «Уточните, о ком речь: имя, @упоминание, id или пересланное сообщение.» без
  вызова LLM; КОГДА форматтер собирает текст досье, СИСТЕМА ДОЛЖНА рендерить только allowlist-ключи
  `details` с русскими подписями и НЕ рендерить `mobile_phone`, `home_phone`, `counters`, `connections`,
  неизвестные ключи и VK-id, пропускать пустые секции и записи истории без известных полей. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`ignores a mention of the bot itself`),
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt)
  (`renders allowlisted details and skips phones and unknown keys`, `omits empty sections`).
- **AC-profile-dossier-12** — КОГДА изменение доставлено, СИСТЕМА ДОЛЖНА содержать этот capability-файл со
  статусом `delivered` и id `profile-dossier` (AC с путями тестов), строку и journey `journey-profile` в
  [`docs/capabilities/index.md`](./index.md), ADR 2026-11-10-profile-dossier и строку в [`docs/README.md`](../README.md), обновлённые
  [`docs/capabilities/answer-decision.md`](./answer-decision.md), [`docs/capabilities/event-dispatch.md`](./event-dispatch.md),
  [`docs/capabilities/message-storage.md`](./message-storage.md), [`docs/architecture/modules.md`](../architecture/modules.md), [`docs/architecture/data-flow.md`](../architecture/data-flow.md),
  [`docs/architecture/overview.md`](../architecture/overview.md), [`docs/development/configuration.md`](../development/configuration.md),
  [`docs/development/deployment.md`](../development/deployment.md), [`README.md`](../../README.md) и ключ `aiProfileSystemprompt` в
  [`src/app/src/main/resources/application.yml`](../../src/app/src/main/resources/application.yml); проверка — [`./scripts/check-capabilities.sh`](../../scripts/check-capabilities.sh) exit 0, а
  проводка и конфигурация на реальном деплое — Test:
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt)
  (`sends the configured system prompt with the dossier data`).
- **AC-profile-dossier-13** — КОГДА изменение доставлено целиком, СИСТЕМА ДОЛЖНА пройти полный гейт
  `./gradlew build` (compile, tests, detekt, ktlint, coverage, capabilities, quarkusBuild) с exit 0 при
  доступном Docker; гейт прогоняет все тесты изменения, включая
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt) и
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt).

- **AC-PROFILE-1** — КОГДА у цели есть знания из чата, СИСТЕМА ДОЛЖНА показать их секцией `Из общения:`
  сразу после `Профиль:` и перед `Статистика:`: свежая сводка (`summaryFresh=true`, непустой текст) —
  связным текстом как есть; иначе — списком фактов через `UserKnowledgeSummaryFormatter` (`CONFIRMED`
  обычной строкой, `CANDIDATE` — «возможно», `HISTORICAL` — «ранее: X», `conflict` — «возможно,
  противоречивые данные», `stale` исключён) без источников, `factKey`, id и дат; при пустых знаниях или
  только stale-фактах без свежей сводки секции нет, а `GroupDossier` рендерится прежним текстом. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt)
  (`rendersFreshSummaryFromChatBelowProfile`, `fallsBackToFactListWhenSummaryIsStaleOrMissing`,
  `omitsKnowledgeSectionWithoutFreshKnowledge`, `rendersKnowledgeWithoutSourcesOrDates`); команда
  `./gradlew --console=plain :src:ai:test --tests "*ProfileDossierFormatter*"`.
- **AC-PROFILE-2** — КОГДА цель — пользователь с профилем, СИСТЕМА ДОЛЖНА ровно один раз прочитать его
  знания через `FindUserKnowledgePort` и отдать LLM обе версии (VK-значение в `Профиль:`, значение из чата
  в `Из общения:`); для группы порт не вызывается; при недоступном профиле знаний нет и LLM не
  вызывается; при сбое порта — ответ по досье без секции и один WARNING с `peerId`, `targetId` и именем
  класса без значений; `vk_user_profiles`/`user_facts`/`user_knowledge_summary`/`messages` не пишутся.
  Tests: [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`feedsBothVkAndChatValuesIntoTheModelForAUser`, `doesNotReadKnowledgeForAGroup`,
  `doesNotReadKnowledgeWhenTheProfileIsUnavailable`, `publishesTheProfileWithoutKnowledgeWhenTheReadFails`),
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt)
  (`readsChatKnowledgeIntoThePromptWithoutWriting`); команды `./gradlew --console=plain :src:ai:test
  --tests "*ProcessProfile*"` и `./gradlew --console=plain :src:app:test --tests
  "*ProfileCommandIntegration*"`.
- **AC-PROFILE-3** — КОГДА вместе с текущей карточкой того же ключа в знаниях есть `HISTORICAL` или две
  карточки одного ключа помечены `conflict=true`, СИСТЕМА ДОЛЖНА вывести историю строкой `- ранее:
  <значение>` без дат, а конфликт — обеими строками `- возможно, противоречивые данные: <значение>` без
  выбора победителя и без дат. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt)
  (`rendersHistoricalFactAsPreviously`, `rendersConflictingFactsAsContradictory`); команда
  `./gradlew --console=plain :src:ai:test --tests "*ProfileDossierFormatter*"`.

- **AC-PROFILE-4** — КОГДА у цели есть знания, а у чата — сохранённая сводка, СИСТЕМА ДОЛЖНА читать
  знания цели глобально по `userId` (без фильтра `peer_id`), добавить секцию `Темы чата:` со сводкой чата
  как есть и не менять `Из общения:`; при пустой сводке секции нет. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt)
  (`renders chat topics and the requester context after the target knowledge`),
  [`src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt`](../../src/app/src/test/kotlin/com/simarel/vkbot/integration/ProfileCommandIntegrationTest.kt)
  (`answer includes facts learned in another chat and the chat topics`); команды
  `./gradlew --console=plain :src:ai:test --tests "*ProfileDossierFormatter*"` и
  `./gradlew --console=plain :src:app:test --tests "*ProfileCommandIntegration*"`.
- **AC-PROFILE-5** — КОГДА запрос пришёл от человека, который не является целью, СИСТЕМА ДОЛЖНА добавить
  секции `Кто запрашивает:` (allowlist-рендер его профиля) и `Знания запрашивающего:` (свежая сводка либо
  список фактов) после `Темы чата:`; КОГДА запрашивающий — бот или совпадает с целью, контекст не
  читается и секций нет; для группы контекст запрашивающего рендерится тем же правилом. Tests:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/domain/profile/ProfileDossierFormatterTest.kt)
  (`renders chat topics and the requester context after the target knowledge`,
  `renders requester context in a group dossier without statistics`),
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`readsRequesterProfileAndKnowledgeForAUser`, `doesNotReadRequesterContextForTheTarget`,
  `doesNotReadRequesterContextForTheBot`).
- **AC-PROFILE-6** — КОГДА чтение сводки чата или контекста запрашивающего бросило `RuntimeException`,
  СИСТЕМА ДОЛЖНА записать ровно один WARNING без значений (соответственно
  `Profile chat knowledge read failed for peerId=<peerId>` и
  `Profile requester read failed for peerId=<peerId> requesterId=<id>`) и опубликовать досье без
  соответствующей секции; `InterruptedException` восстанавливает флаг и пробрасывается. Test:
  [`src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt`](../../src/ai/src/test/kotlin/com/simarel/vkbot/ai/command/profile/ProcessProfileCommandImplTest.kt)
  (`publishesTheProfileWithoutChatTopicsWhenTheReadFails`,
  `publishesTheProfileWithoutRequesterContextWhenTheReadFails`,
  `publishesTheProfileWithoutKnowledgeWhenTheReadFails`).

## Пробелы приёмки

- **Сопоставление по имени — эвристика префикса/основы:** lowercase, `ё → е`, срезание хвоста гласных и
  `й/ь/ъ`, затем префикс. Уменьшительные вне префикса не находятся: `Паша` ≠ `Павел`; ложные совпадения
  решаются уточнением, состояние не запоминается.
- **Группы** ищутся только по `club`/`public`-id или пересылке; по голому имени группа не находится.
- **Allowlist `details`:** телефоны, контакты, `counters`, `connections`, вложенные списки/карты
  (`career`, `universities`, `schools`, `relatives`) и неизвестные ключи не рендерятся вовсе — список
  кандидатов уточнения исключение (там печатается `id<fromId>` как подсказка выбора).
- **At-least-once:** состояния и дедупликации нет; повторная доставка события может дать повторный ответ.
  VK дедуплицирует только одинаковый текст через `random_id`, а текст LLM может отличаться.
- **Кэша ответов и rate limit нет:** LLM-вызов (до трёх попыток порта) на каждый запрос; условие
  апгрейда — cooldown на `(peerId, userId)` при измеренном абьюзе.
- **Live-профиль не сохраняется:** у живого ответа VK нет истории и материализованной статистики; досье по
  нему короче, а следующий запрос повторит вызов VK.
- **В групповом чате запрос достижим только после `requiredAnswer=true`** (упоминание бота или forward от
  бота) — следствие решения «только интент decision-модели», а не дефект резолва.
- **PII-egress:** профиль, история, статистика (включая активность из чужих чатов) и исходный вопрос уходят
  в LLM-промпт — общий PII-контур (§«PII и логи» в [`docs/development/configuration.md`](../development/configuration.md); принятый риск
  ADR 2026-11-10-profile-dossier); новый код значения не логирует.
- **Наблюдаемость:** новых метрик и спанов нет; логи — ERROR при отказе LLM и WARNING при сбое чтения
  знаний (только `peerId`, `targetId` и простое имя класса исключения). Отдельного INFO-лога успеха нет.
- **Контекст запрашивающего — это его собственные данные в чужом чате:** в групповом чате ответ читают
  все, поэтому профиль и знания автора запроса видны участникам; это осознанный компромисс «ответ под
  автора», а не утечка. Апгрейд — не показывать контекст запрашивающего в групповых чатах, если понадобится.
- **Сводка чата — свободный LLM-текст:** запрет инструкций — правило промпта, а не структурная изоляция;
  prompt-injection через тело сводки не исключён (как и у остальных знаний).
- **Fail-open только для опционального чтения знаний:** сбой `FindUserKnowledgePort` не валит команду и
  не ретраится брокером — досье публикуется без секции `Из общения:`. Ошибки профиля, статистики,
  истории, публикации и LLM по-прежнему уходят в retry-лестницу (`Amends` ADR 2026-11-10-profile-dossier).
- **`Из общения:` без дат гарантируется рендером, а не вырезанием:** текст свежей сводки — свободный
  LLM-ответ, промпт сводки запрещает источники/даты/id, но программа даты из чужого текста не вырезает
  (это портило бы связный текст); список фактов дат не содержит по построению. Апгрейд — нормализатор
  дат, если понадобится программная гарантия.
- **Устаревшая сводка не показывается:** при `summaryFresh=false` секция строится только из фактов; если
  свежих фактов нет, секции нет вовсе (следствие решения «свежая сводка приоритетнее» и `summaryFresh`
  read-модели). Апгрейд — решение человека показывать текст устаревшей сводки.
