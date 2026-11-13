# 2026-11-10: Человеческие поля профиля VK вместо сырого снимка

- **Статус:** accepted
- **Дата:** 2026-11-10
- **Supersedes:** —
- **Amends:** ADR 2026-11-10-vk-profile-history-and-statistics — профильные пункты: состав `USER_FIELDS`
  (54 → 35), хранение ответа VK (`profile_data` → `profile_details`, человеческая проекция), правило diff
  истории и ключ `birth_date` вместо `bdate`; формат истории `{field: {old, new}}`, её запись при
  актуализации и вся статистика остаются в силе. Тело старого ADR не переписывается; в его шапку добавлена
  только строка `**Amended by:**`.
- **Amended by:** —

## Контекст

[ADR 2026-11-10-vk-profile-history-and-statistics](2026-11-10-vk-profile-history-and-statistics.md) сохранял
ответ VK как есть: `VkUserProfile.profileData: Map<String, Any?>` — набор VK-полей верхнего уровня, вложенные
объекты с id, коды (`sex: 2`, `relation: 4`), `photo_*` отбрасывались только по имени. Колонка БД —
`vk_user_profiles.profile_data JSONB`.

Читать такие данные человеком нельзя: `sex: 2` и `country: {id: 1, title: "Россия"}` не значат ничего без
справочника VK, а id групп/городов/вузов в JSONB — мусор для потребителя. Кроме того, в снимок попадали
волатильные и пер-аккаунтные поля (`last_seen`, `online*`, `friend_status`, фото и т. п.), которые меняются
на каждом вызове и раздувают историю.

Требование: хранить в `vk_user_profiles` полезные поля VK в человеческом виде — русские подписи enum, title
города/страны, вложенные объекты без VK-id, без пустых строк и неизвестных кодов; сырую колонку
`profile_data` удалить всей колонкой, как и старую сырую историю, и сложить человеческие значения в один
JSONB `profile_details`. Типизированные колонки профиля остаются.

## Решение

- **Маппинг — один адаптер.** `VkUserProfileMapper` (`vk-facade/adapter/output/vk/circuitbreaker`) —
  единственное место перевода ответа VK в человеческие значения; `VkGetUserProfilesBatchAdapter`
  делегирует ему (`response.response?.map(VkUserProfileMapper::map)`), а бывший `toProfileData()` удалён.
  Allowlist полей и таблица подписей — константы маппера; неизвестный код, пустая/пробельная строка,
  отсутствующее поле и `null` не дают ключа. Маппер не логирует значения (PII); `JsonNode` живёт только в
  адаптере, в домен уходит `Map<String, Any?>`.
- **`USER_FIELDS` сокращается с 54 до 35** — только поля, которые маппятся. Волатильные и пер-аккаунтные
  поля (`last_seen`, `online`, `lists`, `common_count`, `friend_status`, `wall_comments`, `photo*`,
  `has_photo`, `crop_photo`, `relation_partner`), `exports`, `wall_default`, `blacklisted*` не
  запрашиваются.
- **Контракт домена.** `VkUserProfile.profileData` → `details: Map<String, Any?> = emptyMap()`. Значения —
  `String`/`Int`/`Boolean`/списки человеческих объектов; без VK-id, `null` и пустых строк; присутствующая
  пустая коллекция — `[]`/`{}`. JSON обоих REST, отдающих профиль (`GET /api/profiles/users/batch`,
  `GET /persistence/profiles/users`), отдаёт `details` без алиаса `profileData` — жёсткий разрыв.
- **Схема.** `vk_user_profiles.profile_data` заменяется на `profile_details JSONB` (nullable, без default и
  backfill); типизированные колонки не меняются. Новый changeset `008-replace-profile-data-with-human-details`
  — три changeSet'а по порядку: `008-add-profile-details` (`addColumn`), `008-clear-profile-history`
  (`SET LOCAL lock_timeout = '10s'` + `delete` без `where` — вся старая история), `008-drop-profile-data`
  (`SET LOCAL lock_timeout = '10s'` + `dropColumn`). Разделение на changeset'ы не даёт all-rows `DELETE`
  идти в одной транзакции с `ACCESS EXCLUSIVE` от `addColumn`; 001–007 не редактируются. Liquibase
  применяет master на старте, назад не откатывается.
