# 2026-11-07: Ежедневные поздравления — cron-расписание, at-most-once через `greeting_log`

- **Статус:** accepted
- **Дата:** 2026-11-07
- **Supersedes:** —

## Контекст

Нужна возможность, которая раз в сутки поздравляет чаты VK: Новый год (1 января), День программиста
(256-й день года) и дни рождения участников из `vk_user_profiles.birth_date`. Праздники уходят во все
групповые чаты с перепиской (`peer_id > 2 000 000 000`), именинник — в каждый чат, где он писал, включая
личные переписки. В чате нужен отдельный текст на каждый праздник и общий текст на всех именинников даты;
разные праздники одного дня — разные сообщения. Текст генерирует ИИ, а поздравление нельзя терять при
отказе ИИ. Ручного REST-триггера нет — доказательство поведения дают тесты.

Задача допускает `quarkus-scheduler` и новые `share`-порты: планировщик из каркаса и контракты под
поздравления — часть доставки, а не обход архитектуры.

## Решение

- **Расписание.** `:src:greetings` — гексагональный модуль, единственная production-зависимость `:src:share`;
  расширение `quarkus-scheduler` объявлено в модуле. `DailyGreetingRunner`
  (`adapter/input/schedule/DailyGreetingRunner.kt`) — тонкий `@ApplicationScoped`-бин:
  `@Scheduled(identity = "daily-greetings", cron = "{greetings.cron}", timeZone = "{greetings.timezone}")`
  вызывает `runOnce(LocalDate.now(zone))`. Cron задаётся ключом `greetings.cron` (дефолт `0 0 0 * * ?`,
  env `GREETINGS_CRON`); значение `off`/`disabled` выключает расписание. Зона — `greetings.timezone`
  (дефолт `Europe/Minsk`, env `GREETINGS_TIMEZONE`), невалидная зона валит старт бина. Отдельного
  виртуального потока, `StartupEvent`/`ShutdownEvent` и расчёта ближайшей полуночи в коде нет: cron,
  зона, выключение и пропуск срабатывания — семантика планировщика Quarkus.
- **Оркестрация.** `RunDailyGreetingsUsecase`: `CollectGreetingPlansCommand` (план по чатам) →
  `ClaimDailyGreetingPort` (один раз на чат) → для каждого плана `GenerateGreetingTextCommand` (ИИ через
  `GenerateGreetingTextPort`) и `SendGreetingMessagePort` (по одному вызову на план). `GreetingPlan` — либо
  `HolidayGreeting(peerId, holiday)` (один план на праздник), либо `BirthdayGreeting(peerId, people)`
  (один план на всех именинников чата, имена отсортированы). Планы чата группируются, claim берётся до
  отправки всех его сообщений. Ошибка claim/отправки логируется как ERROR и учитывается в `failed`,
  остальные сообщения и чаты продолжаются; `InterruptedException` (остановка приложения) пробрасывается
  с сохранённым флагом. Генерация тотальна: при любом другом исключении порта или пустом/пробельном ответе
  возвращается детерминированный шаблон и пишется WARN; `InterruptedException` не подменяется шаблоном.
- **At-most-once.** `ClaimDailyGreetingPort` пишет строку `greeting_log` (PK `(greeting_date, peer_id)`,
  changeset `006-create-greeting-log-table.yaml`) один раз на чат до отправки; `GreetingLogTable.claim`
  — один autocommit `INSERT … ON CONFLICT DO NOTHING`. Повторный прогон даты получает `claimed = false` и
  не отправляет ни одного сообщения этого чата; два экземпляра проигрывают конфликт. Цена: ошибка
  отправки не повторяется, оставшиеся сообщения чата теряются до следующих суток.
- **ИИ-контракт.** `GenerateGreetingTextPort.Request(date, occasion, people)`: `occasion` — заголовок
  праздника (`Holiday.title`) или `День рождения`, `people` — отсортированные имена именинников или
  пустой список для праздника («все»). `GreetingTextOutputAdapter` подставляет дату, повод и список в
  промпт; праздники в промпте не хардкодятся и приходят только через `occasion`.
