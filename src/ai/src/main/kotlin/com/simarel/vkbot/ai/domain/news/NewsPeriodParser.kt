package com.simarel.vkbot.ai.domain.news

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val WEEK_DAYS = 7L
private const val YESTERDAY_DAYS = 1L
private const val DAY_BEFORE_YESTERDAY_DAYS = 2L
private const val MAX_FORM_DAYS = 999
private const val MIN_FORM_DAYS = 1

private const val MONTH_PATTERN =
    "января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря"

private val MONTH_NUMBERS: Map<String, Int> = mapOf(
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

private val RANGE_PATTERN = Regex(
    "с\\s+(?<startDay>\\d{1,2})(?:\\s+(?<startMonth>$MONTH_PATTERN))?(?:\\s+(?<startYear>\\d{4}))?" +
        "(?:\\s+(?:года|г\\.))?\\s+по\\s+(?<endDay>\\d{1,2})\\s+(?<endMonth>$MONTH_PATTERN)" +
        "(?:\\s+(?<endYear>\\d{4}))?(?:\\s+(?:года|г\\.))?",
)
private val DOT_DATE_PATTERN = Regex("(?<day>\\d{1,2})\\.(?<month>\\d{1,2})(?:\\.(?<year>\\d{4}))?")
private val DAY_MONTH_PATTERN = Regex(
    "(?<day>\\d{1,2})\\s+(?<month>$MONTH_PATTERN)(?:\\s+(?<year>\\d{4}))?(?:\\s+(?:года|г\\.))?",
)
private val WEEK_PATTERN = Regex(
    "за\\s+(?:последнюю\\s+)?неделю|(?:эта|последняя)\\s+неделя|на\\s+этой\\s+неделе",
)
private val DAYS_PATTERN = Regex("за\\s+(?:последние\\s+)?(\\d{1,3})\\s+д(?:ень|ня|ней)")

class NewsPeriodParser {

    fun parse(text: String, reference: OffsetDateTime): NewsPeriod? {
        val normalized = text.trim().lowercase()
        val offset = reference.offset
        RANGE_PATTERN.find(normalized)?.let { return parseRange(it, reference, offset) }
        DOT_DATE_PATTERN.find(normalized)?.let { return parseDotDate(it, reference, offset) }
        DAY_MONTH_PATTERN.find(normalized)?.let { return parseDayMonth(it, reference, offset) }
        return parseRelative(normalized, reference, offset)
    }

    private fun parseRange(match: MatchResult, reference: OffsetDateTime, offset: ZoneOffset): NewsPeriod? {
        val startDay = match.groups["startDay"]?.value?.toInt() ?: return null
        val startMonth = match.groups["startMonth"]?.value?.let(::monthNumber)
        val startYear = match.groups["startYear"]?.value?.toInt()
        val endDay = match.groups["endDay"]?.value?.toInt() ?: return null
        val endMonth = monthNumber(match.groups["endMonth"]?.value ?: return null)
        val endYear = match.groups["endYear"]?.value?.toInt()
        val referenceDate = reference.toLocalDate()
        val resolvedEndYear = endYear ?: inferYear(endMonth, endDay, referenceDate) ?: return null
        val end = localDateOrNull(resolvedEndYear, endMonth, endDay) ?: return null
        if (startMonth == null && startDay > end.dayOfMonth) return null
        val resolvedStartMonth = startMonth ?: endMonth
        val resolvedStartYear = startYear ?: resolveStartYear(startDay, resolvedStartMonth, end)
        val start = localDateOrNull(resolvedStartYear, resolvedStartMonth, startDay) ?: return null
        if (start.isAfter(end)) return null
        return NewsPeriod(start.atStartOfDay().atOffset(offset), end.atTime(LocalTime.MAX).atOffset(offset))
    }

    private fun parseDotDate(match: MatchResult, reference: OffsetDateTime, offset: ZoneOffset): NewsPeriod? {
        val day = match.groups["day"]?.value?.toInt() ?: return null
        val month = match.groups["month"]?.value?.toInt() ?: return null
        val year = match.groups["year"]?.value?.toInt() ?: inferYear(month, day, reference.toLocalDate()) ?: return null
        val date = localDateOrNull(year, month, day) ?: return null
        return dayPeriod(date, offset)
    }

    private fun parseDayMonth(match: MatchResult, reference: OffsetDateTime, offset: ZoneOffset): NewsPeriod? {
        val day = match.groups["day"]?.value?.toInt() ?: return null
        val month = monthNumber(match.groups["month"]?.value ?: return null)
        val year = match.groups["year"]?.value?.toInt() ?: inferYear(month, day, reference.toLocalDate()) ?: return null
        val date = localDateOrNull(year, month, day) ?: return null
        return dayPeriod(date, offset)
    }

    private fun parseRelative(text: String, reference: OffsetDateTime, offset: ZoneOffset): NewsPeriod? {
        val referenceDate = reference.toLocalDate()
        when {
            text.contains("позавчера") -> return dayPeriod(referenceDate.minusDays(DAY_BEFORE_YESTERDAY_DAYS), offset)
            text.contains("вчера") -> return dayPeriod(referenceDate.minusDays(YESTERDAY_DAYS), offset)
            text.contains("сегодня") -> return dayPeriod(referenceDate, offset)
        }
        if (WEEK_PATTERN.containsMatchIn(text)) {
            return NewsPeriod(reference.minusDays(WEEK_DAYS), reference)
        }
        val days = DAYS_PATTERN.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (days !in MIN_FORM_DAYS..MAX_FORM_DAYS) return null
        return NewsPeriod(reference.minusDays(days.toLong()), reference)
    }

    private fun resolveStartYear(startDay: Int, startMonth: Int, end: LocalDate): Int {
        val startsInEndYear = startMonth < end.monthValue ||
            (startMonth == end.monthValue && startDay <= end.dayOfMonth)
        return if (startsInEndYear) end.year else end.year - 1
    }

    private fun inferYear(month: Int, day: Int, reference: LocalDate): Int? {
        val thisYear = localDateOrNull(reference.year, month, day)
        if (thisYear != null && !thisYear.isAfter(reference)) return reference.year
        return if (localDateOrNull(reference.year - 1, month, day) != null) reference.year - 1 else null
    }

    private fun dayPeriod(date: LocalDate, offset: ZoneOffset): NewsPeriod = NewsPeriod(
        from = date.atStartOfDay().atOffset(offset),
        to = date.atTime(LocalTime.MAX).atOffset(offset),
    )

    private fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    private fun monthNumber(month: String): Int = MONTH_NUMBERS[month] ?: 0
}
