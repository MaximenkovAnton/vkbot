# daily-greetings — поздравления в чаты VK по расписанию

- **id:** `daily-greetings`
- **Статус:** delivered

## Что делает

По cron-расписанию `greetings.cron` (по умолчанию `0 0 0 * * ?`, то есть 00:00) в часовом поясе
`greetings.timezone` (по умолчанию `Europe/Minsk`, UTC+3) собирает поздравления и отправляет их в чаты VK:
Новый год (1 января), День программиста (256-й день года: 13 сентября в невисокосный, 12 сентября в
високосный) и дни рождения участников из `vk_user_profiles.birth_date` или из подтверждённого
(`CONFIRMED`) факта `PROFILE_ATTRIBUTE/birthday`, если VK-дата скрыта или пуста. Праздники уходят во все групповые
чаты, где у бота есть переписка (`peer_id > 2 000 000 000`); именинник — в каждый чат, где он писал,
включая личные переписки с ботом. На каждый праздник в чате уходит отдельное сообщение, а все именинники
одной даты в одном чате получают одно общее сообщение; разные праздники одного дня — разные сообщения.
Текст генерирует ИИ через `GenerateGreetingTextPort` (дата, повод, список имён); при отказе ИИ или пустом
ответе уходит детерминированный шаблон, а не тишина. At-most-once держится на строке `greeting_log`
(`ClaimDailyGreetingPort`): claim один на пару `(date, peer)` и пишется до отправки всех сообщений чата,
поэтому повторный прогон той же даты не отправляет ничего. Ошибка одного чата логируется и не роняет
остальные.

Ручного REST-триггера нет: прогон запускается планировщиком Quarkus (`@Scheduled`), поведение доказывают
тесты модуля и `DailyGreetingsIntegrationTest`.

## Точки входа

