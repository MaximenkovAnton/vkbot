package com.simarel.vkbot.ai.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

private const val USER_ID = 7_600_001L
private val CONFIRMED_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-02T00:00:00Z")

class UserKnowledgeSummaryFormatterTest {

    @Test
    fun `formatsFactStatusesAndExcludesStale`() {
        val facts = listOf(
            fact(category = UserFactCategory.INTEREST, factKey = "interest_sport", value = "футбол"),
            fact(
                category = UserFactCategory.HABIT,
                factKey = "habit_morning_run",
                value = "бегает по утрам",
                status = UserFactStatus.CANDIDATE,
            ),
            fact(
                category = UserFactCategory.KNOWLEDGE,
                factKey = "knowledge_python",
                value = "начинал с Python",
                status = UserFactStatus.HISTORICAL,
                supersededAt = CONFIRMED_AT,
            ),
            fact(
                category = UserFactCategory.PROFILE_ATTRIBUTE,
                factKey = "attribute_city",
                value = "Москва",
            ),
            fact(
                category = UserFactCategory.SOCIAL,
                factKey = "social_sergey",
                value = "друг Сергей",
                conflict = true,
            ),
            fact(
                category = UserFactCategory.EVENT,
                factKey = "event_concert",
                value = "концерт в субботу",
            ),
            fact(
                category = UserFactCategory.INTEREST,
                factKey = "interest_trip",
                value = "поездка в Москву",
                stale = true,
            ),
        )

        val formatted = UserKnowledgeSummaryFormatter.format(facts)

        assertEquals(
            "Интересы и увлечения:\n- футбол\n" +
                "Привычки и рутина:\n- возможно: бегает по утрам\n" +
                "Знания и уровень:\n- ранее: начинал с Python\n" +
                "Атрибуты профиля:\n- Москва\n" +
                "Социальные связи:\n- возможно, противоречивые данные: друг Сергей\n" +
                "События и планы:\n- концерт в субботу",
            formatted,
        )
        listOf("interest_sport", "7600001", "2026-10-02", "поездка в Москву").forEach { forbidden ->
            assertFalse(formatted.contains(forbidden), forbidden)
        }
    }

    @Test
    fun `returnsEmptyStringForEmptyInput`() {
        assertEquals("", UserKnowledgeSummaryFormatter.format(emptyList()))
    }

    @Suppress("LongParameterList")
    private fun fact(
        category: UserFactCategory,
        factKey: String,
        value: String,
        status: UserFactStatus = UserFactStatus.CONFIRMED,
        supersededAt: OffsetDateTime? = null,
        conflict: Boolean = false,
        stale: Boolean = false,
    ): UserFact = FakeModelProvider.createUserFact(
        userId = USER_ID,
        category = category,
        factKey = factKey,
        value = value,
        status = status,
        lastConfirmedAt = CONFIRMED_AT,
        supersededAt = supersededAt,
        conflict = conflict,
        stale = stale,
    )
}
