package com.simarel.vkbot.persistence.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.MonthDay
import java.util.stream.Stream

class BirthDateMatcherTest {

    @ParameterizedTest
    @MethodSource("birthDateCases")
    fun `parse returns month and day for a valid birth date and null otherwise`(case: BirthDateCase) {
        assertEquals(case.expected, BirthDateMatcher.parse(case.birthDate))
    }

    data class BirthDateCase(
        val birthDate: String?,
        val expected: MonthDay?,
    )

    companion object {
        @JvmStatic
        fun birthDateCases(): Stream<BirthDateCase> = Stream.of(
            BirthDateCase("5.9", MonthDay.of(9, 5)),
            BirthDateCase("05.09", MonthDay.of(9, 5)),
            BirthDateCase("5.9.1990", MonthDay.of(9, 5)),
            BirthDateCase("05.09.1990", MonthDay.of(9, 5)),
            BirthDateCase(" 5.9 ", MonthDay.of(9, 5)),
            BirthDateCase("29.2", MonthDay.of(2, 29)),
            BirthDateCase("31.12.2000", MonthDay.of(12, 31)),
            BirthDateCase(null, null),
            BirthDateCase("", null),
            BirthDateCase("5", null),
            BirthDateCase("5.9.90", null),
            BirthDateCase("31.4", null),
            BirthDateCase("0.1", null),
            BirthDateCase("5.13", null),
            BirthDateCase("5.9.19900", null),
            BirthDateCase("abc", null),
            BirthDateCase("5-9", null),
        )
    }
}
