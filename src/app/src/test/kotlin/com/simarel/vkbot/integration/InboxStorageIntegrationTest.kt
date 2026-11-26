package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.output.persistence.AcquireLongPollLeasePort
import com.simarel.vkbot.share.port.output.persistence.PurgeIncomingMessagesPort
import com.simarel.vkbot.share.port.output.persistence.RegisterIncomingMessagePort
import com.simarel.vkbot.share.port.output.persistence.ReleaseLongPollLeasePort
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private const val PAYLOAD = """{"type":"message_new","object":{"id":1}}"""

class InboxStorageTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf("vk.longpoll.enabled" to "false")
}

@QuarkusTest
@TestProfile(InboxStorageTestProfile::class)
class InboxStorageIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var dsl: DSLContext

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var registerIncomingMessagePort: RegisterIncomingMessagePort

    @Inject
    lateinit var purgeIncomingMessagesPort: PurgeIncomingMessagesPort

    @Inject
    lateinit var acquireLongPollLeasePort: AcquireLongPollLeasePort

    @Inject
    lateinit var releaseLongPollLeasePort: ReleaseLongPollLeasePort

    @BeforeEach
    fun cleanStorage() {
        execute("DELETE FROM incoming_messages")
        execute("DELETE FROM long_poll_lease")
        execute("DELETE FROM messages WHERE peer_id = ?", MESSAGES_PEER_ID)
    }

    @Test
    fun `register stores the raw payload and returns its generated id`() {
        val response = registerIncomingMessagePort.execute(
            FakePersistencePortProvider.createRegisterIncomingMessageRequest(
                peerId = 101,
                conversationMessageId = 202,
                payload = PAYLOAD,
            ),
        )

        assertTrue(response.inboxMessageId.value > 0)
        val row = incomingRow(peerId = 101, conversationMessageId = 202)
        assertNotNull(row)
        assertEquals(response.inboxMessageId.value, row?.id)
        assertEquals(objectMapper.readTree(PAYLOAD), objectMapper.readTree(row?.payload))
        assertNotNull(row?.receivedAt)
        assertEquals(1, countRows("SELECT count(*) FROM incoming_messages"))
    }

    @Test
    fun `repeated register keeps the id and refreshes the payload and received_at`() {
        val first = registerIncomingMessagePort.execute(
            FakePersistencePortProvider.createRegisterIncomingMessageRequest(
                peerId = 101,
                conversationMessageId = 202,
                payload = """{"v":1}""",
            ),
        )
        execute(
            "UPDATE incoming_messages SET received_at = now() - interval '1 hour' WHERE id = ?",
            first.inboxMessageId.value,
        )
        val previous = incomingRowRequired(peerId = 101, conversationMessageId = 202).receivedAt

        val second = registerIncomingMessagePort.execute(
            FakePersistencePortProvider.createRegisterIncomingMessageRequest(
                peerId = 101,
                conversationMessageId = 202,
                payload = """{"v":2}""",
            ),
        )

        assertEquals(first.inboxMessageId.value, second.inboxMessageId.value)
        assertEquals(1, countRows("SELECT count(*) FROM incoming_messages"))
        val row = incomingRowRequired(peerId = 101, conversationMessageId = 202)
        assertEquals(objectMapper.readTree("""{"v":2}"""), objectMapper.readTree(row.payload))
        assertTrue(row.receivedAt.toInstant().isAfter(previous.toInstant()))
    }

    @Test
    fun `purge deletes only the rows older than the cutoff and counts them`() {
        insertIncoming(peerId = 101, conversationMessageId = 301, receivedAt = "now() - interval '25 hours'")
        insertIncoming(peerId = 101, conversationMessageId = 302, receivedAt = "now() - interval '23 hours'")
        val until = OffsetDateTime.now().minusHours(24)

        val first = purgeIncomingMessagesPort.execute(
            FakePersistencePortProvider.createPurgeIncomingMessagesRequest(until = until),
        )
        val second = purgeIncomingMessagesPort.execute(
            FakePersistencePortProvider.createPurgeIncomingMessagesRequest(until = until),
        )

        assertEquals(1, first.deleted)
        assertEquals(0, second.deleted)
        execute("DELETE FROM incoming_messages")
        assertEquals(
            0,
            purgeIncomingMessagesPort.execute(
                FakePersistencePortProvider.createPurgeIncomingMessagesRequest(until = until),
            ).deleted,
        )
    }

    @Test
    fun `free lease is acquired by the caller`() {
        val response = acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-a"))

        assertTrue(response.acquired)
        val lease = leaseRow()
        assertEquals(1.toShort(), lease.id)
        assertEquals("owner-a", lease.owner)
        assertLeaseInWindow()
    }

    @Test
    fun `a lease held by another owner is not acquired before it expires`() {
        acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-a"))
        val before = leaseRow()

        val response = acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-b"))

        assertFalse(response.acquired)
        val after = leaseRow()
        assertEquals("owner-a", after.owner)
        assertEquals(before.leaseUntil.toInstant(), after.leaseUntil.toInstant())
    }

    @Test
    fun `an expired lease is taken over by the next acquire`() {
        execute(
            "INSERT INTO long_poll_lease (id, owner, lease_until) " +
                "VALUES (1, 'owner-a', now() - interval '1 second')",
        )

        val response = acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-b"))

        assertTrue(response.acquired)
        assertEquals("owner-b", leaseRow().owner)
        assertLeaseInWindow()
    }

    @Test
    fun `the current owner renews its own lease`() {
        execute(
            "INSERT INTO long_poll_lease (id, owner, lease_until) " +
                "VALUES (1, 'owner-a', now() + interval '10 seconds')",
        )

        val response = acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-a"))

        assertTrue(response.acquired)
        assertEquals("owner-a", leaseRow().owner)
        assertLeaseInWindow()
    }

    @Test
    fun `release frees the lease only for the current owner`() {
        assertTrue(acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-a")).acquired)
        releaseLongPollLeasePort.execute(
            FakePersistencePortProvider.createReleaseLongPollLeaseRequest(owner = "owner-a"),
        )
        assertTrue(acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-b")).acquired)

        releaseLongPollLeasePort.execute(
            FakePersistencePortProvider.createReleaseLongPollLeaseRequest(owner = "owner-c"),
        )

        assertFalse(acquireLongPollLeasePort.execute(leaseRequest(owner = "owner-a")).acquired)
        assertEquals("owner-b", leaseRow().owner)
    }

    @Test
    fun `messages inbox id is unique while nulls do not conflict`() {
        assertEquals("inbox_id", Tables.Messages.INBOX_ID.name)

        insertMessageRow(inboxId = null)
        insertMessageRow(inboxId = null)
        insertMessageRow(inboxId = 7)

        val exception = assertThrows(DataAccessException::class.java) { insertMessageRow(inboxId = 7) }

        assertEquals("23505", sqlState(exception))
        assertEquals(
            2,
            countRows(
                "SELECT count(*) FROM messages WHERE peer_id = ? AND inbox_id IS NULL",
                MESSAGES_PEER_ID,
            ),
        )
        assertEquals(1, countRows("SELECT count(*) FROM messages WHERE peer_id = ? AND inbox_id = 7", MESSAGES_PEER_ID))
    }

    @Test
    fun `message json carries the inbox id and legacy json stays readable`() {
        val message = FakeMessageProvider.createMessage(
            peerId = FakeVoProvider.createPeerId(101),
            conversationMessageId = FakeVoProvider.createConversationMessageId(202),
            inboxMessageId = FakeVoProvider.createInboxMessageId(42),
        )

        val json = objectMapper.writeValueAsString(message)

        assertTrue(json.contains("\"inboxMessageId\":42"))
        assertEquals(42L, objectMapper.readValue(json, Message::class.java).inboxMessageId?.value)
        val legacyJson = objectMapper.readTree(json).apply {
            (this as ObjectNode).remove("inboxMessageId")
        }.toString()
        assertNull(objectMapper.readValue(legacyJson, Message::class.java).inboxMessageId)
    }

    @Test
    fun `simultaneous acquire lets exactly one caller win`() {
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val owners = listOf("owner-race-a", "owner-race-b")
        try {
            val futures = owners.map { owner ->
                executor.submit<Boolean> {
                    barrier.await(10, TimeUnit.SECONDS)
                    acquireLongPollLeasePort.execute(leaseRequest(owner = owner)).acquired
                }
            }
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it })
            assertEquals(1, results.count { !it })
            assertEquals(1, countRows("SELECT count(*) FROM long_poll_lease"))
            assertEquals(owners[results.indexOf(true)], leaseRow().owner)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun leaseRequest(owner: String) = FakePersistencePortProvider.createAcquireLongPollLeaseRequest(
        owner = owner,
        ttl = Duration.ofSeconds(40),
    )

    private fun insertIncoming(peerId: Long, conversationMessageId: Long, receivedAt: String) {
        execute(
            "INSERT INTO incoming_messages (peer_id, conversation_message_id, payload, received_at) " +
                "VALUES (?, ?, ?::jsonb, $receivedAt)",
            peerId,
            conversationMessageId,
            PAYLOAD,
        )
    }

    private fun insertMessageRow(inboxId: Long?) {
        dsl.insertInto(Tables.Messages.TABLE)
            .columns(
                Tables.Messages.ID,
                Tables.Messages.DATE,
                Tables.Messages.FROM_ID,
                Tables.Messages.GROUP_ID,
                Tables.Messages.PEER_ID,
                Tables.Messages.CONVERSATION_MESSAGE_ID,
                Tables.Messages.MESSAGE_TEXT,
                Tables.Messages.INBOX_ID,
            )
            .values(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                1L,
                2L,
                MESSAGES_PEER_ID,
                nextConversationMessageId(),
                "text",
                inboxId,
            )
            .execute()
    }

    private fun incomingRow(peerId: Long, conversationMessageId: Long): IncomingRow? = queryOne(
        "SELECT id, payload::text AS payload, received_at FROM incoming_messages " +
            "WHERE peer_id = ? AND conversation_message_id = ?",
        peerId,
        conversationMessageId,
    ) { rows ->
        IncomingRow(
            id = rows.getLong("id"),
            payload = rows.getString("payload"),
            receivedAt = rows.getObject("received_at", OffsetDateTime::class.java),
        )
    }

    private fun incomingRowRequired(peerId: Long, conversationMessageId: Long): IncomingRow {
        val row = incomingRow(peerId, conversationMessageId)
        return row ?: error("incoming message row is missing")
    }

    private fun leaseRow(): LeaseRow = queryOne(
        "SELECT id, owner, lease_until FROM long_poll_lease",
    ) { rows ->
        LeaseRow(
            id = rows.getShort("id"),
            owner = rows.getString("owner"),
            leaseUntil = rows.getObject("lease_until", OffsetDateTime::class.java),
        )
    } ?: error("lease row is missing")

    private fun assertLeaseInWindow() {
        val seconds = queryOne(
            "SELECT EXTRACT(EPOCH FROM (lease_until - now())) FROM long_poll_lease WHERE id = 1",
        ) { rows -> rows.getDouble(1) } ?: error("lease row is missing")

        assertTrue(seconds in 35.0..41.0, "lease_until must be about 40s ahead, was $seconds")
    }

    private fun countRows(sql: String, vararg parameters: Any?): Int {
        val count = queryOne(sql, *parameters) { rows -> rows.getInt(1) }
        return count ?: 0
    }

    private fun <T> queryOne(sql: String, vararg parameters: Any?, read: (ResultSet) -> T): T? {
        val rowsReader: (ResultSet) -> T? = { rows -> if (rows.next()) read(rows) else null }
        val result = withStatement(sql, *parameters) { statement ->
            statement.executeQuery().use { rows -> rowsReader(rows) }
        }
        return result
    }

    private fun <T> withStatement(sql: String, vararg parameters: Any?, block: (PreparedStatement) -> T): T {
        val connection = dataSource.connection
        return connection.use { openedConnection ->
            openedConnection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
                block(statement)
            }
        }
    }

    private fun execute(sql: String, vararg parameters: Any?) {
        withStatement(sql, *parameters) { statement -> statement.executeUpdate() }
    }

    private fun sqlState(exception: Throwable): String? {
        var current: Throwable? = exception
        while (current != null) {
            if (current is SQLException) {
                return current.sqlState
            }
            current = current.cause
        }
        return null
    }

    private fun nextConversationMessageId(): Long = countRows(
        "SELECT count(*) FROM messages WHERE peer_id = ?",
        MESSAGES_PEER_ID,
    ).toLong() + 1

    private data class IncomingRow(
        val id: Long,
        val payload: String,
        val receivedAt: OffsetDateTime,
    )

    private data class LeaseRow(
        val id: Short,
        val owner: String,
        val leaseUntil: OffsetDateTime,
    )

    private companion object {
        const val MESSAGES_PEER_ID = 555L
    }
}
