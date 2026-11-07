# ADR 0009: Ежедневные поздравления — расписание 00:00, at-most-once через `greeting_log`

- **Статус:** accepted
- **Дата:** 2026-11-07
- **Supersedes:** —

## Контекст

Нужна возможность, которая раз в сутки в 00:00 по `Europe/Minsk` поздравляет чаты VK: Новый год (1 января),
День программиста (256-й день года) и дни рождения участников из `vk_user_profiles.birth_date`. Праздники
уходят во все групповые чаты с перепиской (`peer_id > 2 000 000 000`), именинник — в каждый чат, где он
писал, включая личные переписки. Если в чате на дату несколько событий, уходит одно сообщение. Текст
генерирует ИИ; поздравление нельзя терять при отказе ИИ. Ручного REST-триггера нет — доказательство
поведения дают тесты.

Ограничения: не добавлять `quarkus-scheduler`, не менять `share`-порты, `persistence`, `ai`, `vk-facade`,
схему БД, `MessageSendUsecase`/`ANSWER_MESSAGE` и Long Poll; интеграционный тест — отдельная задача.

## Решение

- **Расписание.** `:src:greetings` — гексагональный модуль, единственная production-зависимость `:src:share`.
  `DailyGreetingRunner` (`adapter/input/schedule/DailyGreetingRunner.kt`) стартует виртуальный поток по
  `StartupEvent` и останавливается по `ShutdownEvent`, как `LongPollRunner` в `receiver`. Цель — ближайшая
  полночь строго после `now` (`NextRun.after`), задержка пересчитывается в цикле ожидания, поэтому ранний
  wake не теряет и не дублирует дату. Старт после 00:00 ждёт следующие сутки (catch-up нет). Зона — ключ
  `greetings.timezone` (дефолт `Europe/Minsk`), включение — `greetings.enabled` (дефолт `true`).
- **Оркестрация.** `RunDailyGreetingsUsecase`: `CollectGreetingPlansCommand` (план по чатам) →
  `GenerateGreetingTextCommand` (ИИ через `GenerateGreetingTextPort`) → `ClaimDailyGreetingPort` →
  `SendGreetingMessagePort`. Один `GreetingPlan` на чат, одно сообщение. Ошибка claim/отправки одного чата
  логируется как ERROR и учитывается в `failed`, остальные чаты продолжаются; `InterruptedException`
  (Shutdown) пробрасывается. Генерация тотальна: при исключении порта или пустом/пробельном ответе
  возвращается детерминированный шаблон и пишется WARN.
- **At-most-once.** `ClaimDailyGreetingPort` пишет строку `greeting_log` (PK `(greeting_date, peer_id)`,
  changeset `006-create-greeting-log-table.yaml`) до отправки; `JooqGreetingLogRepository.claim` — один
  autocommit `INSERT … ON CONFLICT DO NOTHING`. Повторный прогон даты получает `claimed = false` и не
  отправляет; два экземпляра проигрывают конфликт. Цена: ошибка отправки сжигает чат на день.
- **Праздники и дни рождения — код, не конфиг.** `Holidays` знает 1 января и `dayOfYear == 256`;
  `BirthdayDates` запрашивает `(day, month)` и добавляет `(29, 2)` к 1 марта невисокосного года;
  `NameFormatter` рендерит «Имя Фамилия» → `screen_name` → id. Матчинг строки `birth_date` остаётся в
  `persistence` (`BirthDateMatcher`), модуль получает готовые профили.
- **Отправка.** `SendGreetingMessagePort` ведёт в плоскую ветку `vk-facade`
  (`SendGreetingMessageInProcessAdapter` → `PlainMessageSendUsecase`): `forwardedMessages = null`,
  детерминированный `random_id` от текста и peer. `MessageSendUsecase` и `ANSWER_MESSAGE` не меняются.
- **Наблюдаемость.** Один OTel-спан `greetings.daily-run` на границе раннера: на успехе статус `OK` и
  атрибуты `date`/`planned`/`sent`/`failed`, на ошибке — статус `ERROR` и только `date`; цикл логирует
  ERROR и ждёт следующую полночь. Метрик нет. Логи — `System.Logger` полем класса.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| `quarkus-scheduler` `@Scheduled(cron=…)` | Планировщик внутри каркаса, «ровно один вызов» зависит от его семантики и часов, юнит-тест требует Quarkus; виртуальный поток + `NextRun` — ~40 строк своего цикла без новой зависимости |
| Отправка через `ANSWER_MESSAGE`/`MessageSendUsecase` | Use case формирует forward группового чата; поздравлению нужна плоская отправка, поэтому доставлена отдельная ветка |
| Парсинг `birth_date` в SQL | Формат строки терпимый (`D.M`, `DD.MM`, `D.M.YYYY`), SQL разошёлся бы с `BirthDateMatcher`; парсинг остаётся в `persistence` |
| Retry/backoff и типизированный код VK в этом таске | Надёжная доставка `messages.send` — отдельная задача; здесь ошибка VK = пропуск чата на день |
| At-most-once в памяти | Теряется на рестарте и не работает для двух экземпляров; строка `greeting_log` — доставленная зависимость |
| Метрики (Micrometer) | Спан и логи покрывают эксплуатацию; метрики — отдельная задача |
| Праздники из env | Список закрыт и меняется кодом; конфиг добавил бы нетипизированные строки |

## Последствия

- Новый модуль `:src:greetings` входит в allowlist arch-тестов и в `coverageModules`; `:src:app` —
  composition root, ключи читаются из `src/app/src/main/resources/application.yml`.
- Первый прогон после деплоя — живой (дефолт `GREETINGS_ENABLED=true`), его ограничивают at-most-once,
  один прогон в сутки и тестовые профили с `greetings.enabled: false`.
- Известные ограничения (приняты):
  - ошибка VK/claim сжигает чат на день; апгрейд — когда появится требование надёжной доставки
    `messages.send` с bounded retry;
  - `greeting_log` не чистится (1 строка на чат в день); апгрейд — при требовании ретенции или росте таблицы;
  - при переводе системных часов/смене зоны отдельной защиты нет; задержка пересчитывается каждую итерацию,
    зона фиксирована конфигом;
  - перезапуск после 00:00 не догоняет день (catch-up нет);
  - имена и `birth_date` попадают в промпт ИИ и в pre-existing sink'и LLM/REST/трейсов
    (`quarkus.langchain4j.log-requests/log-responses: true`, `quarkus.rest-client.logging.scope:
    request-response`, `quarkus.langchain4j.tracing.include-prompt/include-completion: true` при включённых
    OTel-трейсах); собственные sink'и прогона — user id на WARN профиля без чатов, peer id и класс
    исключения на INFO/ERROR, message исключения в статусе спана; имена и текст сообщения не логируются,
    конфиг логирования в этом проходе не меняется;
  - остаточный prompt-injection: имена — untrusted user data, у `GreetingAiService` нет tools/memory, но
    текст промпта не санитайзится.