| Точка входа | Где в коде |
|---|---|
| расписание: `@Scheduled(identity = "daily-greetings", cron = "{greetings.cron}", timeZone = "{greetings.timezone}")`, выключение — `off`/`disabled` | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/adapter/input/schedule/DailyGreetingRunner.kt` |
| сценарий прогона: план → claim на чат → на каждый план текст и отправка | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecase.kt` |
| входящий порт `Request(date)` → `Response(planned, sent, failed)` | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/port/input/RunDailyGreetingsInputPort.kt` |
| правила праздников и дней рождения | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/domain/Holidays.kt`, `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/domain/BirthdayDates.kt` |
| скрытый ДР: чтение `CONFIRMED`-фактов на месяц/день, объединение с VK-датой, дедуп по `userId`, fail-open | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommand.kt`, `src/share/src/main/kotlin/com/simarel/vkbot/share/port/output/persistence/FindUserBirthdayFactsPort.kt` |
| план чата: отдельный праздник или все именинники даты | `src/greetings/src/main/kotlin/com/simarel/vkbot/greetings/domain/GreetingPlan.kt` |
| ключи `greetings.cron`, `greetings.timezone`, `greetings.hidden-birthdays.enabled`, `aiGreetingSystemprompt` | `src/app/src/main/resources/application.yml` |
| решение | `docs/adr/2026-11-07-daily-greetings.md` |

## Данные

| Данные | Владелец | Как использует `daily-greetings` |
|---|---|---|
| `greeting_log` (changeset `006-create-greeting-log-table.yaml`) и семантика claim | `message-storage` | `ClaimDailyGreetingPort`; модуль не видит таблицу |
| `messages` (`peer_id`, `from_id`) | `message-storage` | `FindGroupChatPeerIdsPort`, `FindUserChatPeerIdsPort` |
| `vk_user_profiles.birth_date` | `message-storage` (схема), `vk-profiles` (заполняет) | `FindUserProfilesByBirthDatePort` |
| `VkUserProfile` и правило nullable-полей | `vk-profiles` | рендер имени именинника (`NameFormatter`) |
| `user_facts` (changeset `010-create-user-knowledge-tables.yaml`) | `knowledge-store` | `FindUserBirthdayFactsPort` — текущие `PROFILE_ATTRIBUTE/birthday`-карточки на месяц/день, `status` и `confidence` как есть |
| LLM и ключ `aiGreetingSystemprompt` | `ai-answer`/`:src:app` | `GenerateGreetingTextPort` |
| плоская отправка и `random_id` | `vk-message-send` | `SendGreetingMessagePort` |
| спан `greetings.daily-run` и логи прогона | `daily-greetings` | эксплуатация |

## Journey

- `journey-greetings` — срабатывание `greetings.cron` → план по чатам (`message-storage`) → claim на чат
  (`message-storage`) → текст (`ai-answer`) и отправка (`vk-message-send`) для каждого плана.
  Новых событий RabbitMQ и HTTP-входов нет: прогон запускается планировщиком, а не событием VK.

## Общие данные и контракты

| Общее | Кто ещё использует | Владелец |
|---|---|---|
| `FindGroupChatPeerIdsPort`, `FindUserChatPeerIdsPort` | только `daily-greetings` | `share` (контракт), `message-storage` (реализация) |
| `FindUserProfilesByBirthDatePort` | только `daily-greetings` | `share` (контракт), `message-storage` (реализация) |
| `FindUserBirthdayFactsPort` | только `daily-greetings` | `share` (контракт), `knowledge-store`/`persistence` (реализация) |
| `FindStoredUserProfilesByIdsPort` | `ai-answer`, `daily-greetings` | `share` (контракт), `message-storage` (реализация) |
| `ClaimDailyGreetingPort` и таблица `greeting_log` | только `daily-greetings` | `share` (контракт), `message-storage` (схема и реализация) |
| `GenerateGreetingTextPort` | только `daily-greetings` | `share` (контракт), `ai-answer` (реализация) |
| `SendGreetingMessagePort` | только `daily-greetings` | `share` (контракт), `vk-message-send` (реализация) |
| `VkUserProfile` | `message-storage`, `ai-answer`, `conversation-summary`, `daily-greetings` | `vk-profiles` |

## Зависит от

- `message-storage` — in-process порты чтения чатов/профилей и claim.
- `knowledge-store` — read-порт `FindUserBirthdayFactsPort` (текущие birthday-факты на месяц/день).
- `ai-answer` — `GenerateGreetingTextPort` (ИИ + ключ `aiGreetingSystemprompt`).
- `vk-message-send` — `SendGreetingMessagePort` (плоская отправка, `random_id`).

## ADR

- ADR 2026-10-28-module-boundaries-and-layer-rules — модуль видит только `share`, слои внутри модуля.
- ADR 2026-10-28-in-process-ports-instead-of-loopback-rest — in-process `share`-порты вместо loopback-REST.
- ADR 2026-11-07-daily-greetings — расписание через `quarkus-scheduler`, at-most-once `greeting_log`, один текст на праздник и
  один на именинников чата, шаблон-фолбэк, известные ограничения.
- ADR 2026-11-11-hidden-birthday-greetings — скрытый ДР из чата: read-порт с ключом «месяц/день»,
  объединение источников в `greetings`, дедуп по `userId`, kill-switch `greetings.hidden-birthdays.enabled`,
  fail-open knowledge-ветки; `Amends` ADR 2026-11-11-knowledge-read-model.

## Acceptance criteria

- **AC-dg-1** — КОГДА срабатывает расписание `greetings.cron` в зоне `greetings.timezone`, СИСТЕМА ДОЛЖНА
  выполнить ровно один прогон `RunDailyGreetingsInputPort.execute(Request(date = LocalDate.now(zone)))` на
  срабатывание; `greetings.cron = off` или `disabled` выключает расписание, а невалидная зона валит старт
  бина. Test: `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/adapter/input/schedule/DailyGreetingRunnerTest.kt`.
- **AC-dg-2** — КОГДА дата прогона 1 января, СИСТЕМА ДОЛЖНА включить `Holiday.NEW_YEAR` в план каждого чата
  из `FindGroupChatPeerIdsPort` — отдельным `GreetingPlan.HolidayGreeting`. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/domain/HolidaysTest.kt`,
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-3** — КОГДА `date.dayOfYear == 256`, СИСТЕМА ДОЛЖНА включить `Holiday.PROGRAMMER_DAY` в план
  каждого известного группового чата; 12.09 високосного и 13.09 невисокосного — да, 255-й и 257-й день —
  нет. Test: `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/domain/HolidaysTest.kt`,
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-4** — КОГДА `FindUserProfilesByBirthDatePort` вернул профили на `(day, month)` даты прогона,
  СИСТЕМА ДОЛЖНА добавить отформатированное имя в план каждого чата из `FindUserChatPeerIdsPort`, включая
  личный peer `< 2 000 000 000`; профиль без чатов — WARN и пропуск. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/domain/NameFormatterTest.kt`,
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-4a** — КОГДА профиль родился 29.02, СИСТЕМА ДОЛЖНА поздравлять его 29 февраля в високосный год и
  1 марта в невисокосный, никогда 28 февраля; на 1 марта невисокосного года запрашиваются `(1, 3)` и
  `(29, 2)` и объединяются в один план. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/domain/BirthdayDatesTest.kt`,
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-5a** — КОГДА в чате несколько событий на дату, СИСТЕМА ДОЛЖНА отправить отдельное сообщение на
  каждый праздник (`GreetingPlan.HolidayGreeting`) и одно сообщение на всех именинников чата
  (`GreetingPlan.BirthdayGreeting` с отсортированными именами); разные праздники одного дня — разные
  сообщения. Test: `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecaseTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-5b** — КОГДА план уходит в `GenerateGreetingTextPort`, запрос ДОЛЖЕН нести `date: LocalDate`,
  `occasion`: `Holiday.title` для праздника и `День рождения` для именинников, `people`: пустой список для
  праздника и отсортированные `String.compareTo` имена для именинников; адаптер рендерит пустой `people`
  как «все». Test: `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/text/GenerateGreetingTextCommandTest.kt`,
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/greeting/GreetingTextOutputAdapterTest.kt`.
- **AC-dg-5c** — КОГДА ИИ отказал, СИСТЕМА ДОЛЖНА вернуть детерминированный шаблон: «С Новым годом!» для
  Нового года, «С Днём программиста!» для Дня программиста, `С днём рождения, <имена через «, »>!` для
  именинников. Test: `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/text/GenerateGreetingTextCommandTest.kt`.
