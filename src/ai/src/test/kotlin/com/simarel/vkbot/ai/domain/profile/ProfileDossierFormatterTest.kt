package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkUserProfile
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
): ProfileDossier.UserDossier = ProfileDossier.UserDossier(
    profile = profile,
    statistics = statistics,
    history = history,
    chatMessageCount = chatMessageCount,
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
}
