package com.simarel.vkbot.greetings.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Stream

class NextRunTest {

    @ParameterizedTest
    @MethodSource("afterCases")
    fun `after picks the next midnight strictly after now`(case: AfterCase) {
        val next = NextRun.after(case.now, ZONE)

        assertEquals(case.expectedTarget, next.target)
        assertEquals(case.expectedDelay, next.delay(case.now))
    }

    @Test
    fun `after the target moves to the next day`() {
        val target = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZONE)

        assertEquals(target.plusDays(1), NextRun.after(target, ZONE).target)
    }

    data class AfterCase(
        val now: ZonedDateTime,
        val expectedTarget: ZonedDateTime,
        val expectedDelay: Duration,
    )

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Europe/Minsk")

        @JvmStatic
        fun afterCases(): Stream<AfterCase> = Stream.of(
            AfterCase(
                now = ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 999_000_000, ZONE),
                expectedTarget = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZONE),
                expectedDelay = Duration.ofMillis(1),
            ),
            AfterCase(
                now = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZONE),
                expectedTarget = ZonedDateTime.of(2027, 1, 2, 0, 0, 0, 0, ZONE),
                expectedDelay = Duration.ofHours(24),
            ),
            AfterCase(
                now = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 1_000_000, ZONE),
                expectedTarget = ZonedDateTime.of(2027, 1, 2, 0, 0, 0, 0, ZONE),
                expectedDelay = Duration.ofSeconds(86_399, 999_000_000),
            ),
            AfterCase(
                now = ZonedDateTime.of(2026, 12, 31, 12, 0, 0, 0, ZONE),
                expectedTarget = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZONE),
                expectedDelay = Duration.ofHours(12),
            ),
            AfterCase(
                now = ZonedDateTime.of(2026, 12, 31, 20, 59, 59, 999_000_000, ZoneId.of("UTC")),
                expectedTarget = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZONE),
                expectedDelay = Duration.ofMillis(1),
            ),
        )
    }
}
