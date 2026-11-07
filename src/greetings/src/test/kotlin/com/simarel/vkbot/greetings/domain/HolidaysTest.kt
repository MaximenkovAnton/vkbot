package com.simarel.vkbot.greetings.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.stream.Stream

class HolidaysTest {

    @ParameterizedTest
    @MethodSource("cases")
    fun `forDate maps the run date to the holidays`(case: HolidayCase) {
        assertEquals(case.expected, Holidays.forDate(case.date))
    }

    data class HolidayCase(val date: LocalDate, val expected: List<Holiday>)

    companion object {
        @JvmStatic
        fun cases(): Stream<HolidayCase> = Stream.of(
            HolidayCase(LocalDate.parse("2026-01-01"), listOf(Holiday.NEW_YEAR)),
            HolidayCase(LocalDate.parse("2024-09-12"), listOf(Holiday.PROGRAMMER_DAY)),
            HolidayCase(LocalDate.parse("2024-09-13"), emptyList()),
            HolidayCase(LocalDate.parse("2026-09-13"), listOf(Holiday.PROGRAMMER_DAY)),
            HolidayCase(LocalDate.parse("2026-09-12"), emptyList()),
            HolidayCase(LocalDate.parse("2026-01-02"), emptyList()),
            HolidayCase(LocalDate.parse("2026-03-01"), emptyList()),
        )
    }
}
