package com.simarel.vkbot.greetings.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.MonthDay
import java.util.stream.Stream

class BirthdayDatesTest {

    @ParameterizedTest
    @MethodSource("cases")
    fun `monthDaysFor applies the february twenty ninth rule`(case: BirthdayDatesCase) {
        assertEquals(case.expected, BirthdayDates.monthDaysFor(case.date))
    }

    data class BirthdayDatesCase(val date: LocalDate, val expected: List<MonthDay>)

    companion object {
        private val FEBRUARY_29: MonthDay = MonthDay.of(2, 29)

        @JvmStatic
        fun cases(): Stream<BirthdayDatesCase> = Stream.of(
            BirthdayDatesCase(LocalDate.parse("2024-02-29"), listOf(MonthDay.of(2, 29))),
            BirthdayDatesCase(LocalDate.parse("2025-02-28"), listOf(MonthDay.of(2, 28))),
            BirthdayDatesCase(LocalDate.parse("2025-03-01"), listOf(MonthDay.of(3, 1), FEBRUARY_29)),
            BirthdayDatesCase(LocalDate.parse("2024-03-01"), listOf(MonthDay.of(3, 1))),
        )
    }
}
