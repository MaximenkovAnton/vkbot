package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.ChatStatistics
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SECTION_SEPARATOR = "\n\n"
private const val LINE_SEPARATOR = "\n"
private const val INDENT = "  "
private const val SPACE = " "
private const val ENTRY_SEPARATOR = "; "
private const val FIELD_SEPARATOR = ": "
private const val RANGE_ARROW = " → "
private const val QUOTE_START = "«"
private const val QUOTE_END = "»"
private const val EMPTY_HISTORY_VALUE = "—"
private const val SCREEN_NAME_PREFIX = "@"
private const val DATE_PATTERN = "dd.MM.yyyy HH:mm"
private const val AVERAGE_FORMAT = "%.1f"
private const val CURRENT_CHAT = "этот чат"
private const val OTHER_CHAT = "другой чат"
private const val CASE_POSITION = 1
private const val WEEK_OFFSET = 1

private const val PROFILE_HEADER = "Профиль:"
private const val STATISTICS_HEADER = "Статистика:"
private const val HISTORY_HEADER = "История изменений:"
private const val CHAT_HEADER = "В этом чате:"

private const val NAME_LABEL = "Имя"
private const val SCREEN_NAME_LABEL = "Страница"
private const val BIRTH_DATE_LABEL = "Дата рождения"
private const val CITY_LABEL = "Город"
private const val GROUP_NAME_LABEL = "Название"

private const val TOTAL_MESSAGES_LABEL = "Всего сообщений"
private const val TOTAL_LENGTH_LABEL = "Суммарная длина"
private const val AVERAGE_LENGTH_LABEL = "Средняя длина"
private const val FIRST_MESSAGE_LABEL = "Первое сообщение"
private const val LAST_MESSAGE_LABEL = "Последнее сообщение"
private const val CHATS_LABEL = "Чаты"
private const val HOURS_ACTIVITY_LABEL = "Активность по часам"
private const val DAYS_ACTIVITY_LABEL = "Активность по дням недели"
private const val CHAT_MESSAGES_LABEL = "Сообщений в этом чате"
private const val CHAT_AVERAGE_LENGTH = "средняя длина"
private const val CHAT_TOTAL_LENGTH = "суммарная длина"
private const val CHAT_LAST_MESSAGE = "последнее сообщение"

private val DETAIL_FIELDS: List<Pair<String, String>> = listOf(
    "country" to "Страна",
    "home_town" to "Родной город",
    "sex" to "Пол",
    "relation" to "Семейное положение",
    "political" to "Политические взгляды",
    "religion" to "Религия",
    "people_main" to "Главное в людях",
    "life_main" to "Главное в жизни",
    "smoking" to "Отношение к курению",
    "alcohol" to "Отношение к алкоголю",
    "occupation_type" to "Тип занятия",
    "occupation_name" to "Занятие",
    "education_university" to "Вуз",
    "education_faculty" to "Факультет",
    "education_graduation" to "Год окончания",
    "education_form" to "Форма обучения",
    "education_status" to "Статус обучения",
    "status" to "Статус",
    "about" to "О себе",
    "activities" to "Деятельность",
    "interests" to "Интересы",
    "music" to "Музыка",
    "movies" to "Фильмы",
    "tv" to "Телепередачи",
    "books" to "Книги",
    "games" to "Игры",
    "quotes" to "Цитаты",
    "site" to "Сайт",
    "followers_count" to "Подписчиков",
    "verified" to "Подтверждён",
)

private val HISTORY_FIELDS: List<Pair<String, String>> = listOf(
    "first_name" to NAME_LABEL,
    "last_name" to "Фамилия",
    "screen_name" to SCREEN_NAME_LABEL,
    "birth_date" to BIRTH_DATE_LABEL,
    "city" to CITY_LABEL,
)

private val HISTORY_LABELS: Map<String, String> = (HISTORY_FIELDS + DETAIL_FIELDS).toMap()

private val DAY_NAMES = listOf(
    "понедельник",
    "вторник",
    "среда",
    "четверг",
    "пятница",
    "суббота",
    "воскресенье",
)

private fun section(header: String, lines: List<String>): String? {
    val body = lines.takeIf { it.isNotEmpty() } ?: return null
    return (listOf(header) + body).joinToString(LINE_SEPARATOR)
}

