package com.simarel.vkbot.integration

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserStatisticsRepository
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.domain.profile.ProfileDataMerger
import com.simarel.vkbot.persistence.domain.statistics.DayOfWeekActivity
import com.simarel.vkbot.persistence.domain.statistics.HourActivity
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class ProfileHistoryAndStatisticsIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var profileRepository: JooqVkUserProfileRepository

    @Inject
    lateinit var statisticsRepository: JooqUserStatisticsRepository

    @Test
    fun `profile actualization keeps old values of changed fields in history`() {
        val profileId = 7_610_001L
        val stored = FakeModelProvider.createVkUserProfile(
            id = profileId,
            firstName = "Ivan",
            city = "Moscow",
            profileData = mapOf(
                "first_name" to "Ivan",
                "city" to mapOf("id" to 1, "title" to "Moscow"),
                "about" to "about me",
            ),
        )
        val current = FakeModelProvider.createVkUserProfile(
            id = profileId,
            firstName = "Ivan",
            city = "Minsk",
            profileData = mapOf(
                "first_name" to "Ivan",
                "city" to mapOf("id" to 2, "title" to "Minsk"),
                "about" to "about me",
            ),
        )

        profileRepository.persist(stored, null)
        profileRepository.persist(current, ProfileDataMerger.diff(stored, current))

        val history = profileRepository.findHistory(profileId, limit = 10, offset = 0)

        assertEquals(1, history.size)
        val entry = history.single()
        assertEquals(profileId, entry.profileId)
        assertNotNull(entry.changedAt)
        val cityChange = entry.changes["city"] as Map<*, *>
        assertEquals(mapOf("id" to 1, "title" to "Moscow"), cityChange["old"])
        assertEquals(mapOf("id" to 2, "title" to "Minsk"), cityChange["new"])
        assertNull(entry.changes["about"])
    }

    @Test
    fun `statistics aggregate messages by chat, local hour and day of week`() {
        val userId = 7_610_002L
        insertMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        insertMessage(userId, peerId = 111, text = "abcdef", date = "2026-01-05T11:00:00+03:00", messageId = 2)
        insertMessage(userId, peerId = 222, text = "", date = "2026-01-06T23:30:00+03:00", messageId = 3)

        val statistics = statisticsRepository.findByUser(userId, ZoneId.of("Europe/Minsk"))

        assertEquals(3, statistics.messageCount)
        assertEquals(9, statistics.totalLength)
        assertEquals(3.0, statistics.averageLength)
        assertNotNull(statistics.firstMessageAt)
        assertNotNull(statistics.lastMessageAt)
        assertEquals(listOf(111L, 222L), statistics.chats.map { it.peerId })
        assertEquals(listOf(2L, 1L), statistics.chats.map { it.messageCount })
        assertEquals(listOf(HourActivity(10, 1), HourActivity(11, 1), HourActivity(23, 1)), statistics.activityByHour)
        assertEquals(
            listOf(DayOfWeekActivity(1, 2), DayOfWeekActivity(2, 1)),
            statistics.activityByDayOfWeek,
        )
    }

    @Test
    fun `statistics of a user without messages are empty`() {
        val statistics = statisticsRepository.findByUser(7_610_003L, ZoneId.of("Europe/Minsk"))

        assertEquals(0, statistics.messageCount)
        assertEquals(0, statistics.totalLength)
        assertEquals(0.0, statistics.averageLength)
        assertNull(statistics.firstMessageAt)
        assertNull(statistics.lastMessageAt)
        assertTrue(statistics.chats.isEmpty())
        assertTrue(statistics.activityByHour.isEmpty())
        assertTrue(statistics.activityByDayOfWeek.isEmpty())
    }

    private fun insertMessage(fromId: Long, peerId: Long, text: String, date: String, messageId: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_MESSAGE_SQL).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, OffsetDateTime.parse(date))
                statement.setLong(3, fromId)
                statement.setLong(4, 0)
                statement.setLong(5, peerId)
                statement.setLong(6, messageId)
                statement.setString(7, text)
                statement.executeUpdate()
            }
        }
    }

    private companion object {
        const val INSERT_MESSAGE_SQL =
            "INSERT INTO messages " +
                "(id, date, from_id, group_id, peer_id, conversation_message_id, message_text, forwarded_messages) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NULL)"
    }
}
