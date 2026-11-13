package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.adapter.input.event.MessageNewEventProcessor
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserStatisticsReader
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkUserProfileTable
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class UserStatisticsMaterializationIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var messageNewEventProcessor: MessageNewEventProcessor

    @Inject
    lateinit var userProfileTable: VkUserProfileTable

    @Inject
    lateinit var userStatisticsReader: UserStatisticsReader

    private val seededProfiles = mutableSetOf<Long>()

    @Test
    fun `message new increments the three statistics tables`() {
        val userId = 7_610_002L

        saveMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        saveMessage(userId, peerId = 111, text = "ab", date = "2026-01-05T11:00:00+03:00", messageId = 2)
        saveMessage(userId, peerId = 222, text = "😀", date = "2026-01-05T12:00:00+03:00", messageId = 3)

        val totals = requireNotNull(totalsRow(userId))
        assertEquals(3L, totals.messageCount)
        assertEquals(6L, totals.totalLength)
        assertEquals(Instant.parse("2026-01-05T07:00:00Z"), totals.firstMessageAt?.toInstant())
        assertEquals(Instant.parse("2026-01-05T09:00:00Z"), totals.lastMessageAt?.toInstant())

        assertEquals(
            listOf(ChatRow(peerId = 111, messageCount = 2, totalLength = 5), ChatRow(222, 1, 1)),
            chatRows(userId),
        )
        assertEquals(
            listOf(
                HourRow(LocalDate.parse("2026-01-05"), 7, 1),
                HourRow(LocalDate.parse("2026-01-05"), 8, 1),
                HourRow(LocalDate.parse("2026-01-05"), 9, 1),
            ),
            hourRows(userId),
        )
    }

    @Test
    fun `statistics write failure rolls the message back`() {
        val userId = 7_610_009L
        seedProfile(userId)
        execute(
            "ALTER TABLE user_hour_statistics ADD CONSTRAINT tmp_statistics_atomicity_check " +
                "CHECK (user_id <> $userId)",
        )

        try {
            val failure = assertThrows(RuntimeException::class.java) {
                saveMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
            }
            assertTrue(isJooqDataAccessException(failure), "unexpected failure ${failure.javaClass.name}")
        } finally {
            execute("ALTER TABLE user_hour_statistics DROP CONSTRAINT IF EXISTS tmp_statistics_atomicity_check")
        }

        assertEquals(0L, messagesCount(userId))
        assertEquals(0L, aggregateRows("user_statistics", userId))
        assertEquals(0L, aggregateRows("user_chat_statistics", userId))
        assertEquals(0L, aggregateRows("user_hour_statistics", userId))
    }

    @Test
    fun `statistics are read from aggregates with the local hour and day of week`() {
        val userId = 7_610_003L

        saveMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        saveMessage(userId, peerId = 111, text = "abcdef", date = "2026-01-05T11:00:00+03:00", messageId = 2)
        saveMessage(userId, peerId = 222, text = "", date = "2026-01-06T23:30:00+03:00", messageId = 3)

        val minsk = userStatisticsReader.findByUser(userId, ZoneId.of("Europe/Minsk"))

        assertEquals(3L, minsk.messageCount)
        assertEquals(9L, minsk.totalLength)
        assertEquals(3.0, minsk.averageLength)
        assertEquals(Instant.parse("2026-01-05T07:00:00Z"), minsk.firstMessageAt?.toInstant())
        assertEquals(Instant.parse("2026-01-06T20:30:00Z"), minsk.lastMessageAt?.toInstant())
        assertEquals(listOf(111L, 222L), minsk.chats.map { it.peerId })
        assertEquals(listOf(2L, 1L), minsk.chats.map { it.messageCount })
        assertEquals(listOf(9L, 0L), minsk.chats.map { it.totalLength })
        assertEquals(listOf(4.5, 0.0), minsk.chats.map { it.averageLength })
        assertEquals(
            listOf(
                FakeModelProvider.createHourActivity(10, 1),
                FakeModelProvider.createHourActivity(11, 1),
                FakeModelProvider.createHourActivity(23, 1),
            ),
            minsk.activityByHour,
        )
        assertEquals(
            listOf(
                FakeModelProvider.createDayOfWeekActivity(1, 2),
                FakeModelProvider.createDayOfWeekActivity(2, 1),
            ),
            minsk.activityByDayOfWeek,
        )

        val utc = userStatisticsReader.findByUser(userId, ZoneId.of("UTC"))

        assertEquals(
            listOf(
                FakeModelProvider.createHourActivity(7, 1),
                FakeModelProvider.createHourActivity(8, 1),
                FakeModelProvider.createHourActivity(20, 1),
            ),
            utc.activityByHour,
        )
        assertEquals(
            listOf(
                FakeModelProvider.createDayOfWeekActivity(1, 2),
                FakeModelProvider.createDayOfWeekActivity(2, 1),
            ),
            utc.activityByDayOfWeek,
        )
    }

    @Test
    fun `statistics convert the utc bucket across the date boundary`() {
        val userId = 7_610_005L

        saveMessage(userId, peerId = 111, text = "abc", date = "2026-01-06T00:30:00+03:00", messageId = 1)

        val minsk = userStatisticsReader.findByUser(userId, ZoneId.of("Europe/Minsk"))
        assertEquals(
            listOf(
                FakeModelProvider.createHourActivity(0, 1),
            ),
            minsk.activityByHour,
        )
        assertEquals(
            listOf(
                FakeModelProvider.createDayOfWeekActivity(2, 1),
            ),
            minsk.activityByDayOfWeek,
        )

        val utc = userStatisticsReader.findByUser(userId, ZoneId.of("UTC"))
        assertEquals(
            listOf(
                FakeModelProvider.createHourActivity(21, 1),
            ),
            utc.activityByHour,
        )
        assertEquals(
            listOf(
                FakeModelProvider.createDayOfWeekActivity(1, 1),
            ),
            utc.activityByDayOfWeek,
        )
    }

    @Test
    fun `statistics of a user without aggregates are zeros`() {
        val userId = 7_610_004L
        deleteAggregates(userId)
        insertRawMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        insertRawMessage(userId, peerId = 111, text = "abcdef", date = "2026-01-05T11:00:00+03:00", messageId = 2)

        val statistics = userStatisticsReader.findByUser(userId, ZoneId.of("Europe/Minsk"))

        assertZeroStatistics(statistics, userId)
        assertEquals(2L, messagesCount(userId))
    }

    @Test
    fun `statistics do not depend on the messages journal after materialization`() {
        val userId = 7_610_007L

        saveMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        saveMessage(userId, peerId = 111, text = "abcdef", date = "2026-01-05T11:00:00+03:00", messageId = 2)
        saveMessage(userId, peerId = 222, text = "", date = "2026-01-06T23:30:00+03:00", messageId = 3)

        val before = userStatisticsReader.findByUser(userId, ZoneId.of("Europe/Minsk"))
        execute("DELETE FROM messages WHERE from_id = $userId")
        val after = userStatisticsReader.findByUser(userId, ZoneId.of("Europe/Minsk"))

        assertEquals(before, after)
        assertEquals(0L, messagesCount(userId))
        assertEquals(3L, after.messageCount)
        assertEquals(9L, after.totalLength)
        assertEquals(3.0, after.averageLength)
        assertEquals(Instant.parse("2026-01-05T07:00:00Z"), after.firstMessageAt?.toInstant())
        assertEquals(Instant.parse("2026-01-06T20:30:00Z"), after.lastMessageAt?.toInstant())
        assertEquals(listOf(111L, 222L), after.chats.map { it.peerId })
        assertEquals(listOf(2L, 1L), after.chats.map { it.messageCount })
        assertEquals(
            listOf(
                FakeModelProvider.createHourActivity(10, 1),
                FakeModelProvider.createHourActivity(11, 1),
                FakeModelProvider.createHourActivity(23, 1),
            ),
            after.activityByHour,
        )
        assertEquals(
            listOf(
                FakeModelProvider.createDayOfWeekActivity(1, 2),
                FakeModelProvider.createDayOfWeekActivity(2, 1),
            ),
            after.activityByDayOfWeek,
        )
    }

    @Test
    fun `statistics schema is applied by liquibase`() {
        assertEquals(ColumnSpec("bigint", "NO", "0"), column("user_statistics", "message_count"))
        assertEquals(ColumnSpec("bigint", "NO", "0"), column("user_statistics", "total_length"))
        assertEquals(ColumnSpec("timestamp with time zone", "YES", null), column("user_statistics", "first_message_at"))
        assertEquals(ColumnSpec("timestamp with time zone", "NO", "now()"), column("user_statistics", "updated_at"))
        assertEquals(ColumnSpec("smallint", "NO", null), column("user_hour_statistics", "utc_hour"))
        assertEquals(ColumnSpec("date", "NO", null), column("user_hour_statistics", "utc_date"))
        assertEquals(ColumnSpec("bigint", "NO", "0"), column("user_hour_statistics", "message_count"))
        assertEquals(ColumnSpec("bigint", "NO", null), column("user_chat_statistics", "user_id"))
        assertEquals(
            ColumnSpec("timestamp with time zone", "NO", null),
            column("user_chat_statistics", "last_message_at"),
        )
        assertEquals(1L, primaryKeyColumnCount("user_statistics"))
        assertEquals(2L, primaryKeyColumnCount("user_chat_statistics"))
        assertEquals(3L, primaryKeyColumnCount("user_hour_statistics"))
        assertEquals("c", constraintType("chk_user_hour_statistics_utc_hour"))
    }

    @Test
    fun `backfill script fills aggregates from messages and is idempotent`() {
        val userId = 7_610_011L

        insertRawMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        insertRawMessage(userId, peerId = 111, text = "abcdef", date = "2026-01-05T11:00:00+03:00", messageId = 2)
        insertRawMessage(userId, peerId = 222, text = "", date = "2026-01-06T23:30:00+03:00", messageId = 3)

        executeBackfill()

        assertEquals(3L, messagesCount(userId))
        val totals = requireNotNull(totalsRow(userId))
        assertEquals(3L, totals.messageCount)
        assertEquals(9L, totals.totalLength)
        assertEquals(Instant.parse("2026-01-05T07:00:00Z"), totals.firstMessageAt?.toInstant())
        assertEquals(Instant.parse("2026-01-06T20:30:00Z"), totals.lastMessageAt?.toInstant())
        assertEquals(
            listOf(ChatRow(peerId = 111, messageCount = 2, totalLength = 9), ChatRow(222, 1, 0)),
            chatRows(userId),
        )
        assertEquals(
            listOf(
                HourRow(LocalDate.parse("2026-01-05"), 7, 1),
                HourRow(LocalDate.parse("2026-01-05"), 8, 1),
                HourRow(LocalDate.parse("2026-01-06"), 20, 1),
            ),
            hourRows(userId),
        )

        executeBackfill()

        assertEquals(totals, totalsRow(userId))
        assertEquals(3L, messagesCount(userId))
        assertEquals(3, hourRows(userId).size)
        assertEquals(
            listOf(ChatRow(peerId = 111, messageCount = 2, totalLength = 9), ChatRow(222, 1, 0)),
            chatRows(userId),
        )
    }

    @Test
    fun `backfill script fails fast when another transaction holds a lock`() {
        val userId = 7_610_015L
        insertRawMessage(userId, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)

        dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                holder.createStatement().use {
                    it.execute("SELECT 1 FROM user_statistics WHERE user_id = $userId FOR UPDATE")
                }
                val startedAt = System.nanoTime()
                val failure = assertThrows(SQLException::class.java) { executeBackfill() }
                val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
                assertEquals("55P03", failure.sqlState)
                assertTrue(elapsedMs < MAX_LOCK_WAIT_MS, "lock wait took ${elapsedMs}ms")
            } finally {
                holder.rollback()
            }
        }

        assertEquals(0L, aggregateRows("user_statistics", userId))
        assertEquals(0L, aggregateRows("user_chat_statistics", userId))
        assertEquals(0L, aggregateRows("user_hour_statistics", userId))
    }

    @Test
    fun `backfill script rolls back completely when it fails mid-way`() {
        val stableUser = 7_610_014L
        val failingUser = 7_610_013L

        insertRawMessage(stableUser, peerId = 111, text = "abc", date = "2026-01-05T12:00:00+03:00", messageId = 1)
        executeBackfill()
        assertEquals(ChatRow(peerId = 111, messageCount = 1, totalLength = 3), chatRows(stableUser).single())

        insertRawMessage(failingUser, peerId = 111, text = "abc", date = "2026-01-05T10:00:00+03:00", messageId = 1)
        execute(
            "ALTER TABLE user_hour_statistics ADD CONSTRAINT tmp_backfill_atomicity_check " +
                "CHECK (user_id <> $failingUser)",
        )
        try {
            assertThrows(SQLException::class.java) { executeBackfill() }
        } finally {
            execute("ALTER TABLE user_hour_statistics DROP CONSTRAINT IF EXISTS tmp_backfill_atomicity_check")
        }

        val stableTotals = requireNotNull(totalsRow(stableUser))
        assertEquals(1L, stableTotals.messageCount)
        assertEquals(3L, stableTotals.totalLength)
        assertEquals(ChatRow(peerId = 111, messageCount = 1, totalLength = 3), chatRows(stableUser).single())
        assertEquals(0L, aggregateRows("user_statistics", failingUser))
        assertEquals(0L, aggregateRows("user_chat_statistics", failingUser))
        assertEquals(0L, aggregateRows("user_hour_statistics", failingUser))
    }

    private fun saveMessage(fromId: Long, peerId: Long, text: String, date: String, messageId: Long) {
        seedProfile(fromId)
        val message = FakeVoProvider.createMessage(
            date = FakeVoProvider.createDate(OffsetDateTime.parse(date)),
            fromId = FakeVoProvider.createFromId(fromId),
            peerId = FakeVoProvider.createPeerId(peerId),
            conversationMessageId = FakeVoProvider.createConversationMessageId(messageId),
            messageText = FakeVoProvider.createMessageText(text),
        )
        messageNewEventProcessor.process(objectMapper.writeValueAsString(message))
    }

    private fun seedProfile(userId: Long) {
        if (!seededProfiles.add(userId)) {
            return
        }
        val profile = FakeModelProvider.createVkUserProfile(id = userId, lastUpdated = OffsetDateTime.now())
        userProfileTable.persist(profile)
    }

    private fun insertRawMessage(fromId: Long, peerId: Long, text: String, date: String, messageId: Long) {
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

    private fun executeBackfill() {
        val script = requireNotNull(javaClass.classLoader.getResource(BACKFILL_SCRIPT)) {
            "missing backfill script $BACKFILL_SCRIPT"
        }.readText()
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { it.execute(script) }
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun totalsRow(userId: Long): TotalsRow? = withConnection { connection ->
        connection.prepareStatement(SELECT_TOTALS_SQL).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    return@withConnection null
                }
                TotalsRow(
                    messageCount = resultSet.getLong("message_count"),
                    totalLength = resultSet.getLong("total_length"),
                    firstMessageAt = resultSet.getObject("first_message_at", OffsetDateTime::class.java),
                    lastMessageAt = resultSet.getObject("last_message_at", OffsetDateTime::class.java),
                )
            }
        }
    }

    private fun chatRows(userId: Long): List<ChatRow> = withConnection { connection ->
        connection.prepareStatement(SELECT_CHATS_SQL).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            ChatRow(
                                peerId = resultSet.getLong("peer_id"),
                                messageCount = resultSet.getLong("message_count"),
                                totalLength = resultSet.getLong("total_length"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun hourRows(userId: Long): List<HourRow> = withConnection { connection ->
        connection.prepareStatement(SELECT_HOURS_SQL).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            HourRow(
                                utcDate = resultSet.getObject("utc_date", LocalDate::class.java),
                                utcHour = resultSet.getInt("utc_hour"),
                                messageCount = resultSet.getLong("message_count"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun messagesCount(fromId: Long): Long = withConnection { connection ->
        connection.prepareStatement("SELECT count(*) FROM messages WHERE from_id = ?").use { statement ->
            statement.setLong(1, fromId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getLong(1)
            }
        }
    }

    private fun aggregateRows(table: String, userId: Long): Long = withConnection { connection ->
        connection.prepareStatement("SELECT count(*) FROM $table WHERE user_id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getLong(1)
            }
        }
    }

    private fun deleteAggregates(userId: Long) {
        execute("DELETE FROM user_statistics WHERE user_id = $userId")
        execute("DELETE FROM user_chat_statistics WHERE user_id = $userId")
        execute("DELETE FROM user_hour_statistics WHERE user_id = $userId")
    }

    private fun column(table: String, columnName: String): ColumnSpec = withConnection { connection ->
        connection.prepareStatement(SELECT_COLUMN_SQL).use { statement ->
            statement.setString(1, table)
            statement.setString(2, columnName)
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next(), "missing column $table.$columnName")
                ColumnSpec(
                    dataType = resultSet.getString("data_type"),
                    isNullable = resultSet.getString("is_nullable"),
                    columnDefault = resultSet.getString("column_default"),
                )
            }
        }
    }

    private fun primaryKeyColumnCount(table: String): Long = withConnection { connection ->
        connection.prepareStatement(SELECT_PRIMARY_KEY_COUNT_SQL).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getLong(1)
            }
        }
    }

    private fun constraintType(constraintName: String): String? = withConnection { connection ->
        connection.prepareStatement(SELECT_CONSTRAINT_TYPE_SQL).use { statement ->
            statement.setString(1, constraintName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getString(1) else null
            }
        }
    }

    private fun execute(sql: String) {
        withConnection { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    private fun isJooqDataAccessException(throwable: Throwable): Boolean {
        var current: Class<*>? = throwable.javaClass
        while (current != null) {
            if (current.name == DATA_ACCESS_EXCEPTION) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    private fun assertZeroStatistics(statistics: UserStatistics, userId: Long) {
        assertEquals(userId, statistics.userId)
        assertEquals(0L, statistics.messageCount)
        assertEquals(0L, statistics.totalLength)
        assertEquals(0.0, statistics.averageLength)
        assertNull(statistics.firstMessageAt)
        assertNull(statistics.lastMessageAt)
        assertTrue(statistics.chats.isEmpty())
        assertTrue(statistics.activityByHour.isEmpty())
        assertTrue(statistics.activityByDayOfWeek.isEmpty())
    }

    private data class TotalsRow(
        val messageCount: Long,
        val totalLength: Long,
        val firstMessageAt: OffsetDateTime?,
        val lastMessageAt: OffsetDateTime?,
    )

    private data class ChatRow(val peerId: Long, val messageCount: Long, val totalLength: Long)

    private data class HourRow(val utcDate: LocalDate, val utcHour: Int, val messageCount: Long)

    private data class ColumnSpec(val dataType: String, val isNullable: String, val columnDefault: String?)

    private companion object {
        const val BACKFILL_SCRIPT = "db/backfill/backfill-user-statistics.sql"
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_LOCK_WAIT_MS = 10_000L
        const val DATA_ACCESS_EXCEPTION = "org.jooq.exception.DataAccessException"
        const val INSERT_MESSAGE_SQL =
            "INSERT INTO messages (id, date, from_id, group_id, peer_id, conversation_message_id, message_text) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        const val SELECT_TOTALS_SQL =
            "SELECT message_count, total_length, first_message_at, last_message_at " +
                "FROM user_statistics WHERE user_id = ?"
        const val SELECT_CHATS_SQL =
            "SELECT peer_id, message_count, total_length FROM user_chat_statistics " +
                "WHERE user_id = ? ORDER BY peer_id"
        const val SELECT_HOURS_SQL =
            "SELECT utc_date, utc_hour, message_count FROM user_hour_statistics " +
                "WHERE user_id = ? ORDER BY utc_date, utc_hour"
        const val SELECT_COLUMN_SQL =
            "SELECT data_type, is_nullable, column_default FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?"
        const val SELECT_PRIMARY_KEY_COUNT_SQL =
            "SELECT count(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_schema = 'public' AND tc.table_name = ? " +
                "AND tc.constraint_type = 'PRIMARY KEY'"
        const val SELECT_CONSTRAINT_TYPE_SQL = "SELECT contype::text FROM pg_constraint WHERE conname = ?"
    }
}