- **AC-dg-6** — КОГДА у чата нет ни праздника, ни именинника, СИСТЕМА ДОЛЖНА не создавать план и не
  вызывать claim/отправку; пустой набор планов даёт `Response(0, 0, 0)`. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecaseTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-7** — КОГДА формируется текст, СИСТЕМА ДОЛЖНА вызвать `GenerateGreetingTextPort`; при любом другом
  исключении или пустом/пробельном ответе — WARN и шаблон, прочие исключения команда не бросает;
  `InterruptedException` пробрасывается с сохранённым флагом. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/text/GenerateGreetingTextCommandTest.kt`,
  `src/ai/src/test/kotlin/com/simarel/vkbot/ai/adapter/output/ai/greeting/GreetingTextOutputAdapterTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-8** — КОГДА `ClaimDailyGreetingPort` вернул `claimed = false` для чата, СИСТЕМА ДОЛЖНА не
  отправлять ни одного сообщения этого чата и продолжить остальные чаты; claim вызывается ровно один раз на
  `(date, peer)` до отправки сообщений чата, повторный прогон даты не отправляет повторно; при смешанных
  claim уходят только незаклеймленные чаты. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecaseTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-9** — КОГДА claim или отправка одного сообщения бросили исключение, СИСТЕМА ДОЛЖНА записать ERROR
  с `peerId` и классом исключения, учесть в `failed` (ошибка claim — все сообщения чата, ошибка отправки —
  одно сообщение), продолжить остальные сообщения и чаты и не повторять; `InterruptedException`
  пробрасывается с сохранённым флагом. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecaseTest.kt`.
- **AC-dg-10** — КОГДА план обрабатывается, единственный исходящий вызов доставки — ровно один
  `SendGreetingMessagePort.Request(peerId, итоговый MessageText)` на план (праздник или именинники);
  плоская отправка и детерминированный `random_id` — доставленная зависимость. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/usecase/RunDailyGreetingsUsecaseTest.kt`,
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/usecase/message/PlainMessageSendUsecaseTest.kt`,
  `src/vk-facade/src/test/kotlin/com/simarel/vkbot/vkFacade/domain/RandomIdTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-dg-11** — КОГДА изменение доставлено, СИСТЕМА ДОЛЖНА иметь production-рёбра `:src:app →
  :src:greetings` и `:src:testing:arch-tests → :src:greetings`, модуль в allowlist и `coverageModules`,
  capability-файл в индексе с ADR 2026-11-07-daily-greetings и зелёные `./gradlew capabilitiesCheck` /
  `./scripts/check-capabilities.sh`. Test:
  `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/GradleDependenciesTest.kt`,
  `src/testing/arch-tests/src/test/kotlin/com/simarel/vkbot/arch/ModuleDependenciesTest.kt`.
