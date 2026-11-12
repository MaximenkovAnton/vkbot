package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.persistence.adapter.input.event.MessageNewEventProcessor
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class KnowledgeKillSwitchTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "vk.longpoll.enabled" to "false",
        "knowledge.enabled" to "false",
        "knowledge.batch-size" to "1",
        "knowledge.batch-interval" to "1s",
        "statistics.timezone" to "Europe/Minsk",
    )
}

@QuarkusTest
@TestProfile(KnowledgeKillSwitchTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class UserKnowledgeKillSwitchIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var messageNewEventProcessor: MessageNewEventProcessor

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    private lateinit var fixtures: UserKnowledgeReadFixtures

    @BeforeEach
    fun setUp() {
        fixtures = UserKnowledgeReadFixtures(dataSource)
        WireMockTestResource.resetToDefaultStubs()
        clearData()
        stubMessagesSend()
        stubUsersGet()
        stubDecision()
        stubChatReply(ANSWER_RUBRIC, ANSWER_REPLY)
    }

    @AfterEach
    fun tearDown() {
        clearData()
    }

    @Test
    fun `kill switch disables knowledge extraction and keeps the answer prompt pre-change`() {
        deliverMessage()

        awaitSentMessage(ANSWER_REPLY)
        assertEquals(0, chatCallCount(EXTRACTION_RUBRIC), "extraction must not run when knowledge is disabled")
        assertEquals(0, chatCallCount(SUMMARY_RUBRIC), "profile summary must not run when knowledge is disabled")
        val counts = fixtures.counts(USER_ID, PEER_ID)
        assertEquals(0, counts["user_facts"], counts.toString())
        assertEquals(0, counts["knowledge_extraction_cursors"], counts.toString())
        assertEquals(ONE_MESSAGE, messageCount())
        assertFalse(promptFor(ANSWER_RUBRIC).contains(USER_KNOWLEDGE_MARKER))
    }

    private fun deliverMessage() {
        val message = FakeVoProvider.createMessage(
            date = FakeVoProvider.createDate(OffsetDateTime.now()),
            fromId = FakeVoProvider.createFromId(USER_ID),
            groupId = FakeVoProvider.createGroupId(COMMUNITY_ID),
            peerId = FakeVoProvider.createPeerId(PEER_ID),
            conversationMessageId = FakeVoProvider.createConversationMessageId(MESSAGE_ID),
            messageText = FakeVoProvider.createMessageText(QUESTION_TEXT),
        )
        messageNewEventProcessor.process(objectMapper.writeValueAsString(message))
    }

    private fun awaitSentMessage(text: String) {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertTrue(sentTexts().contains(text), "no messages.send with text '$text', sent: ${sentTexts()}")
        }
    }

    private fun sentTexts(): List<String> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(MESSAGES_SEND_PATH)))
        .map { request -> request.formParameter(MESSAGE_PARAM).firstValue() }

    private fun chatCallCount(rubric: String): Int = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).withRequestBody(containing(rubric)))
        .size

    private fun promptFor(rubric: String): String {
        val request = wireMock
            .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).withRequestBody(containing(rubric)))
            .last()
        return objectMapper.readTree(request.bodyAsString)["messages"].last()["content"].asText()
    }

    private fun messageCount(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(MESSAGES_COUNT_SQL).use { statement ->
            statement.setLong(1, PEER_ID)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    private fun stubChatReply(rubric: String, content: String) {
        wireMock.stubFor(
            post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .withRequestBody(containing(rubric))
                .willReturn(jsonResponse(chatReplyBody(content))),
        )
    }

    private fun stubMessagesSend() {
        wireMock.stubFor(
            post(urlPathEqualTo(MESSAGES_SEND_PATH)).willReturn(jsonResponse(MESSAGES_SEND_REPLY)),
        )
    }

    private fun stubUsersGet() {
        wireMock.stubFor(post(urlPathEqualTo(USERS_GET_PATH)).willReturn(jsonResponse(USERS_GET_REPLY)))
    }

    private fun stubDecision() {
        wireMock.stubFor(post(urlPathEqualTo(SYSTEM_ONE_PATH)).willReturn(jsonResponse(DECISION_REPLY)))
    }

    private fun chatReplyBody(content: String): String = objectMapper.writeValueAsString(
        mapOf(
            "id" to "chatcmpl-knowledge-kill-switch",
            "object" to "chat.completion",
            "created" to 1_735_689_600,
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

    private fun jsonResponse(body: String): ResponseDefinitionBuilder = aResponse()
        .withStatus(OK_STATUS)
        .withHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
        .withBody(body)

    private fun clearData() {
        fixtures.deleteKnowledge(USER_ID, PEER_ID)
        execute(DELETE_MESSAGES_SQL)
        execute(DELETE_STATISTICS_SQL)
        execute(DELETE_CHAT_STATISTICS_SQL)
        execute(DELETE_HOUR_STATISTICS_SQL)
        execute(DELETE_PROFILE_SQL)
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.executeUpdate(sql) }
        }
    }

    private companion object {
        const val USER_ID = 7_910_101L
        const val PEER_ID = 2_910_100_001L
        const val COMMUNITY_ID = 232_142_875L
        const val MESSAGE_ID = 911_001L
        const val ONE_MESSAGE = 1

        const val QUESTION_TEXT = "что я люблю, @simarel?"
        const val ANSWER_REPLY = "Ответ без знаний из чата"
        const val USER_KNOWLEDGE_MARKER = "[USER_KNOWLEDGE]"

        const val AWAIT_SECONDS = 120L
        const val POLL_SECONDS = 1L
        const val OK_STATUS = 200

        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
        const val MESSAGES_SEND_PATH = "/method/messages.send"
        const val USERS_GET_PATH = "/method/users.get"
        const val SYSTEM_ONE_PATH = "/v1/systemone"
        const val MESSAGE_PARAM = "message"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val JSON_CONTENT_TYPE = "application/json"

        const val EXTRACTION_RUBRIC = "Извлеки факты о людях из сообщений ниже."
        const val SUMMARY_RUBRIC = "Ты — составитель сводки профиля человека"
        const val ANSWER_RUBRIC = "You are a helpful ai chatbot in a chat."

        const val MESSAGES_SEND_REPLY = """{"response":12345}"""
        const val MESSAGES_COUNT_SQL = "select count(*) from messages where peer_id = ?"
        const val DELETE_MESSAGES_SQL = "delete from messages where peer_id = 2910100001"
        const val DELETE_STATISTICS_SQL = "delete from user_statistics where user_id = 7910101"
        const val DELETE_CHAT_STATISTICS_SQL = "delete from user_chat_statistics where user_id = 7910101"
        const val DELETE_HOUR_STATISTICS_SQL = "delete from user_hour_statistics where user_id = 7910101"
        const val DELETE_PROFILE_SQL = "delete from vk_user_profiles where id = 7910101"

        val USERS_GET_REPLY = """
            {
                "response": [
                    {
                        "id": $USER_ID,
                        "first_name": "Иван",
                        "last_name": "Петров",
                        "screen_name": "ivan_kill_switch",
                        "city": {"id": 1, "title": "Минск"}
                    }
                ]
            }
        """.trimIndent()

        val DECISION_REPLY = """
            {
              "model": "test-decision",
              "answers": {
                "spam": {"type": "noul", "noul": 0.1},
                "jailbreak": {"type": "noul", "noul": 0.05},
                "class": {
                  "type": "choice",
                  "choice": "ham",
                  "probabilities": {"spam": 0.1, "command": 0.1, "ham": 0.8},
                  "confidence": 0.8
                },
                "intent": {
                  "type": "choice",
                  "choice": "general",
                  "probabilities": {"help": 0.05, "news": 0.05, "general": 0.9},
                  "confidence": 0.9
                }
              },
              "usage": {"input_tokens": 199, "output_tokens": 0}
            }
        """.trimIndent()
    }
}
