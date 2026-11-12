package com.simarel.vkbot.persistence.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import java.time.DayOfWeek
import java.time.Duration
import java.time.Month
import java.time.OffsetDateTime
import java.time.YearMonth

object KnowledgeTemporalPolicy {

    const val CONFLICT_PENALTY = 0.15

    private const val LONG_CHANGE_WINDOW_DAYS = 180L
    private const val SHORT_CHANGE_WINDOW_DAYS = 30L
    private const val EVENT_FRESHNESS_DAYS = 30L
    private const val HABIT_FRESHNESS_DAYS = 180L
    private const val SOCIAL_FRESHNESS_DAYS = 365L

    private val LONG_CHANGE_WINDOW = Duration.ofDays(LONG_CHANGE_WINDOW_DAYS)
    private val SHORT_CHANGE_WINDOW = Duration.ofDays(SHORT_CHANGE_WINDOW_DAYS)
    private val EVENT_FRESHNESS = Duration.ofDays(EVENT_FRESHNESS_DAYS)
    private val HABIT_FRESHNESS = Duration.ofDays(HABIT_FRESHNESS_DAYS)
    private val SOCIAL_FRESHNESS = Duration.ofDays(SOCIAL_FRESHNESS_DAYS)

    fun changeWindow(category: UserFactCategory): Duration = when (category) {
        UserFactCategory.PROFILE_ATTRIBUTE,
        UserFactCategory.INTEREST,
        UserFactCategory.KNOWLEDGE,
        UserFactCategory.SOCIAL,
        -> LONG_CHANGE_WINDOW

        UserFactCategory.EVENT,
        UserFactCategory.HABIT,
        -> SHORT_CHANGE_WINDOW
    }

    fun freshnessWindow(category: UserFactCategory): Duration? = when (category) {
        UserFactCategory.EVENT -> EVENT_FRESHNESS
        UserFactCategory.HABIT -> HABIT_FRESHNESS
        UserFactCategory.SOCIAL -> SOCIAL_FRESHNESS
        UserFactCategory.PROFILE_ATTRIBUTE,
        UserFactCategory.INTEREST,
        UserFactCategory.KNOWLEDGE,
        -> null
    }

    fun isStale(category: UserFactCategory, lastConfirmedAt: OffsetDateTime, now: OffsetDateTime): Boolean {
        val window = freshnessWindow(category) ?: return false
        return lastConfirmedAt.plus(window).isBefore(now)
    }

    fun normalize(
        category: UserFactCategory,
        factKey: String,
        value: String,
        observedAt: OffsetDateTime,
    ): NormalizedFact {
        val trimmed = value.trim()
        if (category != UserFactCategory.PROFILE_ATTRIBUTE) {
            return NormalizedFact(factKey, trimmed, emptyMap())
        }
        val normalized = when (factKey) {
            AGE_KEY -> normalizeAge(trimmed, observedAt)
            BIRTHDAY_KEY -> normalizeBirthday(trimmed, observedAt)
            else -> null
        }
        return normalized ?: NormalizedFact(factKey, trimmed, emptyMap())
    }

    fun classify(
        category: UserFactCategory,
        latestCurrent: UserFact?,
        historicalSameValue: UserFact?,
        incomingValueHash: String,
        observedAt: OffsetDateTime,
    ): FactTransition {
        if (latestCurrent == null) {
            return FactTransition.CREATE
        }
        if (KnowledgeValueHash.of(latestCurrent.value) == incomingValueHash) {
            return FactTransition.SAME
        }
        if (observedAt.isBefore(latestCurrent.lastConfirmedAt)) {
            return FactTransition.OLDER
        }
        if (historicalSameValue != null) {
            return FactTransition.REVIVAL
        }
        val age = Duration.between(latestCurrent.lastConfirmedAt, observedAt)
        return if (age <= changeWindow(category)) FactTransition.CONFLICT else FactTransition.CHANGE
    }

    fun mergeBirthdayPayload(
        stored: Map<String, Any?>,
        storedLastConfirmedAt: OffsetDateTime,
        incoming: Map<String, Any?>,
        observedAt: OffsetDateTime,
    ): Map<String, Any?> {
        val storedYear = stored[YEAR]?.let { it as? Number }?.toInt()
        val incomingYear = incoming[YEAR]?.let { it as? Number }?.toInt()
        val year = when {
            incomingYear == null -> storedYear
            storedYear == null -> incomingYear
            storedYear == incomingYear -> incomingYear
            !observedAt.isBefore(storedLastConfirmedAt) -> incomingYear
            else -> storedYear
        }
        return buildMap {
            incoming[MONTH_DAY]?.let { put(MONTH_DAY, it) }
            if (year != null) {
                put(YEAR, year)
            }
        }
    }
}

