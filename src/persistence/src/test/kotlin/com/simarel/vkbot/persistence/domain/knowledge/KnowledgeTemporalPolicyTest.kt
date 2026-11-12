package com.simarel.vkbot.persistence.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.OffsetDateTime

class KnowledgeTemporalPolicyTest {

    @Test
    fun `windowsAndFreshness`() {
        val longWindow = Duration.ofDays(180)
        val shortWindow = Duration.ofDays(30)
        listOf(
            UserFactCategory.PROFILE_ATTRIBUTE,
            UserFactCategory.INTEREST,
            UserFactCategory.KNOWLEDGE,
            UserFactCategory.SOCIAL,
        ).forEach { category -> assertEquals(longWindow, KnowledgeTemporalPolicy.changeWindow(category)) }
        listOf(UserFactCategory.EVENT, UserFactCategory.HABIT)
            .forEach { category -> assertEquals(shortWindow, KnowledgeTemporalPolicy.changeWindow(category)) }

        assertEquals(Duration.ofDays(30), KnowledgeTemporalPolicy.freshnessWindow(UserFactCategory.EVENT))
        assertEquals(Duration.ofDays(180), KnowledgeTemporalPolicy.freshnessWindow(UserFactCategory.HABIT))
        assertEquals(Duration.ofDays(365), KnowledgeTemporalPolicy.freshnessWindow(UserFactCategory.SOCIAL))
        listOf(
            UserFactCategory.PROFILE_ATTRIBUTE,
            UserFactCategory.INTEREST,
            UserFactCategory.KNOWLEDGE,
        ).forEach { category -> assertNull(KnowledgeTemporalPolicy.freshnessWindow(category)) }

        val lastConfirmedAt = OffsetDateTime.parse("2026-10-01T00:00:00Z")
        assertTrue(
            KnowledgeTemporalPolicy.isStale(
                UserFactCategory.EVENT,
                lastConfirmedAt,
                OffsetDateTime.parse("2026-11-01T00:00:00Z"),
            ),
        )
        assertFalse(
            KnowledgeTemporalPolicy.isStale(
                UserFactCategory.EVENT,
                lastConfirmedAt,
                OffsetDateTime.parse("2026-10-31T00:00:00Z"),
            ),
        )
        assertFalse(
            KnowledgeTemporalPolicy.isStale(
                UserFactCategory.INTEREST,
                lastConfirmedAt,
                OffsetDateTime.parse("2028-01-01T00:00:00Z"),
            ),
        )
    }

    @Test
    fun `normalizesDerivedValues`() {
        val age = KnowledgeTemporalPolicy.normalize(
            UserFactCategory.PROFILE_ATTRIBUTE,
            "age",
            "мне 20",
            OffsetDateTime.parse("2010-06-01T00:00:00Z"),
        )
        assertEquals("birth_year", age.factKey)
        assertEquals("1990", age.value)
        assertEquals(mapOf("year" to 1990), age.valuePayload)

        listOf("14 мая", "14.05").forEach { value ->
            assertEquals("birthday", birthday(value).factKey)
            assertEquals("ДР: 14.05", birthday(value).value)
            assertEquals(mapOf("monthDay" to "05-14"), birthday(value).valuePayload)
        }
        listOf("14.05.1993", "14 мая 1993").forEach { value ->
            assertEquals("ДР: 14.05", birthday(value).value)
            assertEquals(mapOf("monthDay" to "05-14", "year" to 1993), birthday(value).valuePayload)
        }
        val relative = birthday("в следующую пятницу")
        assertEquals("ДР: 13.11", relative.value)
        assertEquals(mapOf("monthDay" to "11-13"), relative.valuePayload)

        val unknown = birthday("летом")
        assertEquals("летом", unknown.value)
        assertEquals(emptyMap<String, Any?>(), unknown.valuePayload)

        val trimmed = KnowledgeTemporalPolicy.normalize(
            UserFactCategory.INTEREST,
            "interest_hobby",
            "  Футбол   и  хобби ",
            OffsetDateTime.parse("2026-11-11T00:00:00Z"),
        )
        assertEquals("Футбол   и  хобби", trimmed.value)

        val merged = KnowledgeTemporalPolicy.mergeBirthdayPayload(
            mapOf("monthDay" to "05-14"),
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            mapOf("monthDay" to "05-14", "year" to 1993),
            OffsetDateTime.parse("2026-02-01T00:00:00Z"),
        )
        assertEquals(1993, merged["year"])
        val keptYear = KnowledgeTemporalPolicy.mergeBirthdayPayload(
            mapOf("monthDay" to "05-14", "year" to 1993),
            OffsetDateTime.parse("2026-02-01T00:00:00Z"),
            mapOf("monthDay" to "05-14", "year" to 1995),
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        )
        assertEquals(1993, keptYear["year"])
    }

    @Test
    fun `classifiesTransitions`() {
        val current = FakeModelProvider.createUserFact(
            value = "футбол",
            lastConfirmedAt = OffsetDateTime.parse("2026-06-01T00:00:00Z"),
        )
        val historical = FakeModelProvider.createUserFact(
            value = "теннис",
            status = UserFactStatus.HISTORICAL,
            lastConfirmedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        )

        assertEquals(
            FactTransition.CREATE,
            KnowledgeTemporalPolicy.classify(
                UserFactCategory.INTEREST,
                null,
                null,
                KnowledgeValueHash.of("футбол"),
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            ),
        )
        assertEquals(
            FactTransition.SAME,
            KnowledgeTemporalPolicy.classify(
                UserFactCategory.INTEREST,
                current,
                historical,
                KnowledgeValueHash.of("футбол"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
            ),
        )
        assertChangedTransitions(current)
        assertRevival(current, historical)
    }

    private fun assertChangedTransitions(current: UserFact) {
        listOf(
            Triple("2026-06-01T00:00:00Z", null, FactTransition.CONFLICT),
            Triple("2026-12-01T00:00:00Z", null, FactTransition.CHANGE),
            Triple("2026-01-01T00:00:00Z", null, FactTransition.OLDER),
        ).forEach { (observedAt, historical, expected) ->
            assertEquals(
                expected,
                KnowledgeTemporalPolicy.classify(
                    UserFactCategory.INTEREST,
                    current,
                    historical,
                    KnowledgeValueHash.of("теннис"),
                    OffsetDateTime.parse(observedAt),
                ),
            )
        }
    }

    private fun assertRevival(current: UserFact, historical: UserFact) {
        assertEquals(
            FactTransition.REVIVAL,
            KnowledgeTemporalPolicy.classify(
                UserFactCategory.INTEREST,
                current,
                historical,
                KnowledgeValueHash.of("теннис"),
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            ),
        )
    }

    private fun birthday(value: String): NormalizedFact = KnowledgeTemporalPolicy.normalize(
        UserFactCategory.PROFILE_ATTRIBUTE,
        "birthday",
        value,
        OffsetDateTime.parse("2026-11-11T00:00:00Z"),
    )
}
