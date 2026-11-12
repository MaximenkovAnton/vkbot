package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.ai.domain.exception.ChatKnowledgeSummaryException
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatSummaryMessagesPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

private const val PEER_READ = 2_400_000_001L
private const val PEER_ARCHIVE_START = 2_400_000_002L
private const val PEER_SUMMARY = 2_400_000_003L
private const val PEER_END_TO_END = 2_400_000_004L
private const val PEER_EMPTY_WINDOW = 2_400_000_005L
private const val PEER_FAILED_MODEL = 2_400_000_006L
private const val PEER_LIMIT = 2_400_000_007L
private const val PEER_NO_SUMMARY = 2_400_000_008L
private const val AUTHOR_ID = 7_600_001L
private const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
private const val READ_LIMIT = 10
private const val SMALL_LIMIT = 1
private const val ZERO_LIMIT = 0
private const val NEGATIVE_LIMIT = -1
private const val PREVIOUS_SUMMARY = "прежняя сводка"
private const val SAVED_SUMMARY = "Темы: программирование и мемы"
private const val MODEL_REPLY = "Темы чата: программирование, мемы и шутки."
private const val FIRST_READ_ID = 69L
private const val SECOND_READ_ID = 70L
private const val THIRD_READ_ID = 71L
private const val FOURTH_READ_ID = 72L
private const val FORMAT_MARKER = "Не пересказывай сообщения по одному"