private const val AGE_KEY = "age"
private const val BIRTHDAY_KEY = "birthday"
private const val BIRTH_YEAR_KEY = "birth_year"
private const val YEAR = "year"
private const val MONTH_DAY = "monthDay"
private const val YEAR_GROUP = 3
private const val MONTH_COUNT = 12
private const val LEAP_YEAR = 2000

private val AGE_PATTERN = Regex("""\d{1,3}""")
private val NUMERIC_BIRTHDAY = Regex("""^(\d{1,2})[./-](\d{1,2})(?:[./-](\d{4}))?$""")
private val TEXT_BIRTHDAY = Regex("""^(\d{1,2})\s+([а-яё]+)(?:\s+(\d{4}))?$""")
private val RELATIVE_BIRTHDAY = Regex("""^в\s+следующую\s+([а-яё]+)$""")

private val MONTHS = mapOf(
    "января" to Month.JANUARY.value,
    "февраля" to Month.FEBRUARY.value,
    "марта" to Month.MARCH.value,
    "апреля" to Month.APRIL.value,
    "мая" to Month.MAY.value,
    "июня" to Month.JUNE.value,
    "июля" to Month.JULY.value,
    "августа" to Month.AUGUST.value,
    "сентября" to Month.SEPTEMBER.value,
    "октября" to Month.OCTOBER.value,
    "ноября" to Month.NOVEMBER.value,
    "декабря" to Month.DECEMBER.value,
)

private val WEEKDAYS = mapOf(
    "понедельник" to DayOfWeek.MONDAY,
    "вторник" to DayOfWeek.TUESDAY,
    "среду" to DayOfWeek.WEDNESDAY,
    "среда" to DayOfWeek.WEDNESDAY,
    "четверг" to DayOfWeek.THURSDAY,
    "пятницу" to DayOfWeek.FRIDAY,
    "пятница" to DayOfWeek.FRIDAY,
    "субботу" to DayOfWeek.SATURDAY,
    "суббота" to DayOfWeek.SATURDAY,
    "воскресенье" to DayOfWeek.SUNDAY,
)

private fun normalizeAge(value: String, observedAt: OffsetDateTime): NormalizedFact? {
    val age = AGE_PATTERN.find(value)?.value?.toIntOrNull() ?: return null
    val year = observedAt.year - age
    return NormalizedFact(BIRTH_YEAR_KEY, year.toString(), mapOf(YEAR to year))
}

private fun normalizeBirthday(value: String, observedAt: OffsetDateTime): NormalizedFact? {
    val numeric = numericBirthday(value) ?: textBirthday(value)
    return numeric ?: relativeBirthday(value, observedAt)
}

private fun numericBirthday(value: String): NormalizedFact? {
    val match = NUMERIC_BIRTHDAY.matchEntire(value) ?: return null
    val day = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val year = match.groupValues[YEAR_GROUP].takeIf { it.isNotEmpty() }?.toInt()
    return birthday(day, month, year)
}

private fun textBirthday(value: String): NormalizedFact? {
    val match = TEXT_BIRTHDAY.matchEntire(value.lowercase()) ?: return null
    val month = MONTHS[match.groupValues[2]] ?: return null
    val year = match.groupValues[YEAR_GROUP].takeIf { it.isNotEmpty() }?.toInt()
    return birthday(match.groupValues[1].toInt(), month, year)
}

private fun relativeBirthday(value: String, observedAt: OffsetDateTime): NormalizedFact? {
    val match = RELATIVE_BIRTHDAY.matchEntire(value.lowercase()) ?: return null
    val dayOfWeek = WEEKDAYS[match.groupValues[1]] ?: return null
    var date = observedAt.toLocalDate().plusDays(1)
    while (date.dayOfWeek != dayOfWeek) {
        date = date.plusDays(1)
    }
    return birthday(date.dayOfMonth, date.monthValue, null)
}

private fun birthday(day: Int, month: Int, year: Int?): NormalizedFact? {
    if (month !in 1..MONTH_COUNT || day !in 1..YearMonth.of(LEAP_YEAR, month).lengthOfMonth()) {
        return null
    }
    val payload = buildMap<String, Any?> {
        put(MONTH_DAY, "%02d-%02d".format(month, day))
        if (year != null) {
            put(YEAR, year)
        }
    }
    return NormalizedFact(BIRTHDAY_KEY, "ДР: %02d.%02d".format(day, month), payload)
}