@Suppress("TooManyFunctions")
class ProfileDossierFormatter(private val zoneId: ZoneId) {

    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN)

    fun format(dossier: ProfileDossier, peerId: Long): String = when (dossier) {
        is ProfileDossier.UserDossier -> userText(dossier, peerId)
        is ProfileDossier.GroupDossier -> groupText(dossier)
    }

    private fun userText(dossier: ProfileDossier.UserDossier, peerId: Long): String = listOfNotNull(
        section(PROFILE_HEADER, userProfileLines(dossier.profile)),
        section(STATISTICS_HEADER, statisticsLines(dossier.statistics, peerId)),
        section(HISTORY_HEADER, historyLines(dossier.history)),
        dossier.chatMessageCount?.let { section(CHAT_HEADER, chatLines(it)) },
    ).joinToString(SECTION_SEPARATOR)

    private fun groupText(dossier: ProfileDossier.GroupDossier): String = listOfNotNull(
        section(PROFILE_HEADER, groupProfileLines(dossier.profile)),
        dossier.chatMessageCount?.let { section(CHAT_HEADER, chatLines(it)) },
    ).joinToString(SECTION_SEPARATOR)

    private fun userProfileLines(profile: VkUserProfile): List<String> = buildList {
        val name = listOfNotNull(profile.firstName, profile.lastName)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(SPACE)
        if (name.isNotEmpty()) {
            add("$NAME_LABEL$FIELD_SEPARATOR$name")
        }
        profile.screenName?.trim()?.takeIf(String::isNotEmpty)?.let {
            add("$SCREEN_NAME_LABEL$FIELD_SEPARATOR$SCREEN_NAME_PREFIX$it")
        }
        profile.birthDate?.trim()?.takeIf(String::isNotEmpty)?.let {
            add("$BIRTH_DATE_LABEL$FIELD_SEPARATOR$it")
        }
        profile.city?.trim()?.takeIf(String::isNotEmpty)?.let {
            add("$CITY_LABEL$FIELD_SEPARATOR$it")
        }
        DETAIL_FIELDS.forEach { (key, label) ->
            detailValue(profile.details[key])?.let { add("$label$FIELD_SEPARATOR$it") }
        }
    }

    private fun groupProfileLines(profile: VkGroupProfile): List<String> = listOfNotNull(
        profile.name?.trim()?.takeIf(String::isNotEmpty)?.let { "$GROUP_NAME_LABEL$FIELD_SEPARATOR$it" },
        profile.screenName?.trim()?.takeIf(String::isNotEmpty)?.let {
            "$SCREEN_NAME_LABEL$FIELD_SEPARATOR$SCREEN_NAME_PREFIX$it"
        },
    )

    private fun statisticsLines(statistics: UserStatistics, peerId: Long): List<String> = buildList {
        add("$TOTAL_MESSAGES_LABEL$FIELD_SEPARATOR${statistics.messageCount}")
        add("$TOTAL_LENGTH_LABEL$FIELD_SEPARATOR${statistics.totalLength}")
        add("$AVERAGE_LENGTH_LABEL$FIELD_SEPARATOR${average(statistics.averageLength)}")
        statistics.firstMessageAt?.let { add("$FIRST_MESSAGE_LABEL$FIELD_SEPARATOR${date(it)}") }
        statistics.lastMessageAt?.let { add("$LAST_MESSAGE_LABEL$FIELD_SEPARATOR${date(it)}") }
        if (statistics.chats.isNotEmpty()) {
            add("$CHATS_LABEL:")
            statistics.chats.sortedBy { it.peerId }.forEachIndexed { index, chat ->
                add("$INDENT${index + CASE_POSITION}) ${chatLine(chat, peerId)}")
            }
        }
        if (statistics.activityByHour.isNotEmpty()) {
            add("$HOURS_ACTIVITY_LABEL:")
            statistics.activityByHour.sortedBy { it.hour }.forEach {
                add("$INDENT${it.hour}${FIELD_SEPARATOR}${it.messageCount}")
            }
        }
        if (statistics.activityByDayOfWeek.isNotEmpty()) {
            add("$DAYS_ACTIVITY_LABEL:")
            statistics.activityByDayOfWeek.sortedBy { it.dayOfWeek }.forEach {
                add("$INDENT${dayName(it.dayOfWeek)}${FIELD_SEPARATOR}${it.messageCount}")
            }
        }
    }

    private fun chatLine(chat: ChatStatistics, peerId: Long): String {
        val label = if (chat.peerId == peerId) CURRENT_CHAT else OTHER_CHAT
        val lastMessage = chat.lastMessageAt?.let { "$CHAT_LAST_MESSAGE$FIELD_SEPARATOR${date(it)}" }
        val parts = listOfNotNull(
            "$label — сообщений: ${chat.messageCount}",
            "$CHAT_AVERAGE_LENGTH: ${average(chat.averageLength)}",
            "$CHAT_TOTAL_LENGTH: ${chat.totalLength}",
            lastMessage,
        )
        return parts.joinToString(", ")
    }

    private fun historyLines(entries: List<ProfileHistoryEntry>): List<String> = entries.mapNotNull { entry ->
        val parts = entry.changes.mapNotNull { (key, raw) ->
            val label = HISTORY_LABELS[key] ?: return@mapNotNull null
            val change = raw as? Map<*, *> ?: return@mapNotNull null
            "$label$FIELD_SEPARATOR$QUOTE_START${change["old"] ?: EMPTY_HISTORY_VALUE}$QUOTE_END" +
                "$RANGE_ARROW$QUOTE_START${change["new"] ?: EMPTY_HISTORY_VALUE}$QUOTE_END"
        }
        parts.takeIf { it.isNotEmpty() }?.let { "${date(entry.changedAt)} — ${it.joinToString(ENTRY_SEPARATOR)}" }
    }

    private fun chatLines(messageCount: Long): List<String> {
        val line = "$CHAT_MESSAGES_LABEL$FIELD_SEPARATOR$messageCount"
        return listOf(line)
    }

    private fun detailValue(value: Any?): String? = when (value) {
        null -> null
        is Boolean -> if (value) "да" else "нет"
        is String -> value.trim().takeIf(String::isNotEmpty)
        is Number -> value.toString()
        else -> null
    }

    private fun average(value: Double): String = String.format(Locale.ROOT, AVERAGE_FORMAT, value)

    private fun dayName(dayOfWeek: Int): String = DAY_NAMES.getOrNull(dayOfWeek - WEEK_OFFSET) ?: dayOfWeek.toString()

    private fun date(value: OffsetDateTime): String = value.atZoneSameInstant(zoneId).format(dateFormatter)
}