- **AC-dg-12** — КОГДА прогон завершился успешно, СИСТЕМА ДОЛЖНА создать и закрыть OTel-спан
  `greetings.daily-run` со статусом `OK` и атрибутами `date`, `planned`, `sent`, `failed`; на ошибке —
  статус `ERROR` и только `date`, исключение пробрасывается, а `InterruptedException` не помечается
  `ERROR` и уходит вызывающему. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/adapter/input/schedule/DailyGreetingRunnerTest.kt`.

- **AC-BDAY-2a** — КОГДА у человека с пустой VK-датой есть `CONFIRMED` birthday-факт на 14.05 и он писал
  в двух чатах, СИСТЕМА ДОЛЖНА один раз включить его имя в `BirthdayGreeting` каждого его чата на 14.05
  любого года; на уровне приложения — ровно одно отправленное сообщение в дату и `0` при повторном
  прогоне. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`,
  `src/app/src/test/kotlin/com/simarel/vkbot/integration/DailyGreetingsIntegrationTest.kt`.
- **AC-BDAY-2b** — КОГДА человек есть и в VK-выдаче по дате, и в `CONFIRMED`-факте на ту же дату, СИСТЕМА
  ДОЛЖНА добавить его имя ровно один раз и не запрашивать его профиль повторно для knowledge-ветки. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.
- **AC-BDAY-2c** — КОГДА у человека непустая VK-дата на другую дату и `CONFIRMED`-факт на дату прогона,
  СИСТЕМА ДОЛЖНА не поздравлять его по факту, сохранив VK-поздравление на его собственную дату. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.
- **AC-BDAY-2d** — КОГДА единственный факт на дату — `CANDIDATE` (VK-даты нет), СИСТЕМА ДОЛЖНА не включать
  человека в план и не читать его профиль. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.
- **AC-BDAY-2e** — КОГДА `greetings.hidden-birthdays.enabled=false`, СИСТЕМА ДОЛЖНА не вызывать
  `FindUserBirthdayFactsPort` и не поздравлять по фактам, сохранив VK-именинника и праздник. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.
- **AC-BDAY-2f** — КОГДА `CONFIRMED`-факт имеет `monthDay="02-29"`, СИСТЕМА ДОЛЖНА поздравлять 29.02 в
  високосный год и 01.03 в невисокосный, никогда 28.02, запрашивая тот же набор `MonthDay`, что и VK-ветка.
  Test: `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.
- **AC-BDAY-2g** — КОГДА у knowledge-именинника нет сохранённого VK-профиля, СИСТЕМА ДОЛЖНА пропустить его с
  WARN, содержащим только `user=<id>`, и не подставлять id или значение факта в план и текст. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.
- **AC-BDAY-2h** — КОГДА чтение birthday-фактов или профилей knowledge-ветки бросило исключение, СИСТЕМА
  ДОЛЖНА записать WARN с классом исключения (без имени и значения факта) и собрать план из VK-даты и
  праздников, не роняя прогон. Test:
  `src/greetings/src/test/kotlin/com/simarel/vkbot/greetings/command/collect/CollectGreetingPlansCommandTest.kt`.

## Известные ограничения

- Ошибка VK/claim сжигает чат на день: строка claim пишется на чат до отправки всех его сообщений, retry в
  этом таске нет (ADR 2026-11-07-daily-greetings).
- `greeting_log` не чистится: 1 строка на чат в день, ретенции нет.
- Пропущенный день не догоняется: catch-up за прошлые сутки нет.
- Приватность: имена и `birth_date` попадают в промпт ИИ и в pre-existing sink'и LLM/REST/трейсов
  (`quarkus.langchain4j.log-requests/log-responses: true`, `quarkus.rest-client.logging.scope:
  request-response`, `quarkus.langchain4j.tracing.include-prompt/include-completion: true` при включённых
  OTel-трейсах в `src/app/src/main/resources/application.yml`); собственные sink'и прогона — user id на
  WARN профиля без чатов, peer id и класс исключения на INFO/ERROR, message исключения в статусе спана;
  имена и текст сообщения не логируются.
- Остаточный prompt-injection: имена — untrusted user data, у `GreetingAiService` нет tools/memory, но
  текст промпта не санитайзится.
- Скрытый ДР: knowledge-ветка fail-open — ошибка чтения фактов/профилей даёт план из VK-даты и праздников,
  knowledge-имена в этот прогон не попадают.
- «VK-дата скрыта» = `birth_date` пуст или `null`; невалидная непустая VK-дата скрывает человека из
  knowledge-ветки, парсер `birth_date` в `greetings` не дублируется.
- Год рождения из факта хранится в payload, но в текст поздравления не попадает: поздравление — имя и
  повод.
