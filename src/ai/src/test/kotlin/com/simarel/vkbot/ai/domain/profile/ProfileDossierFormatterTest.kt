package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId

private const val PEER_ID = 2_000_000_001L
private const val OTHER_PEER_ID = 2_000_000_002L

private fun userDossier(
    profile: VkUserProfile = FakeModelProvider.createVkUserProfile(),
    statistics: UserStatistics = FakeModelProvider.createUserStatistics(),
    history: List<ProfileHistoryEntry> = emptyList(),
    chatMessageCount: Long? = null,
    knowledge: UserKnowledge = FakeModelProvider.createUserKnowledge(),
): ProfileDossier.UserDossier = ProfileDossier.UserDossier(
    profile = profile,
    statistics = statistics,
    history = history,
    chatMessageCount = chatMessageCount,
    knowledge = knowledge,
)

class ProfileDossierFormatterTest {

    @Test
    fun `renders allowlisted details and skips phones and unknown keys`() {
        val profile = FakeModelProvider.createVkUserProfile(
            id = 7_600_001L,
            firstName = "Паша",
            lastName = "Селезнев",
            screenName = "pasha_seleznev",
            birthDate = "01.01.1990",
            city = "Минск",
            details = mapOf(
                "country" to "Беларусь",
                "mobile_phone" to "+375291234567",
                "home_phone" to "+375171234567",
                "counters" to mapOf("friends" to 10, "followers" to 5),
                "connections" to mapOf("skype" to "pasha"),
                "secret_field" to "secret",
                "verified" to true,
            ),
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(userDossier(profile = profile), PEER_ID)

        assertTrue(text.contains("Страна: Беларусь"))
        assertTrue(text.contains("Подтверждён: да"))
        assertFalse(text.contains("7600001"))
        assertFalse(text.contains("mobile_phone"))
        assertFalse(text.contains("home_phone"))
        assertFalse(text.contains("+375291234567"))
        assertFalse(text.contains("+375171234567"))
        assertFalse(text.contains("counters"))
        assertFalse(text.contains("connections"))
        assertFalse(text.contains("secret_field"))
    }

    @Test
    fun `renders the history diff of a city change`() {
        val historyEntry = FakeModelProvider.createProfileHistoryEntry(
            profileId = 7_600_001L,
            changedAt = OffsetDateTime.parse("2026-11-10T09:30:00Z"),
            changes = mapOf("city" to mapOf("old" to "Москва", "new" to "Минск")),
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(history = listOf(historyEntry)),
            PEER_ID,
        )

        assertTrue(text.contains("История изменений:"))
        assertTrue(text.contains("10.11.2026 12:30 — Город: «Москва» → «Минск»"))
    }

    @Test
    fun `omits empty sections`() {
        val userText = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(chatMessageCount = null),
            PEER_ID,
        )

        assertFalse(userText.contains("История изменений:"))
        assertFalse(userText.contains("В этом чате:"))

        val unknownHistory = FakeModelProvider.createProfileHistoryEntry(
            profileId = 7_600_001L,
            changes = mapOf("secret_field" to mapOf("old" to "a", "new" to "b")),
        )
        val withUnknownHistory = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(history = listOf(unknownHistory)),
            PEER_ID,
        )

        assertFalse(withUnknownHistory.contains("История изменений:"))
        assertFalse(withUnknownHistory.contains("secret_field"))

