package com.simarel.vkbot.ai.domain.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.OffsetDateTime
import java.util.stream.Stream

private const val DEFAULT_REFERENCE = "2026-11-10T12:00:00Z"

private fun reference(value: String = DEFAULT_REFERENCE): OffsetDateTime = OffsetDateTime.parse(value)

private fun boundary(value: String): OffsetDateTime = OffsetDateTime.parse(value)

private fun period(text: String, from: String, to: String, reference: String = DEFAULT_REFERENCE): Arguments {
    val parsedReference = reference(reference)
    return Arguments.of(text, parsedReference, boundary(from), boundary(to))
}

class NewsPeriodParserTest {

    @ParameterizedTest(name = "{0} @ {1}")
    @MethodSource("periods")
    fun `parse returns the expected period`(
        text: String,
        reference: OffsetDateTime,
        expectedFrom: OffsetDateTime,
        expectedTo: OffsetDateTime,
    ) {
        val period = NewsPeriodParser().parse(text, reference)

        assertEquals(expectedFrom, period?.from)
        assertEquals(expectedTo, period?.to)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "",
            "что нового",
            "за 0 дней",
            "за дней",
            "32.11",
            "31.02",
            "29.02.2025",
            "13.13",
            "с 3 по 1 ноября",
        ],
    )
    fun `parse returns null when the text has no valid period`(text: String) {
        assertNull(NewsPeriodParser().parse(text, reference()))
    }

    companion object {

        @JvmStatic
        fun periods(): Stream<Arguments> = Stream.concat(relativePeriods(), absolutePeriods())

        private fun relativePeriods(): Stream<Arguments> = Stream.of(
            period("сегодня", "2026-11-10T00:00:00Z", "2026-11-10T23:59:59.999999999Z"),
            period("СЕГОДНЯ", "2026-11-10T00:00:00Z", "2026-11-10T23:59:59.999999999Z"),
            period("что нового за вчера?", "2026-11-09T00:00:00Z", "2026-11-09T23:59:59.999999999Z"),
            period("позавчера", "2026-11-08T00:00:00Z", "2026-11-08T23:59:59.999999999Z"),
            period("за 1 день", "2026-11-09T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за 3 дня", "2026-11-07T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за 7 дней", "2026-11-03T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за 8 дней", "2026-11-02T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за 10 дней", "2026-10-31T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за последние 3 дня", "2026-11-07T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за неделю", "2026-11-03T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("за последнюю неделю", "2026-11-03T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("эта неделя", "2026-11-03T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("последняя неделя", "2026-11-03T12:00:00Z", "2026-11-10T12:00:00Z"),
            period("на этой неделе", "2026-11-03T12:00:00Z", "2026-11-10T12:00:00Z"),
        )

        private fun absolutePeriods(): Stream<Arguments> = Stream.of(
            period("05.11", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("5.11", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("05.11.2026", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("05.11.2025", "2025-11-05T00:00:00Z", "2025-11-05T23:59:59.999999999Z"),
            period("1 ноября", "2026-11-01T00:00:00Z", "2026-11-01T23:59:59.999999999Z"),
            period("5 ноября", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("5 ноября 2026", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("5 ноября 2026 года", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("5 ноября 2026 г.", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("с 1 по 3 ноября", "2026-11-01T00:00:00Z", "2026-11-03T23:59:59.999999999Z"),
            period("с 1 ноября по 3 ноября 2026", "2026-11-01T00:00:00Z", "2026-11-03T23:59:59.999999999Z"),
            period(
                "с 30 декабря по 2 января",
                "2025-12-30T00:00:00Z",
                "2026-01-02T23:59:59.999999999Z",
                reference = "2026-01-10T12:00:00Z",
            ),
            period(
                "5 ноября",
                "2025-11-05T00:00:00Z",
                "2025-11-05T23:59:59.999999999Z",
                reference = "2026-01-10T12:00:00Z",
            ),
            period(
                "05.11",
                "2025-11-05T00:00:00Z",
                "2025-11-05T23:59:59.999999999Z",
                reference = "2026-01-10T12:00:00Z",
            ),
            period(
                "29.02",
                "2024-02-29T00:00:00Z",
                "2024-02-29T23:59:59.999999999Z",
                reference = "2025-06-01T12:00:00Z",
            ),
            period("5 ноября 2027", "2027-11-05T00:00:00Z", "2027-11-05T23:59:59.999999999Z"),
            period("за вчера 05.11", "2026-11-05T00:00:00Z", "2026-11-05T23:59:59.999999999Z"),
            period("с 1 по 3 ноября за 5 дней", "2026-11-01T00:00:00Z", "2026-11-03T23:59:59.999999999Z"),
            period(
                "сегодня",
                "2026-11-10T00:00:00+03:00",
                "2026-11-10T23:59:59.999999999+03:00",
                reference = "2026-11-10T01:00:00+03:00",
            ),
        )
    }
}
