package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.adapter.input.event.MessageNewEventProcessor
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkUserProfileTable
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-05T10:00:00+03:00")
private const val PEER_ID: Long = 111L
private const val DUPLICATE_INBOX_ID: Long = 42L
private val FROM_IDS: List<Long> = listOf(7_610_101L, 7_610_102L, 7_610_103L, 7_610_104L, 7_610_105L)

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class MessageDedupIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var messages: MessageTable

    @Inject
    lateinit var messageNewEventProcessor: MessageNewEventProcessor

    @Inject
    lateinit var userProfileTable: VkUserProfileTable

    @BeforeEach
    fun cleanStorage() {
        val placeholders = FROM_IDS.joinToString(", ") { "?" }
        val ids = FROM_IDS.toTypedArray()
        execute("DELETE FROM messages WHERE from_id IN ($placeholders)", *ids)
        execute("DELETE FROM user_statistics WHERE user_id IN ($placeholders)", *ids)
        execute("DELETE FROM user_chat_statistics WHERE user_id IN ($placeholders)", *ids)
        execute("DELETE FROM user_hour_statistics WHERE user_id IN ($placeholders)", *ids)
        FROM_IDS.forEach { fromId ->
            userProfileTable.persist(
                FakeModelProvider.createVkUserProfile(id = fromId, lastUpdated = OffsetDateTime.now()),
            )
        }
    }

    @Test
    fun `message table persists the inbox id and reports the duplicate`() {
        val first = messages.persist(messageEntity(fromId = 7_610_101, inboxId = DUPLICATE_INBOX_ID))
        val second = messages.persist(messageEntity(fromId = 7_610_101, inboxId = DUPLICATE_INBOX_ID))

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, messageCount(fromId = 7_610_101, inboxId = DUPLICATE_INBOX_ID))
        val row = singleMessageRow(fromId = 7_610_101)
        assertEquals(DUPLICATE_INBOX_ID, row.inboxId)
        assertEquals("abc", row.messageText)
    }

    @Test
    fun `duplicate message new keeps one row and one statistics increment`() {
        val json = messageJson(fromId = 7_610_102, conversationMessageId = 1, inboxId = DUPLICATE_INBOX_ID)

        messageNewEventProcessor.process(json)
        messageNewEventProcessor.process(json)

        assertEquals(1, messageCount(fromId = 7_610_102, inboxId = DUPLICATE_INBOX_ID))
        val totals = totalsRow(userId = 7_610_102)
        assertEquals(1L, totals.messageCount)
        assertEquals(3L, totals.totalLength)
        assertEquals(1L, chatMessageCount(userId = 7_610_102))
    }

    @Test
    fun `different inbox ids create two rows and two increments`() {
        messageNewEventProcessor.process(messageJson(fromId = 7_610_103, conversationMessageId = 1, inboxId = 43))
        messageNewEventProcessor.process(messageJson(fromId = 7_610_103, conversationMessageId = 2, inboxId = 44))

        assertEquals(2, messageCount(fromId = 7_610_103))
        assertEquals(listOf(43L, 44L), messageRows(fromId = 7_610_103))
        val totals = totalsRow(userId = 7_610_103)
        assertEquals(2L, totals.messageCount)
        assertEquals(6L, totals.totalLength)
        assertEquals(2L, chatMessageCount(userId = 7_610_103))
    }

    @Test
    fun `legacy message without inbox id is saved every time`() {
        val json = messageJson(fromId = 7_610_104, conversationMessageId = 1, inboxId = null)

        messageNewEventProcessor.process(json)
        messageNewEventProcessor.process(json)

        assertEquals(2, messagesWithNullInboxCount(fromId = 7_610_104))
        val totals = totalsRow(userId = 7_610_104)
        assertEquals(2L, totals.messageCount)
        assertEquals(6L, totals.totalLength)
    }

    @Test
    fun `simultaneous persists of the same inbox id insert once`() {
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                executor.submit<Boolean> {
                    barrier.await(10, TimeUnit.SECONDS)
                    messages.persist(messageEntity(fromId = 7_610_105, inboxId = DUPLICATE_INBOX_ID))
                }
            }
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it })
            assertEquals(1, results.count { !it })
            assertEquals(1, messageCount(fromId = 7_610_105, inboxId = DUPLICATE_INBOX_ID))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun messageEntity(fromId: Long, inboxId: Long): MessageEntity = MessageEntity().apply {
        id = UUID.randomUUID()
        date = FIXED_DATE
        groupId = 1
        this.fromId = fromId
        peerId = PEER_ID
        conversationMessageId = 1
        messageText = "abc"
        this.inboxId = inboxId
    }

    private fun messageJson(fromId: Long, conversationMessageId: Long, inboxId: Long?): String {
        val message = FakeMessageProvider.createMessage(
            date = FakeVoProvider.createDate(FIXED_DATE),
            fromId = FakeVoProvider.createFromId(fromId),
            groupId = FakeVoProvider.createGroupId(1),
            peerId = FakeVoProvider.createPeerId(PEER_ID),
            conversationMessageId = FakeVoProvider.createConversationMessageId(conversationMessageId),
            messageText = FakeVoProvider.createMessageText("abc"),
            inboxMessageId = inboxId?.let { FakeVoProvider.createInboxMessageId(it) },
        )
        return objectMapper.writeValueAsString(message)
    }

    private fun singleMessageRow(fromId: Long): MessageRow = query(
        "SELECT inbox_id, message_text FROM messages WHERE from_id = ?",
        fromId,
    ) { rows -> MessageRow(inboxId = nullableLong(rows, "inbox_id"), messageText = rows.getString("message_text")) }
        ?: error("missing messages row for from_id=$fromId")

    private fun messageRows(fromId: Long): List<Long> = queryAll(
        "SELECT inbox_id FROM messages WHERE from_id = ? ORDER BY inbox_id",
        fromId,
    ) { rows -> nullableLong(rows, "inbox_id") }.filterNotNull()

    private fun totalsRow(userId: Long): TotalsRow = query(
        "SELECT message_count, total_length FROM user_statistics WHERE user_id = ?",
        userId,
    ) { rows -> TotalsRow(rows.getLong("message_count"), rows.getLong("total_length")) }
        ?: error("missing user_statistics row for user_id=$userId")

    private fun chatMessageCount(userId: Long): Long = query(
        "SELECT message_count FROM user_chat_statistics WHERE user_id = ? AND peer_id = ?",
        userId,
        PEER_ID,
    ) { rows -> rows.getLong("message_count") } ?: error("missing user_chat_statistics row for user_id=$userId")

    private fun messageCount(fromId: Long, inboxId: Long? = null): Int = if (inboxId == null) {
        countRows("SELECT count(*) FROM messages WHERE from_id = ?", fromId)
    } else {
        countRows("SELECT count(*) FROM messages WHERE from_id = ? AND inbox_id = ?", fromId, inboxId)
    }

    private fun messagesWithNullInboxCount(
        fromId: Long,
    ): Int = countRows("SELECT count(*) FROM messages WHERE from_id = ? AND inbox_id IS NULL", fromId)

    private fun nullableLong(rows: ResultSet, column: String): Long? {
        val value = rows.getLong(column)
        return if (rows.wasNull()) null else value
    }

    private fun countRows(
        sql: String,
        vararg parameters: Any?,
    ): Int = query(sql, *parameters) { rows -> rows.getInt(1) } ?: 0

    private fun <T> query(
        sql: String,
        vararg parameters: Any?,
        read: (ResultSet) -> T,
    ): T? = withStatement(sql, *parameters) { statement ->
        statement.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
    }

    private fun <T> queryAll(
        sql: String,
        vararg parameters: Any?,
        read: (ResultSet) -> T,
    ): List<T> = withStatement(sql, *parameters) { statement ->
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(read(rows))
                }
            }
        }
    }

    private fun <T> withStatement(sql: String, vararg parameters: Any?, block: (PreparedStatement) -> T): T {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                return block(statement)
            }
        }
    }

    private fun execute(sql: String, vararg parameters: Any?) {
        withStatement(sql, *parameters) { statement -> statement.executeUpdate() }
    }

    private data class MessageRow(
        val inboxId: Long?,
        val messageText: String?,
    )

    private data class TotalsRow(
        val messageCount: Long,
        val totalLength: Long,
    )
}
