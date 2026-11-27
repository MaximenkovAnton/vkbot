package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.receiver.adapter.input.schedule.InboxCleanupRunner
import com.simarel.vkbot.receiver.adapter.input.vk.LongPollRunner
import com.simarel.vkbot.receiver.adapter.input.vk.mapper.MessageMapper
import com.simarel.vkbot.receiver.domain.exception.VkEventPublishException
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.output.CheckLongPollPort
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import com.simarel.vkbot.share.port.output.persistence.AcquireLongPollLeasePort
import com.simarel.vkbot.share.port.output.persistence.FindLongPollTsPort
import com.simarel.vkbot.share.port.output.persistence.RegisterIncomingMessagePort
import com.simarel.vkbot.share.port.output.persistence.ReleaseLongPollLeasePort
import com.simarel.vkbot.share.port.output.persistence.SaveLongPollTsPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import com.simarel.vkbot.testfixtures.vkFacade.FakeVkFacadeProvider
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class InboxE2eTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf("vk.longpoll.enabled" to "false")
}

@QuarkusTest
@TestProfile(InboxE2eTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class InboxE2eIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var getLongPollServerPort: GetLongPollServerPort

    @Inject
    lateinit var checkLongPollPort: CheckLongPollPort

    @Inject
    lateinit var receiveMessageInputPort: ReceiveMessageInputPort

    @Inject
    lateinit var findLongPollTsPort: FindLongPollTsPort

    @Inject
    lateinit var saveLongPollTsPort: SaveLongPollTsPort

    @Inject
    lateinit var acquireLongPollLeasePort: AcquireLongPollLeasePort

    @Inject
    lateinit var releaseLongPollLeasePort: ReleaseLongPollLeasePort

    @Inject
    lateinit var messageMapper: MessageMapper

    @Inject
    lateinit var cleanupRunner: InboxCleanupRunner

    @Inject
    lateinit var registerIncomingMessagePort: RegisterIncomingMessagePort

    @Inject
    lateinit var vkSendMessageInputPort: VkSendMessageInputPort

    private val wireMock get() = WireMockTestResource.wireMockServer

    @BeforeEach
    fun cleanStorage() {
        WireMockTestResource.resetToDefaultStubs()
        execute("DELETE FROM incoming_messages")
        execute("DELETE FROM long_poll_lease")
        execute("DELETE FROM long_poll_position")
        for (peerId in listOf(DUPLICATE_PEER_ID, RETRY_PEER_ID, CLEANUP_PEER_ID)) {
            execute("DELETE FROM messages WHERE peer_id = ?", peerId)
        }
        for (userId in listOf(DEFAULT_FROM_ID, CLEANUP_FROM_ID)) {
            execute("DELETE FROM user_statistics WHERE user_id = ?", userId)
            execute("DELETE FROM user_chat_statistics WHERE user_id = ?", userId)
            execute("DELETE FROM user_hour_statistics WHERE user_id = ?", userId)
        }
    }

    @Test
    fun `duplicate message new delivery stores one row increments statistics once and answers once`() {
        stubVkApi()
        stubLongPollSession(ts = "1")
        stubPoll(
            messageNewBatch(
                ts = "2",
                conversationMessageId = DUPLICATE_CONVERSATION_MESSAGE_ID,
                peerId = DUPLICATE_PEER_ID,
            ),
        )
        val runner = runner()

        runner.pollOnce()
        val inboxId = singleInboxId(peerId = DUPLICATE_PEER_ID)
        runner.pollOnce()

        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_MILLIS, TimeUnit.MILLISECONDS).untilAsserted {
            assertEquals(1, messageCount(peerId = DUPLICATE_PEER_ID))
        }
        assertEquals(1, incomingCount(peerId = DUPLICATE_PEER_ID))
        assertEquals(inboxId, singleInboxId(peerId = DUPLICATE_PEER_ID))
        assertEquals(inboxId, messageInboxId(peerId = DUPLICATE_PEER_ID))
        assertEquals(MESSAGE_TEXT, messageText(peerId = DUPLICATE_PEER_ID))
        val totals = totalsRow(userId = DEFAULT_FROM_ID)
        assertEquals(1L, totals.first)
        assertEquals(MESSAGE_TEXT.length.toLong(), totals.second)
        assertEquals(1L, chatMessageCount(userId = DEFAULT_FROM_ID))
        wireMock.verify(getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("2")))

        val expectedRandomId = randomIdOfInbox(inboxId)
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_MILLIS, TimeUnit.MILLISECONDS).untilAsserted {
            assertEquals(
                listOf(SentMessage(peerId = DUPLICATE_PEER_ID, randomId = expectedRandomId)),
                sentMessages().filter { it.peerId == DUPLICATE_PEER_ID },
            )
        }
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).during(ABSENCE_WINDOW_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertEquals(1, sentMessages().count { it.peerId == DUPLICATE_PEER_ID })
        }
    }

    @Test
    fun `registration failure keeps the poll position and the update is retried after it recovers`() {
        stubVkApi()
        stubLongPollSession(ts = "1")
        val update = messageNewUpdate(conversationMessageId = RETRY_CONVERSATION_MESSAGE_ID, peerId = RETRY_PEER_ID)
        stubPoll(
            messageNewBatch(
                ts = "2",
                conversationMessageId = RETRY_CONVERSATION_MESSAGE_ID,
                peerId = RETRY_PEER_ID,
            ),
        )
        val runner = runner()
        failIncomingMessageRegistration()
        try {
            assertThrows(VkEventPublishException::class.java) { runner.pollOnce() }
            assertNull(savedTs())
            assertEquals(0, messageCount(peerId = RETRY_PEER_ID))
        } finally {
            allowIncomingMessageRegistration()
        }

        runner.pollOnce()

        assertEquals("2", savedTs())
        val inboxId = singleInboxId(peerId = RETRY_PEER_ID)
        assertTrue(inboxId > 0)
        assertEquals(objectMapper.readTree(update), incomingPayload(peerId = RETRY_PEER_ID))
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_MILLIS, TimeUnit.MILLISECONDS).untilAsserted {
            assertEquals(1, messageCount(peerId = RETRY_PEER_ID))
        }
        assertEquals(inboxId, messageInboxId(peerId = RETRY_PEER_ID))
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("1")))
    }

    @Test
    fun `cleanup deletes inbox rows older than the retention and keeps fresh rows and stored messages`() {
        val staleId = registerIncoming(peerId = CLEANUP_PEER_ID, conversationMessageId = 1)
        val freshId = registerIncoming(peerId = CLEANUP_PEER_ID, conversationMessageId = 2)
        execute(
            "UPDATE incoming_messages SET received_at = now() - interval '24 hours 1 minute' WHERE id = ?",
            staleId,
        )
        execute(
            "UPDATE incoming_messages SET received_at = now() - interval '23 hours 59 minutes' WHERE id = ?",
            freshId,
        )
        execute(
            "INSERT INTO messages " +
                "(id, date, from_id, group_id, peer_id, conversation_message_id, message_text, inbox_id) " +
                "VALUES (?, now(), ?, 1, ?, 1, 'kept', ?)",
            UUID.randomUUID(),
            CLEANUP_FROM_ID,
            CLEANUP_PEER_ID,
            staleId,
        )

        cleanupRunner.runScheduled()

        assertEquals(0, countRows("SELECT count(*) FROM incoming_messages WHERE id = ?", staleId))
        assertEquals(1, countRows("SELECT count(*) FROM incoming_messages WHERE id = ?", freshId))
        assertEquals(1, countRows("SELECT count(*) FROM messages WHERE inbox_id = ?", staleId))
        assertEquals("kept", stringValue("SELECT message_text FROM messages WHERE inbox_id = ?", staleId))
    }

    @Test
    fun `standby replica does not poll vk until the lease expires and then takes over from the stored ts`() {
        stubLongPollSession(ts = "1")
        stubPoll("""{"ts":"2","updates":[]}""")
        seedSavedTs(ts = "77")
        execute(
            "INSERT INTO long_poll_lease (id, owner, lease_until) " +
                "VALUES (1, 'replica-a', now() + interval '4 seconds')",
        )
        val runner = runner(enabled = true)
        try {
            runner.start(StartupEvent())

            await().atMost(LEASE_AWAIT_SECONDS, TimeUnit.SECONDS)
                .during(STANDBY_WINDOW_SECONDS, TimeUnit.SECONDS)
                .untilAsserted {
                    wireMock.verify(0, getRequestedFor(urlPathEqualTo(POLL_PATH)))
                }

            await().atMost(LEASE_AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted {
                    wireMock.verify(
                        getRequestedFor(urlPathEqualTo(POLL_PATH)).withQueryParam("ts", equalTo("77")),
                    )
                }

            val owner = stringValue("SELECT owner FROM long_poll_lease")
            assertNotEquals("replica-a", owner)
            assertTrue(leaseSecondsAhead() in LEASE_WINDOW_SECONDS, "lease must be renewed for the new leader")
        } finally {
            runner.stop(ShutdownEvent())
        }
    }

    @Test
    fun `shutdown releases the lease so another replica can acquire it`() {
        stubLongPollSession(ts = "1")
        stubPoll("""{"ts":"2","updates":[]}""")
        val runner = runner(enabled = true)
        runner.start(StartupEvent())
        await().atMost(LEASE_AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                wireMock.verify(getRequestedFor(urlPathEqualTo(POLL_PATH)))
                assertEquals(1, countRows("SELECT count(*) FROM long_poll_lease"))
            }

        runner.stop(ShutdownEvent())

        val acquired = acquireLongPollLeasePort.execute(
            FakePersistencePortProvider.createAcquireLongPollLeaseRequest(
                owner = "replica-b",
                ttl = Duration.ofSeconds(40),
            ),
        ).acquired
        assertTrue(acquired, "released lease must be acquirable by another replica")
        assertEquals("replica-b", stringValue("SELECT owner FROM long_poll_lease"))
    }

    @Test
    fun `answer random id comes from the inbox id and repeated send keeps it`() {
        stubVkApi()
        listOf(42L to 42, 2_147_483_648L to 1).forEach { (inboxId, expectedRandomId) ->
            val request = FakeVkFacadeProvider.createVkSendMessageInputRequest(
                responseMessage = FakeModelProvider.createResponseMessage(
                    messageText = FakeVoProvider.createMessageText(RANDOM_MESSAGE_TEXT),
                    responseTo = FakeVoProvider.createMessage(
                        peerId = FakeVoProvider.createPeerId(DUPLICATE_PEER_ID),
                        inboxMessageId = FakeVoProvider.createInboxMessageId(inboxId),
                    ),
                ),
            )

            vkSendMessageInputPort.execute(request)
            vkSendMessageInputPort.execute(request)

            assertEquals(expectedRandomId, randomIdOfInbox(inboxId))
            assertEquals(
                2,
                sentMessages().count { it.randomId == expectedRandomId },
                "both sends of the same answer must carry random_id=$expectedRandomId",
            )
        }
    }

    private fun runner(enabled: Boolean = false): LongPollRunner = LongPollRunner(
        getLongPollServerPort = getLongPollServerPort,
        checkLongPollPort = checkLongPollPort,
        receiveMessageInputPort = receiveMessageInputPort,
        findLongPollTsPort = findLongPollTsPort,
        saveLongPollTsPort = saveLongPollTsPort,
        acquireLongPollLeasePort = acquireLongPollLeasePort,
        releaseLongPollLeasePort = releaseLongPollLeasePort,
        messageMapper = messageMapper,
        groupId = GROUP_ID,
        enabled = enabled,
        leaseTtl = LEASE_TTL,
        standbyPause = STANDBY_PAUSE,
    )

    private fun failIncomingMessageRegistration() {
        execute(
            """
            CREATE OR REPLACE FUNCTION e2e_inbox_insert_failure() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN
                RAISE EXCEPTION 'e2e inbox register failure';
            END;
            ${'$'}${'$'}
            """.trimIndent(),
        )
        execute(
            "CREATE TRIGGER e2e_inbox_insert_failure BEFORE INSERT OR UPDATE ON incoming_messages " +
                "FOR EACH ROW EXECUTE FUNCTION e2e_inbox_insert_failure()",
        )
    }

    private fun allowIncomingMessageRegistration() {
        execute("DROP TRIGGER IF EXISTS e2e_inbox_insert_failure ON incoming_messages")
        execute("DROP FUNCTION IF EXISTS e2e_inbox_insert_failure()")
    }

    private fun stubVkApi() {
        wireMock.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(jsonResponse(CHAT_COMPLETION_REPLY)))
        wireMock.stubFor(post(urlPathEqualTo(USERS_GET_PATH)).willReturn(jsonResponse(USER_PROFILE_REPLY)))
        wireMock.stubFor(post(urlPathEqualTo(GROUPS_GET_PATH)).willReturn(jsonResponse(GROUP_PROFILE_REPLY)))
        wireMock.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(jsonResponse("""{"response":12345}""")))
    }

    private fun stubLongPollSession(ts: String) {
        val server = "${wireMock.baseUrl()}$POLL_PATH"
        wireMock.stubFor(
            post(urlPathEqualTo(GROUPS_GET_LONG_POLL_SERVER_PATH))
                .willReturn(jsonResponse("""{"response":{"key":"test-key","server":"$server","ts":"$ts"}}""")),
        )
    }

    private fun stubPoll(body: String) {
        wireMock.stubFor(get(urlPathEqualTo(POLL_PATH)).willReturn(jsonResponse(body)))
    }

    private fun jsonResponse(body: String): ResponseDefinitionBuilder = aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(body)

    private fun messageNewBatch(ts: String, conversationMessageId: Long, peerId: Long): String {
        val update = messageNewUpdate(conversationMessageId, peerId)
        return """{"ts":"$ts","updates":[$update]}"""
    }

    private fun messageNewUpdate(conversationMessageId: Long, peerId: Long): String = """
        {
            "group_id": $GROUP_ID,
            "type": "message_new",
            "event_id": "inbox-e2e-$conversationMessageId",
            "v": "5.199",
            "object": {
                "client_info": {
                    "button_actions": ["text", "vkpay"],
                    "keyboard": true,
                    "lang_id": 0
                },
                "message": {
                    "date": 1756381457,
                    "from_id": $DEFAULT_FROM_ID,
                    "id": 0,
                    "version": 10000390,
                    "out": 0,
                    "text": "$MESSAGE_TEXT",
                    "peer_id": $peerId,
                    "random_id": 0,
                    "conversation_message_id": $conversationMessageId
                }
            }
        }
    """.trimIndent()

    private fun sentMessages(): List<SentMessage> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(SEND_PATH)))
        .map { request ->
            SentMessage(
                peerId = request.formParameter("peer_id").firstValue().toLong(),
                randomId = request.formParameter("random_id").firstValue().toInt(),
            )
        }

    private fun randomIdOfInbox(inboxId: Long): Int = (((inboxId - 1) % 2_147_483_647L) + 1).toInt()

    private fun registerIncoming(peerId: Long, conversationMessageId: Long): Long {
        val response = registerIncomingMessagePort.execute(
            FakePersistencePortProvider.createRegisterIncomingMessageRequest(
                peerId = peerId,
                conversationMessageId = conversationMessageId,
                payload = INBOX_PAYLOAD,
            ),
        )
        return response.inboxMessageId.value
    }

    private fun singleInboxId(peerId: Long): Long {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_MILLIS, TimeUnit.MILLISECONDS)
            .untilAsserted {
                assertTrue(incomingCount(peerId = peerId) >= 1, "inbox row for peerId=$peerId is missing")
            }
        return queryValue("SELECT id FROM incoming_messages WHERE peer_id = ?", peerId) { rows -> rows.getLong("id") }
            ?: error("inbox row for peerId=$peerId is missing")
    }

    private fun incomingCount(peerId: Long): Int {
        val count = countRows("SELECT count(*) FROM incoming_messages WHERE peer_id = ?", peerId)
        return count
    }

    private fun incomingPayload(peerId: Long): JsonNode {
        val payload = queryValue(
            "SELECT payload::text AS payload FROM incoming_messages WHERE peer_id = ?",
            peerId,
        ) { rows -> objectMapper.readTree(rows.getString("payload")) }
        return payload ?: error("inbox row for peerId=$peerId is missing")
    }

    private fun messageInboxId(peerId: Long): Long = queryValue(
        "SELECT inbox_id FROM messages WHERE peer_id = ?",
        peerId,
    ) { rows -> rows.getLong("inbox_id") } ?: error("messages row for peerId=$peerId is missing")

    private fun messageText(peerId: Long): String = queryValue(
        "SELECT message_text FROM messages WHERE peer_id = ?",
        peerId,
    ) { rows -> rows.getString("message_text") } ?: error("messages row for peerId=$peerId is missing")

    private fun messageCount(peerId: Long): Int = countRows("SELECT count(*) FROM messages WHERE peer_id = ?", peerId)

    private fun totalsRow(userId: Long): Pair<Long, Long> = queryValue(
        "SELECT message_count, total_length FROM user_statistics WHERE user_id = ?",
        userId,
    ) { rows -> rows.getLong("message_count") to rows.getLong("total_length") }
        ?: error("missing user_statistics row for user_id=$userId")

    private fun chatMessageCount(userId: Long): Long = queryValue(
        "SELECT message_count FROM user_chat_statistics WHERE user_id = ? AND peer_id = ?",
        userId,
        DUPLICATE_PEER_ID,
    ) { rows -> rows.getLong("message_count") } ?: error("missing user_chat_statistics row for user_id=$userId")

    private fun seedSavedTs(ts: String) {
        execute(
            "INSERT INTO long_poll_position (id, ts) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET ts = ?",
            ts,
            ts,
        )
    }

    private fun savedTs(): String? = queryValue("SELECT ts FROM long_poll_position") { rows -> rows.getString("ts") }

    private fun leaseSecondsAhead(): Double = queryValue(
        "SELECT EXTRACT(EPOCH FROM (lease_until - now())) FROM long_poll_lease WHERE id = 1",
    ) { rows -> rows.getDouble(1) } ?: error("lease row is missing")

    private fun countRows(sql: String, vararg parameters: Any?): Int {
        val count = queryValue(sql, *parameters) { rows -> rows.getInt(1) }
        return count ?: 0
    }

    private fun stringValue(sql: String, vararg parameters: Any?): String? {
        val value = queryValue(sql, *parameters) { rows -> rows.getString(1) }
        return value
    }

    private fun <T> queryValue(sql: String, vararg parameters: Any?, read: (ResultSet) -> T): T? {
        val reader: (ResultSet) -> T? = { rows -> if (rows.next()) read(rows) else null }
        val result = withStatement(sql, *parameters) { statement ->
            statement.executeQuery().use { rows -> reader(rows) }
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

    private data class SentMessage(val peerId: Long, val randomId: Int)

    private companion object {
        const val GROUP_ID = 232142875L
        const val DEFAULT_FROM_ID = 173308299L
        const val CLEANUP_FROM_ID = 173308300L
        const val DUPLICATE_PEER_ID = 2_000_000_042L
        const val RETRY_PEER_ID = 2_000_000_043L
        const val CLEANUP_PEER_ID = 2_000_000_044L
        const val DUPLICATE_CONVERSATION_MESSAGE_ID = 2670L
        const val RETRY_CONVERSATION_MESSAGE_ID = 2671L
        const val MESSAGE_TEXT = "@simarel inbox e2e duplicate"
        const val RANDOM_MESSAGE_TEXT = "inbox e2e random"
        const val INBOX_PAYLOAD = """{"type":"message_new","object":{"message":{"text":"cleanup"}}}"""
        const val POLL_PATH = "/whp/1"
        const val SEND_PATH = "/method/messages.send"
        const val USERS_GET_PATH = "/method/users.get"
        const val GROUPS_GET_PATH = "/method/groups.getById"
        const val GROUPS_GET_LONG_POLL_SERVER_PATH = "/method/groups.getLongPollServer"
        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
        const val AWAIT_SECONDS = 120L
        const val LEASE_AWAIT_SECONDS = 30L
        const val POLL_MILLIS = 200L
        const val ABSENCE_WINDOW_SECONDS = 2L
        const val STANDBY_WINDOW_SECONDS = 1L
        val LEASE_TTL: Duration = Duration.ofSeconds(40)
        val STANDBY_PAUSE: Duration = Duration.ofMillis(200)
        val LEASE_WINDOW_SECONDS: ClosedFloatingPointRange<Double> = 30.0..42.0
        const val CHAT_COMPLETION_REPLY = """
            {
                "id": "chatcmpl-test",
                "object": "chat.completion",
                "created": 1735689600,
                "model": "test-model",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Hello! This is a response from AI."
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 50,
                    "completion_tokens": 20,
                    "total_tokens": 70
                }
            }
        """
        const val USER_PROFILE_REPLY = """
            {
                "response": [
                    {
                        "id": 173308299,
                        "first_name": "Test",
                        "last_name": "User",
                        "screen_name": "testuser",
                        "bdate": "01.01.1990",
                        "city": {"id": 1, "title": "Moscow"}
                    }
                ]
            }
        """
        const val GROUP_PROFILE_REPLY = """
            {
                "response": [
                    {
                        "id": 1,
                        "name": "Test Group",
                        "screen_name": "testgroup"
                    }
                ]
            }
        """
    }
}