- **Праздники и дни рождения — код, не конфиг.** `Holidays` знает 1 января и `dayOfYear == 256`;
  `BirthdayDates` запрашивает `(day, month)` и добавляет `(29, 2)` к 1 марта невисокосного года;
  `NameFormatter` рендерит «Имя Фамилия» → `screen_name` → id. Матчинг строки `birth_date` остаётся в
  `persistence` (`BirthDateMatcher`), модуль получает готовые профили.
- **Отправка.** `SendGreetingMessagePort` ведёт в плоскую ветку `vk-facade`
  (`SendGreetingMessageInProcessAdapter` → `PlainMessageSendUsecase`): `forwardedMessages = null`,
  детерминированный `random_id` от текста и peer. `MessageSendUsecase` и `ANSWER_MESSAGE` не меняются.
- **Наблюдаемость.** Один OTel-спан `greetings.daily-run` на прогон в `runOnce`: на успехе статус `OK` и
  атрибуты `date`/`planned`/`sent`/`failed`, на ошибке — статус `ERROR` и только `date`, исключение
  пробрасывается планировщику; `InterruptedException` не помечается `ERROR`. Метрик нет. Логи —
  `System.Logger` полем класса.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Виртуальный поток + собственный цикл ожидания полуночи | Дублирует планировщик: cron, зона, выключение и пропуск срабатывания — его семантика; своей машинерии не нужно |
| Одно сообщение на все события чата | Требование — отдельное поздравление на каждый праздник; все именинники одной даты — одно сообщение, разные праздники — разные |
| Отправка через `ANSWER_MESSAGE`/`MessageSendUsecase` | Use case формирует forward группового чата; поздравлению нужна плоская отправка, поэтому доставлена отдельная ветка |
| Парсинг `birth_date` в SQL | Формат строки терпимый (`D.M`, `DD.MM`, `D.M.YYYY`), SQL разошёлся бы с `BirthDateMatcher`; парсинг остаётся в `persistence` |
| Retry/backoff и типизированный код VK в этом таске | Надёжная доставка `messages.send` — отдельная задача; здесь ошибка VK = пропуск сообщений чата до следующих суток |
| At-most-once в памяти | Теряется на рестарте и не работает для двух экземпляров; строка `greeting_log` — доставленная зависимость |
| Метрики (Micrometer) | Спан и логи покрывают эксплуатацию; метрики — отдельная задача |
| Праздники из env | Список закрыт и меняется кодом; конфиг добавил бы нетипизированные строки |

## Последствия

- Новый модуль `:src:greetings` входит в allowlist arch-тестов и в `coverageModules`; `:src:app` —
  composition root, ключи читаются из `src/app/src/main/resources/application.yml`.
- `quarkus-scheduler` — ещё одно расширение каркаса, но не второй деплой и не второй раннер: расписание
  живёт в `:src:app` (ADR 2026-10-28-single-deployable-app-module).
- Первый прогон после деплоя — живой (дефолт `GREETINGS_CRON=0 0 0 * * ?`), его ограничивают at-most-once,
  один прогон в сутки и тестовые профили с `greetings.cron: disabled`.
- Известные ограничения (приняты):
  - ошибка VK/claim сжигает чат на день; апгрейд — когда появится требование надёжной доставки
    `messages.send` с bounded retry;
  - `greeting_log` не чистится (1 строка на чат в день); апгрейд — при требовании ретенции или росте таблицы;
  - пропущенный день не догоняется: catch-up за прошлые сутки нет;
  - имена и `birth_date` попадают в промпт ИИ и в pre-existing sink'и LLM/REST/трейсов
    (`quarkus.langchain4j.log-requests/log-responses: true`, `quarkus.rest-client.logging.scope:
    request-response`, `quarkus.langchain4j.tracing.include-prompt/include-completion: true` при включённых
    OTel-трейсах); собственные sink'и прогона — user id на WARN профиля без чатов, peer id и класс
    исключения на INFO/ERROR, message исключения в статусе спана; имена и текст сообщения не логируются,
    конфиг логирования в этом проходе не меняется;
  - остаточный prompt-injection: имена — untrusted user data, у `GreetingTextGenerator` нет tools/memory, но
    текст промпта не санитайзится.