        val groupText = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            ProfileDossier.GroupDossier(
                profile = FakeModelProvider.createVkGroupProfile(name = "Тестовая группа", screenName = "testgroup"),
                chatMessageCount = null,
            ),
            PEER_ID,
        )

        assertEquals("Профиль:\nНазвание: Тестовая группа\nСтраница: @testgroup", groupText)
    }

    @Test
    fun `renders the statistics aggregates and chat breakdown`() {
        val statistics = FakeModelProvider.createUserStatistics(
            messageCount = 42,
            totalLength = 189,
            averageLength = 4.5,
            chats = listOf(
                FakeModelProvider.createChatStatistics(
                    peerId = OTHER_PEER_ID,
                    messageCount = 12,
                    totalLength = 39,
                    averageLength = 3.0,
                ),
                FakeModelProvider.createChatStatistics(
                    peerId = PEER_ID,
                    messageCount = 30,
                    totalLength = 150,
                    averageLength = 5.0,
                ),
            ),
            activityByHour = listOf(FakeModelProvider.createHourActivity(hour = 10, messageCount = 7)),
            activityByDayOfWeek = listOf(FakeModelProvider.createDayOfWeekActivity(dayOfWeek = 1, messageCount = 9)),
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(statistics = statistics, chatMessageCount = 30),
            PEER_ID,
        )

        assertTrue(text.contains("Всего сообщений: 42"))
        assertTrue(text.contains("Суммарная длина: 189"))
        assertTrue(text.contains("Средняя длина: 4.5"))
        assertTrue(text.contains("Чаты:"))
        assertTrue(text.indexOf("1) этот чат") < text.indexOf("2) другой чат"))
        assertTrue(text.contains("Активность по часам:"))
        assertTrue(text.contains("Активность по дням недели:"))
        assertTrue(text.contains("понедельник: 9"))
        assertTrue(text.contains("Сообщений в этом чате: 30"))
    }

    @Test
    fun `rendersFreshSummaryFromChatBelowProfile`() {
        val knowledge = FakeModelProvider.createUserKnowledge(
            facts = listOf(
                FakeModelProvider.createUserFact(
                    category = UserFactCategory.INTEREST,
                    value = "футбол",
                ),
                FakeModelProvider.createUserFact(
                    category = UserFactCategory.INTEREST,
                    value = "плавание",
                ),
            ),
            summary = "Паша увлекается футболом и историей.",
            summaryFresh = true,
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(knowledge = knowledge),
            PEER_ID,
        )

        assertTrue(text.contains("Из общения:\nПаша увлекается футболом и историей."))
        assertTrue(text.indexOf("Профиль:") < text.indexOf("Из общения:"))
        assertTrue(text.indexOf("Из общения:") < text.indexOf("Статистика:"))
        val section = text.substringAfter("Из общения:").substringBefore("Статистика:")
        assertFalse(section.contains("Интересы и увлечения:"))
        assertFalse(section.contains("- футбол"))
        assertFalse(section.contains("- плавание"))
    }

    @Test
    fun `fallsBackToFactListWhenSummaryIsStaleOrMissing`() {
        val facts = listOf(
            FakeModelProvider.createUserFact(
                category = UserFactCategory.INTEREST,
                status = UserFactStatus.CONFIRMED,
                value = "футбол",
            ),
            FakeModelProvider.createUserFact(
                category = UserFactCategory.HABIT,
                status = UserFactStatus.CANDIDATE,
                value = "бегает по утрам",
            ),
            FakeModelProvider.createUserFact(
                category = UserFactCategory.INTEREST,
                status = UserFactStatus.CONFIRMED,
                value = "плавание",
                stale = true,
            ),
        )
        val staleSummary = FakeModelProvider.createUserKnowledge(
            facts = facts,
            summary = "Старая сводка про теннис.",
            summaryFresh = false,
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(knowledge = staleSummary),
            PEER_ID,
        )

        assertTrue(
            text.contains(
                "Из общения:\nИнтересы и увлечения:\n- футбол\n" +
                    "Привычки и рутина:\n- возможно: бегает по утрам",
            ),
        )
        assertFalse(text.contains("Старая сводка про теннис."))
        assertFalse(text.contains("плавание"))

        val withoutSummary = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(knowledge = FakeModelProvider.createUserKnowledge(facts = facts)),
            PEER_ID,
        )

        assertTrue(withoutSummary.contains("Из общения:\nИнтересы и увлечения:\n- футбол"))

        val blankFreshSummary = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(
                knowledge = FakeModelProvider.createUserKnowledge(
                    facts = facts,
                    summary = "   ",
                    summaryFresh = true,
                ),
            ),
            PEER_ID,
        )

        assertTrue(
            blankFreshSummary.contains(
                "Из общения:\nИнтересы и увлечения:\n- футбол\n" +
                    "Привычки и рутина:\n- возможно: бегает по утрам",
            ),
        )
    }

    @Test
    fun `omitsKnowledgeSectionWithoutFreshKnowledge`() {
        val formatter = ProfileDossierFormatter(ZoneId.of("Europe/Minsk"))

        val empty = formatter.format(userDossier(knowledge = FakeModelProvider.createUserKnowledge()), PEER_ID)

        assertFalse(empty.contains("Из общения:"))

        val onlyStale = formatter.format(
            userDossier(
                knowledge = FakeModelProvider.createUserKnowledge(
                    facts = listOf(
                        FakeModelProvider.createUserFact(
                            category = UserFactCategory.INTEREST,
                            value = "плавание",
                            stale = true,
                        ),
                    ),
                ),
            ),
            PEER_ID,
        )

        assertFalse(onlyStale.contains("Из общения:"))

        val groupText = formatter.format(
            ProfileDossier.GroupDossier(
                profile = FakeModelProvider.createVkGroupProfile(name = "Тестовая группа", screenName = "testgroup"),
                chatMessageCount = null,
            ),
            PEER_ID,
        )

        assertEquals("Профиль:\nНазвание: Тестовая группа\nСтраница: @testgroup", groupText)
    }

    @Test
    fun `rendersKnowledgeWithoutSourcesOrDates`() {
        val fact = FakeModelProvider.createUserFact(
            userId = 7_600_001L,
            peerId = 2_600_000_001L,
            category = UserFactCategory.INTEREST,
            factKey = "interest_football",
            value = "футбол",
            lastConfirmedAt = OffsetDateTime.parse("2026-10-02T00:00:00Z"),
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(knowledge = FakeModelProvider.createUserKnowledge(facts = listOf(fact))),
            PEER_ID,
        )

        val section = text.substringAfter("Из общения:").substringBefore("Статистика:")
        assertTrue(section.contains("- футбол"))
        assertFalse(section.contains("interest_football"))
        assertFalse(section.contains("7600001"))
        assertFalse(section.contains("2600000001"))
        assertFalse(section.contains("02.10.2026"))
        assertFalse(section.contains("2026"))
        assertFalse(section.contains("@"))
    }

    @Test
    fun `rendersHistoricalFactAsPreviously`() {
        val current = FakeModelProvider.createUserFact(
            category = UserFactCategory.INTEREST,
            factKey = "interest_sport",
            value = "футбол",
        )
        val historical = FakeModelProvider.createUserFact(
            category = UserFactCategory.INTEREST,
            status = UserFactStatus.HISTORICAL,
            factKey = "interest_sport",
            value = "теннис",
            supersededAt = OffsetDateTime.parse("2026-10-02T00:00:00Z"),
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(
                knowledge = FakeModelProvider.createUserKnowledge(facts = listOf(current, historical)),
            ),
            PEER_ID,
        )

        val section = text.substringAfter("Из общения:").substringBefore("Статистика:")
        assertTrue(section.contains("- ранее: теннис"))
        assertFalse(section.contains("02.10.2026"))
        assertFalse(section.contains("2026"))
    }

    @Test
    fun `rendersConflictingFactsAsContradictory`() {
        val minsk = FakeModelProvider.createUserFact(
            category = UserFactCategory.PROFILE_ATTRIBUTE,
            factKey = "city",
            value = "Минск",
            conflict = true,
            lastConfirmedAt = OffsetDateTime.parse("2026-10-02T00:00:00Z"),
        )
        val moscow = FakeModelProvider.createUserFact(
            category = UserFactCategory.PROFILE_ATTRIBUTE,
            factKey = "city",
            value = "Москва",
            conflict = true,
            lastConfirmedAt = OffsetDateTime.parse("2026-10-02T00:00:00Z"),
        )

        val text = ProfileDossierFormatter(ZoneId.of("Europe/Minsk")).format(
            userDossier(knowledge = FakeModelProvider.createUserKnowledge(facts = listOf(minsk, moscow))),
            PEER_ID,
        )

        val section = text.substringAfter("Из общения:").substringBefore("Статистика:")
        assertTrue(section.contains("- возможно, противоречивые данные: Минск"))
        assertTrue(section.contains("- возможно, противоречивые данные: Москва"))
        assertFalse(section.contains("- ранее:"))
        assertFalse(section.contains("02.10.2026"))
    }
}