- **`ProfileDataMerger` → `ProfileFieldsMerger`.** `merge` сливает `details` по ключам (значение `null`
  сохраняет прежнее, `[]`/`{}`/`false` заменяют). `diff` считает typed-ключи (`first_name`, `last_name`,
  `screen_name`, `birth_date`, `city`) **и** изменившиеся ключи `details` в одной карте
  `{field: {old, new}}` с человеческими значениями; `previous.details` пуст → только typed-ключи (первая
  актуализация после 008 не пишет строку на каждого пользователя); изменений нет → истории нет.
- **История.** Типизированные ключи называются именами колонок (`birth_date`, не `bdate`). Старые строки
  истории удаляются в 008 без переноса; ретенции нет.

## Отклонённые альтернативы

| Альтернатива | Почему нет |
|---|---|
| Оставить `profileData` read-only алиасом на релиз | Содержимое всё равно меняется, дубль контракта и мусорное имя живут в API; жёсткий разрыв проще и честнее |
| Сохранить имя `profileData` в JSON с человеческими значениями | Имя врёт о содержимом, `details` не доезжают до клиентов |
| Широкая типизированная таблица на каждое поле | Десятки новых колонок и миграций на каждое изменение VK; типизированных колонок уже хватает для запросов, остальное — один JSONB |
| Хранить сырые значения и маппить на чтении | Маппинг нужен каждому читателю, сырой формат остаётся в БД и истории; вопрос был именно в хранении |
| Backfill старых `profile_data` в человеческий вид | Данное изменение удаляет колонку целиком; backfill — отдельная задача с другой ценой и рисками |
| Двумя шагами: add+purge в 008, drop позже | Требование — удалить сырую колонку сейчас; лишний релиз с мусором и второй changeset |
| Sentinel «поле присутствует, но пустое» для очистки текста | Меняет контракт merge и JSON; пустая строка VK = отсутствие поля — осознанный named gap |
| Полный diff `details` при пустом прежнем снимке | Тысячи шумных строк `old=null` на активных пользователей; typed-полей достаточно для первой актуализации |

## Известные пробелы (named gaps)

- Текстовые поля VK нельзя очистить: пустая строка = отсутствие ключа, merge сохраняет прежнее значение.
  Коллекции очищаются `[]`/`{}`, `false` перекрывает `true`.
- `profile_details` nullable: «не актуализирован» и «пусто» не различаются, читатель отдаёт `emptyMap()`.
- Старые профили получат непустой `profile_details` только при следующей актуализации (до месяца), backfill
  нет.
- `counters`/`military` в батчевом `users.get` обычно отсутствуют (VK отдаёт их при ровно одном `user_id`);
  маппинг их покрывает, отдельные вызовы не делаются.
- `relation_partner` не маппится (нет в `USER_FIELDS`); `relatives[].birth_date` не пишется (нет источника);
  `education_form`/`education_status` верхнего уровня — только если VK их вернул.
- `schools[].type` — русская подпись кода 0–8, неизвестный код не пишется.
- История не ретенционируется; HTTP-теста REST `/persistence/*` нет — JSON-контракт пинится
  `VkUserProfileJsonTest`.
- `SET LOCAL lock_timeout = '10s'` проверяется по разобранному changeset, а не фактическим ожиданием
  блокировки; при проблемах на старте `lock_timeout` уходит в настройку БД/ops-шаг, разделение на
  changeset'ы остаётся.

## Последствия

- `vk_user_profiles.profile_data` и `vk_user_profile_history` старого формата удаляются в 008; профили
  получают один человеческий JSONB `profile_details`.
- REST обоих модулей и история меняют JSON-контракт: `profileData` нет, есть `details`; ключ `birth_date`
  вместо `bdate`; значения человеческие. Внешние потребители переключаются на новое имя.
- **Откат образа после 008 — только с восстановлением БД из бэкапа до 008**: предыдущий образ читает и
  пишет `profile_data` (`VkUserProfileTable`) и падает на `findByIds`/`persist`
  (`FetchUserProfileCommandImpl`). Это осознанное исключение из правила аддитивности, зафиксированное в
  `docs/development/deployment.md`.
- Новых модулей, зависимостей, REST-эндпоинтов, событий, конфига и прав нет; состав PII в `details` тот же,
  что был в `profile_data`.