@QuarkusTest
@TestProfile(LongPollPositionTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class ChatKnowledgeSummaryIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var findChatSummaryMessagesPort: FindChatSummaryMessagesPort

    @Inject
    lateinit var findChatKnowledgeSummaryPort: FindChatKnowledgeSummaryPort

    @Inject
    lateinit var saveChatKnowledgeSummaryPort: SaveChatKnowledgeSummaryPort

    @Inject
    lateinit var processChatKnowledgeSummaryInputPort: ProcessChatKnowledgeSummaryInputPort

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    @BeforeEach
    fun setUp() {
        WireMockTestResource.resetToDefaultStubs()
        clearPeerData()
    }

    @AfterEach
    fun tearDown() {
        clearPeerData()
        WireMockTestResource.resetToDefaultStubs()
    }

    @Test
    fun `readsMessagesAfterLastSummarizedConversationMessageId`() {
        insertMessage(PEER_READ, FIRST_READ_ID, "первое")
        insertMessage(PEER_READ, SECOND_READ_ID, "второе")
        insertMessage(PEER_READ, THIRD_READ_ID, "третье")
        insertMessage(PEER_READ, FOURTH_READ_ID, "четвёртое")
        saveChatSummary(PEER_READ, "темы и шутки", FIRST_READ_ID, SECOND_READ_ID, 2)

        val messages = windowIds(PEER_READ, READ_LIMIT)

        assertEquals(listOf(THIRD_READ_ID, FOURTH_READ_ID), messages)
        assertEquals(listOf(THIRD_READ_ID), windowIds(PEER_READ, SMALL_LIMIT))
    }

    @Test
    fun `readsFromArchiveStartWithoutSummaryRow`() {
        insertMessage(PEER_ARCHIVE_START, 1, "один")
        insertMessage(PEER_ARCHIVE_START, 2, "два")
        insertMessage(PEER_ARCHIVE_START, 3, "три")

        assertEquals(listOf(1L, 2L, 3L), windowIds(PEER_ARCHIVE_START, READ_LIMIT))
    }

    @Test
    fun `returnsNoMessagesWhenLimitIsNotPositive`() {
        insertMessage(PEER_LIMIT, 1, "привет")

        assertEquals(emptyList<Long>(), windowIds(PEER_LIMIT, ZERO_LIMIT))
        assertEquals(emptyList<Long>(), windowIds(PEER_LIMIT, NEGATIVE_LIMIT))
    }

    @Test
    fun `returnsNullWithoutSummaryRow`() {
        val knowledge = findChatKnowledgeSummaryPort.execute(
            FakePersistencePortProvider.createFindChatKnowledgeSummaryRequest(peerId = PEER_NO_SUMMARY),
        ).knowledge

        assertNull(knowledge)
    }

    @Test
    fun `readsSavedChatKnowledgeSummary`() {
        val startedAt = OffsetDateTime.now(ZoneOffset.UTC)
        saveChatSummary(PEER_SUMMARY, SAVED_SUMMARY, 1, 70, 70)

        val knowledge = findChatKnowledgeSummaryPort.execute(
            FakePersistencePortProvider.createFindChatKnowledgeSummaryRequest(peerId = PEER_SUMMARY),
        ).knowledge

        assertNotNull(knowledge)
        assertEquals(PEER_SUMMARY, knowledge?.peerId)
        assertEquals(SAVED_SUMMARY, knowledge?.summary)
        assertFalse(knowledge!!.summarizedAt.isBefore(startedAt))
    }

    @Test
    fun `summarizesWindowThroughModelAndSavesRange`() {
        insertMessage(PEER_END_TO_END, 1, "привет")
        insertMessage(PEER_END_TO_END, 2, "мем про кота")
        insertMessage(PEER_END_TO_END, 3, "шутка")
        stubChatReply(MODEL_REPLY)

        processChatKnowledgeSummaryInputPort.execute(
            FakeKnowledgeProvider.createProcessChatKnowledgeSummaryInputPortRequest(
                payload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(peerId = PEER_END_TO_END),
            ),
        )

        assertEquals(1, chatRequests().size)
        val prompt = chatRequests().single()
        assertTrue(prompt.contains(FORMAT_MARKER), prompt)
        assertTrue(prompt.contains("привет"), prompt)
        assertTrue(prompt.contains("мем про кота"), prompt)
        assertTrue(prompt.contains("шутка"), prompt)
        val row = chatSummaryRow(PEER_END_TO_END)
        assertEquals(MODEL_REPLY, row["summary_text"])
        assertEquals(1L, row["first_conversation_message_id"])
        assertEquals(3L, row["last_conversation_message_id"])
        assertEquals(3, row["messages_covered"])
    }

    @Test
    fun `skipsModelWhenWindowIsEmpty`() {
        saveChatSummary(PEER_EMPTY_WINDOW, PREVIOUS_SUMMARY, 1, 70, 70)

        processChatKnowledgeSummaryInputPort.execute(
            FakeKnowledgeProvider.createProcessChatKnowledgeSummaryInputPortRequest(
                payload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(peerId = PEER_EMPTY_WINDOW),
            ),
        )

        assertEquals(0, chatRequests().size)
        assertEquals(PREVIOUS_SUMMARY, chatSummaryRow(PEER_EMPTY_WINDOW)["summary_text"])
    }

    @Test
    fun `keepsPreviousSummaryWhenModelReturnsBlank`() {
        insertStaleSummaryRow(PEER_FAILED_MODEL, PREVIOUS_SUMMARY, 1, 70, 70)
        insertMessage(PEER_FAILED_MODEL, 71, "привет")
        insertMessage(PEER_FAILED_MODEL, 72, "шутка")
        stubChatReply(" ")

        assertThrows<ChatKnowledgeSummaryException> {
            processChatKnowledgeSummaryInputPort.execute(
                FakeKnowledgeProvider.createProcessChatKnowledgeSummaryInputPortRequest(
                    payload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(peerId = PEER_FAILED_MODEL),
                ),
            )
        }

        assertEquals(3, chatRequests().size)
        val row = chatSummaryRow(PEER_FAILED_MODEL)
        assertEquals(PREVIOUS_SUMMARY, row["summary_text"])
        assertEquals(70L, row["last_conversation_message_id"])
    }

    private fun windowIds(peerId: Long, limit: Int): List<Long> = findChatSummaryMessagesPort.execute(
        FakePersistencePortProvider.createFindChatSummaryMessagesRequest(peerId = peerId, limit = limit),
    ).messages.map { it.conversationMessageId }

    private fun saveChatSummary(
        peerId: Long,
        summaryText: String,
        firstConversationMessageId: Long,
        lastConversationMessageId: Long,
        messagesCovered: Int,
    ) {
        saveChatKnowledgeSummaryPort.execute(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = peerId,
                summaryText = summaryText,
                firstConversationMessageId = firstConversationMessageId,
                lastConversationMessageId = lastConversationMessageId,
                messagesCovered = messagesCovered,
            ),
        )
    }

    private fun insertMessage(peerId: Long, conversationMessageId: Long, text: String) {
        execute(
            "insert into messages (id, date, from_id, group_id, peer_id, conversation_message_id, message_text) " +
                "values (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            OffsetDateTime.now(ZoneOffset.UTC),
            AUTHOR_ID,
            0L,
            peerId,
            conversationMessageId,
            text,
        )
    }

    private fun insertStaleSummaryRow(
        peerId: Long,
        summaryText: String,
        firstConversationMessageId: Long,
        lastConversationMessageId: Long,
        messagesCovered: Int,
    ) {
        execute(
            "insert into chat_knowledge_summary " +
                "(peer_id, summary_text, messages_covered, first_conversation_message_id, " +
                "last_conversation_message_id, summarized_at) " +
                "values (?, ?, ?, ?, ?, now() - interval '8 days')",
            peerId,
            summaryText,
            messagesCovered,
            firstConversationMessageId,
            lastConversationMessageId,
        )
    }

    private fun chatSummaryRow(peerId: Long): Map<String, Any?> = rows(
        "select * from chat_knowledge_summary where peer_id = ?",
        peerId,
    ).single()

    private fun chatRequests(): List<String> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)))
        .map { it.bodyAsString }

    private fun stubChatReply(content: String) {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "id" to "chatcmpl-chat-knowledge-summary",
                "object" to "chat.completion",
                "created" to 1735689600,
                "model" to "test-model",
                "choices" to listOf(
                    mapOf(
                        "index" to 0,
                        "message" to mapOf("role" to "assistant", "content" to content),
                        "finish_reason" to "stop",
                    ),
                ),
                "usage" to mapOf("prompt_tokens" to 1, "completion_tokens" to 1, "total_tokens" to 2),
            ),
        )
        wireMock.stubFor(
            post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body),
            ),
        )
    }

    private fun clearPeerData() {
        listOf(
            PEER_READ,
            PEER_ARCHIVE_START,
            PEER_SUMMARY,
            PEER_END_TO_END,
            PEER_EMPTY_WINDOW,
            PEER_FAILED_MODEL,
            PEER_LIMIT,
            PEER_NO_SUMMARY,
        ).forEach { peerId ->
            execute("delete from chat_knowledge_summary where peer_id = ?", peerId)
            execute("delete from messages where peer_id = ?", peerId)
        }
    }

    private fun rows(sql: String, vararg params: Any?): List<Map<String, Any?>> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
                statement.executeQuery().use { resultSet -> return readRows(resultSet) }
            }
        }
    }

    private fun execute(sql: String, vararg params: Any?) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
                statement.executeUpdate()
            }
        }
    }

    private fun readRows(resultSet: ResultSet): List<Map<String, Any?>> {
        val metadata = resultSet.metaData
        val rows = mutableListOf<Map<String, Any?>>()
        while (resultSet.next()) {
            rows += (1..metadata.columnCount).associate { index ->
                val column = metadata.getColumnLabel(index)
                val value = when (metadata.getColumnType(index)) {
                    Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                        resultSet.getObject(index, OffsetDateTime::class.java)

                    else -> resultSet.getObject(index)
                }
                column to value
            }
        }
        return rows
    }
}
