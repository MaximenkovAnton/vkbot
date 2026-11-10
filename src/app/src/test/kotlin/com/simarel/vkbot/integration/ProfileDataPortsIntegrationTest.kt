package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.adapter.input.event.MessageNewEventProcessor
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class ProfileDataPortsTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "vk.longpoll.enabled" to "false",
        "statistics.timezone" to "Europe/Minsk",
    )
}

@QuarkusTest
@TestProfile(ProfileDataPortsTestProfile::class)
class ProfileDataPortsIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var messageNewEventProcessor: MessageNewEventProcessor

    @Inject
    lateinit var profileRepository: JooqVkUserProfileRepository

    @Inject
    lateinit var findChatAuthorsPort: FindChatAuthorsPort

    @Inject
    lateinit var findStoredUserStatisticsPort: FindStoredUserStatisticsPort

    @Inject
    lateinit var findStoredUserProfileHistoryPort: FindStoredUserProfileHistoryPort

    private val seededProfiles = mutableSetOf<Long>()

    @Test
    fun `chat authors port returns distinct authors with counts`() {
        insertMessage(AUTHOR_ID, CHAT_PEER_ID, "first", "2026-01-05T10:00:00Z", messageId = 1)
        insertMessage(AUTHOR_ID, CHAT_PEER_ID, "second", "2026-01-05T10:01:00Z", messageId = 2)
        insertMessage(GROUP_AUTHOR_ID, CHAT_PEER_ID, "third", "2026-01-05T10:02:00Z", messageId = 3)
        insertMessage(DECOY_AUTHOR_ID, DECOY_PEER_ID, "decoy", "2026-01-05T10:03:00Z", messageId = 1)

        val authors = findChatAuthorsPort.execute(
            FakePersistencePortProvider.createFindChatAuthorsRequest(peerId = CHAT_PEER_ID),
        ).authors

        assertEquals(
            listOf<ChatAuthor>(
                FakeModelProvider.createChatAuthor(fromId = GROUP_AUTHOR_ID, messageCount = 1),
                FakeModelProvider.createChatAuthor(fromId = AUTHOR_ID, messageCount = 2),
            ),
            authors,
        )
    }

    @Test
    fun `chat authors port returns an empty list for a chat without stored messages`() {
        val authors = findChatAuthorsPort.execute(
            FakePersistencePortProvider.createFindChatAuthorsRequest(peerId = EMPTY_CHAT_PEER_ID),
        ).authors

        assertEquals(emptyList<ChatAuthor>(), authors)
    }

    @Test
    fun `statistics port returns materialized aggregates`() {
        saveMessage(fromId = STATISTICS_USER_ID, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00")
        saveMessage(fromId = STATISTICS_USER_ID, peerId = 111, text = "abcdef", date = "2026-01-05T11:00:00+03:00")
        saveMessage(fromId = STATISTICS_USER_ID, peerId = 222, text = "", date = "2026-01-06T23:30:00+03:00")

        val statistics = findStoredUserStatisticsPort.execute(
            FakePersistencePortProvider.createFindStoredUserStatisticsRequest(userId = STATISTICS_USER_ID),
        ).statistics

        assertEquals(EXPECTED_STATISTICS, statistics)
    }

    @Test
    fun `history port returns newest entries within the limit`() {
        insertHistory(profileId = HISTORY_PROFILE_ID, changedAt = "2026-01-01T10:00:00Z", changes = LAST_NAME_CHANGES)
        insertHistory(profileId = HISTORY_PROFILE_ID, changedAt = "2026-01-01T11:00:00Z", changes = CITY_CHANGES)

        val limited = findStoredUserProfileHistoryPort.execute(
            FakePersistencePortProvider.createFindStoredUserProfileHistoryRequest(
                profileId = HISTORY_PROFILE_ID,
                limit = 1,
            ),
        ).entries

        assertEquals(listOf(EXPECTED_CITY_ENTRY), limited)

        val all = findStoredUserProfileHistoryPort.execute(
            FakePersistencePortProvider.createFindStoredUserProfileHistoryRequest(
                profileId = HISTORY_PROFILE_ID,
                limit = 20,
            ),
        ).entries

        assertEquals(listOf(EXPECTED_CITY_ENTRY, EXPECTED_LAST_NAME_ENTRY), all)
    }

    @Test
    fun `history port returns an empty list when the limit is zero`() {
        insertHistory(profileId = EMPTY_HISTORY_PROFILE_ID, changedAt = "2026-01-01T10:00:00Z", changes = CITY_CHANGES)

        val entries = findStoredUserProfileHistoryPort.execute(
            FakePersistencePortProvider.createFindStoredUserProfileHistoryRequest(
                profileId = EMPTY_HISTORY_PROFILE_ID,
                limit = 0,
            ),
        ).entries

        assertEquals(emptyList<ProfileHistoryEntry>(), entries)
    }

    private fun saveMessage(fromId: Long, peerId: Long, text: String, date: String) {
        seedProfile(fromId)
        val message = FakeVoProvider.createMessage(
            date = FakeVoProvider.createDate(OffsetDateTime.parse(date)),
            fromId = FakeVoProvider.createFromId(fromId),
            peerId = FakeVoProvider.createPeerId(peerId),
            messageText = FakeVoProvider.createMessageText(text),
        )
        messageNewEventProcessor.process(objectMapper.writeValueAsString(message))
    }

    private fun seedProfile(userId: Long) {
        if (!seededProfiles.add(userId)) {
            return
        }
        profileRepository.persist(
            FakeModelProvider.createVkUserProfile(id = userId, lastUpdated = OffsetDateTime.now()),
        )
    }

    private fun insertMessage(fromId: Long, peerId: Long, text: String, date: String, messageId: Long) {
        withConnection { connection ->
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

    private fun insertHistory(profileId: Long, changedAt: String, changes: String) {
        withConnection { connection ->
            connection.prepareStatement(INSERT_HISTORY_SQL).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setLong(2, profileId)
                statement.setString(3, changes)
                statement.setObject(4, OffsetDateTime.parse(changedAt))
                statement.executeUpdate()
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    private companion object {
        const val CHAT_PEER_ID = 2_600_000_001L
        const val DECOY_PEER_ID = 2_600_000_002L
        const val EMPTY_CHAT_PEER_ID = 2_600_000_003L
        const val AUTHOR_ID = 7_600_001L
        const val DECOY_AUTHOR_ID = 7_600_004L
        const val GROUP_AUTHOR_ID = -232_142_875L
        const val STATISTICS_USER_ID = 7_600_002L
        const val HISTORY_PROFILE_ID = 7_600_003L
        const val EMPTY_HISTORY_PROFILE_ID = 7_600_005L

        const val LAST_NAME_CHANGES = "{\"lastName\": {\"old\": \"Petrov\", \"new\": \"Sidorov\"}}"
        const val CITY_CHANGES = "{\"city\": {\"old\": \"Moscow\", \"new\": \"Minsk\"}}"

        val EXPECTED_STATISTICS: UserStatistics = FakeModelProvider.createUserStatistics(
            userId = STATISTICS_USER_ID,
            messageCount = 3,
            totalLength = 9,
            averageLength = 3.0,
            firstMessageAt = OffsetDateTime.parse("2026-01-05T07:00:00Z"),
            lastMessageAt = OffsetDateTime.parse("2026-01-06T20:30:00Z"),
            chats = listOf(
                FakeModelProvider.createChatStatistics(
                    peerId = 111,
                    messageCount = 2,
                    totalLength = 9,
                    averageLength = 4.5,
                    lastMessageAt = OffsetDateTime.parse("2026-01-05T08:00:00Z"),
                ),
                FakeModelProvider.createChatStatistics(
                    peerId = 222,
                    messageCount = 1,
                    totalLength = 0,
                    averageLength = 0.0,
                    lastMessageAt = OffsetDateTime.parse("2026-01-06T20:30:00Z"),
                ),
            ),
            activityByHour = listOf(
                FakeModelProvider.createHourActivity(hour = 10, messageCount = 1),
                FakeModelProvider.createHourActivity(hour = 11, messageCount = 1),
                FakeModelProvider.createHourActivity(hour = 23, messageCount = 1),
            ),
            activityByDayOfWeek = listOf(
                FakeModelProvider.createDayOfWeekActivity(dayOfWeek = 1, messageCount = 2),
                FakeModelProvider.createDayOfWeekActivity(dayOfWeek = 2, messageCount = 1),
            ),
        )

        val EXPECTED_CITY_ENTRY: ProfileHistoryEntry = FakeModelProvider.createProfileHistoryEntry(
            profileId = HISTORY_PROFILE_ID,
            changedAt = OffsetDateTime.parse("2026-01-01T11:00:00Z"),
            changes = mapOf("city" to mapOf("old" to "Moscow", "new" to "Minsk")),
        )

        val EXPECTED_LAST_NAME_ENTRY: ProfileHistoryEntry = FakeModelProvider.createProfileHistoryEntry(
            profileId = HISTORY_PROFILE_ID,
            changedAt = OffsetDateTime.parse("2026-01-01T10:00:00Z"),
            changes = mapOf("lastName" to mapOf("old" to "Petrov", "new" to "Sidorov")),
        )

        const val INSERT_MESSAGE_SQL =
            "INSERT INTO messages (id, date, from_id, group_id, peer_id, conversation_message_id, message_text) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        const val INSERT_HISTORY_SQL =
            "INSERT INTO vk_user_profile_history (id, profile_id, changes, changed_at) " +
                "VALUES (?, ?, ?::jsonb, ?)"
    }
}
