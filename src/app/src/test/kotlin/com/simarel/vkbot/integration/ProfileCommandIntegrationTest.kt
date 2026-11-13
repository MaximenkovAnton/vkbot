package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPort
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.persistence.adapter.input.event.MessageNewEventProcessor
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
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
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private const val PROFILE_TEST_SYSTEM_PROMPT = "PROFILE_TEST_SYSTEM_PROMPT"

class ProfileCommandTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "vk.longpoll.enabled" to "false",
        "statistics.timezone" to "Europe/Minsk",
        "aiProfileSystemprompt" to PROFILE_TEST_SYSTEM_PROMPT,
    )
}

@QuarkusTest
@TestProfile(ProfileCommandTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class ProfileCommandIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var messageNewEventProcessor: MessageNewEventProcessor

    @Inject
    lateinit var profileRepository: JooqVkUserProfileRepository

    @Inject
    lateinit var processProfileInputPort: ProcessProfileInputPort

    @Inject
    lateinit var saveChatKnowledgeSummaryPort: SaveChatKnowledgeSummaryPort

    private val knowledgeFixtures: UserKnowledgeReadFixtures by lazy { UserKnowledgeReadFixtures(dataSource) }

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    @BeforeEach
    fun setUp() {
        WireMockTestResource.resetToDefaultStubs()
        clearData()
        stubMessagesSend()
    }

    @AfterEach
    fun tearDown() {
        clearData()
    }

    @Test
    fun `answer is built from the stored dossier and writes nothing`() {
        seedDossier()
        stubChatReply(DOSSIER_REPLY)
        val profilesBefore = countRows(VK_USER_PROFILES_TABLE)
        val historyBefore = countRows(VK_USER_PROFILE_HISTORY_TABLE)
        val messagesBefore = countRows(MESSAGES_TABLE)

        askAbout(DOSSIER_PEER_ID, DOSSIER_USER_ID, DOSSIER_LABEL, DOSSIER_REQUEST_MESSAGE_ID)

        awaitSentMessage(DOSSIER_PEER_ID, DOSSIER_REPLY)
        val prompt = userPrompt()
        assertTrue(prompt.contains(PROFILE_HEADER), prompt)
        assertTrue(prompt.contains("Имя: $DOSSIER_NAME"), prompt)
        assertTrue(prompt.contains("Страна: $COUNTRY"), prompt)
        assertTrue(prompt.contains(STATISTICS_HEADER), prompt)
        assertTrue(prompt.contains("Всего сообщений: $SEEDED_MESSAGE_COUNT"), prompt)
        assertTrue(prompt.contains(HISTORY_HEADER), prompt)
        assertTrue(prompt.contains(HISTORY_LINE), prompt)
        assertTrue(prompt.contains(CHAT_HEADER), prompt)
        assertTrue(prompt.contains("Сообщений в этом чате: $SEEDED_MESSAGE_COUNT"), prompt)
        assertFalse(prompt.contains(PHONE), prompt)
        assertFalse(prompt.contains(HOME_PHONE), prompt)
        assertFalse(prompt.contains(KNOWLEDGE_HEADER), prompt)
        assertEquals(profilesBefore, countRows(VK_USER_PROFILES_TABLE))
        assertEquals(historyBefore, countRows(VK_USER_PROFILE_HISTORY_TABLE))
        assertEquals(messagesBefore, countRows(MESSAGES_TABLE))
    }

    @Test
    fun `readsChatKnowledgeIntoThePromptWithoutWriting`() {
        seedDossier()
        knowledgeFixtures.insertFact(
            userId = DOSSIER_USER_ID,
            value = "Гомель",
            lastConfirmedAt = SEEDED_DATE,
            category = UserFactCategory.PROFILE_ATTRIBUTE,
            factKey = "city",
        )
        stubChatReply(DOSSIER_REPLY)
        val profilesBefore = countRows(VK_USER_PROFILES_TABLE)
        val summariesBefore = countRows(USER_KNOWLEDGE_SUMMARY_TABLE)
        val messagesBefore = countRows(MESSAGES_TABLE)
        val knowledgeBefore = knowledgeFixtures.counts(DOSSIER_USER_ID, DOSSIER_PEER_ID)

        askAbout(DOSSIER_PEER_ID, DOSSIER_USER_ID, DOSSIER_LABEL, DOSSIER_REQUEST_MESSAGE_ID)

        awaitSentMessage(DOSSIER_PEER_ID, DOSSIER_REPLY)
        val prompt = userPrompt()
        assertTrue(prompt.contains("Город: Минск"), prompt)
        assertTrue(prompt.contains(KNOWLEDGE_HEADER), prompt)
        assertTrue(prompt.contains("- Гомель"), prompt)
        assertEquals(profilesBefore, countRows(VK_USER_PROFILES_TABLE))
        assertEquals(summariesBefore, countRows(USER_KNOWLEDGE_SUMMARY_TABLE))
        assertEquals(messagesBefore, countRows(MESSAGES_TABLE))
        assertEquals(knowledgeBefore, knowledgeFixtures.counts(DOSSIER_USER_ID, DOSSIER_PEER_ID))
        assertEquals("Минск", profileRepository.findByIds(listOf(DOSSIER_USER_ID)).single().city)
    }

    @Test
    fun `answer includes facts learned in another chat and the chat topics`() {
        seedDossier()
        knowledgeFixtures.insertFact(
            userId = DOSSIER_USER_ID,
            value = "Хоккей",
            lastConfirmedAt = SEEDED_DATE,
            factKey = "hockey",
            peerId = OTHER_CHAT_PEER_ID,
        )
        saveChatKnowledge(DOSSIER_PEER_ID, "В чате обсуждают дачу")
        stubChatReply(DOSSIER_REPLY)

        askAbout(DOSSIER_PEER_ID, DOSSIER_USER_ID, DOSSIER_LABEL, DOSSIER_REQUEST_MESSAGE_ID)

        awaitSentMessage(DOSSIER_PEER_ID, DOSSIER_REPLY)
        val prompt = userPrompt()
        assertTrue(prompt.contains(KNOWLEDGE_HEADER), prompt)
        assertTrue(prompt.contains("- Хоккей"), prompt)
        assertTrue(prompt.contains(TOPICS_HEADER), prompt)
        assertTrue(prompt.contains("В чате обсуждают дачу"), prompt)
        assertFalse(prompt.contains(REQUESTER_HEADER), prompt)
    }

    private fun saveChatKnowledge(peerId: Long, summary: String) {
        saveChatKnowledgeSummaryPort.execute(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = peerId,
                summaryText = summary,
            ),
        )
    }

    @Test
    fun `sends the configured system prompt with the dossier data`() {
        seedStoredProfile(PROMPT_USER_ID, "Олег", "Петров", "oleg_prompt", "Минск")
        stubChatReply(PROMPT_REPLY)

        val question = askAbout(PROMPT_PEER_ID, PROMPT_USER_ID, PROMPT_LABEL, PROMPT_REQUEST_MESSAGE_ID)

        awaitSentMessage(PROMPT_PEER_ID, PROMPT_REPLY)
        assertEquals(PROFILE_TEST_SYSTEM_PROMPT, systemPrompt())
        val userMessage = userPrompt()
        assertTrue(userMessage.contains("Вопрос: $question"), userMessage)
        assertTrue(userMessage.contains(PROFILE_HEADER), userMessage)
    }

    @Test
    fun `fetches a missing live profile and does not persist it`() {
        stubUsersGet()
        stubChatReply(LIVE_REPLY)
        val profilesBefore = countRows(VK_USER_PROFILES_TABLE)

        askAbout(LIVE_PEER_ID, LIVE_USER_ID, LIVE_LABEL, LIVE_REQUEST_MESSAGE_ID)

        awaitSentMessage(LIVE_PEER_ID, LIVE_REPLY)
        assertEquals(1, chatCompletionBodies().size)
        assertTrue(userPrompt().contains("Имя: $LIVE_NAME"), userPrompt())
        assertEquals(profilesBefore, countRows(VK_USER_PROFILES_TABLE))
        assertEquals(0, profileRows(LIVE_USER_ID))
        wireMock.verify(
            postRequestedFor(urlPathEqualTo(USERS_GET_PATH))
                .withRequestBody(containing(LIVE_USER_ID.toString())),
        )
    }

    private fun seedDossier() {
        seedStoredProfile(DOSSIER_USER_ID, "Паша", "Селезнев", DOSSIER_LABEL, "Минск")
        seedHistory(DOSSIER_USER_ID, HISTORY_CHANGES, HISTORY_CHANGED_AT)
        seedTrackedMessage(DOSSIER_USER_ID, DOSSIER_PEER_ID, "первое сообщение", SEEDED_DATE, FIRST_SEEDED_ID)
        seedTrackedMessage(
            DOSSIER_USER_ID,
            DOSSIER_PEER_ID,
            "второе сообщение",
            SEEDED_DATE.plusHours(1),
            SECOND_SEEDED_ID,
        )
        seedTrackedMessage(
            DOSSIER_USER_ID,
            DOSSIER_PEER_ID,
            "третье сообщение",
            SEEDED_DATE.plusHours(2),
            THIRD_SEEDED_ID,
        )
    }

    private fun seedStoredProfile(
        userId: Long,
        firstName: String,
        lastName: String,
        screenName: String,
        city: String,
    ) {
        profileRepository.persist(
            FakeModelProvider.createVkUserProfile(
                id = userId,
                firstName = firstName,
                lastName = lastName,
                screenName = screenName,
                birthDate = BIRTH_DATE,
                city = city,
                lastUpdated = OffsetDateTime.now(),
                details = mapOf(
                    "country" to COUNTRY,
                    "mobile_phone" to PHONE,
                    "home_phone" to HOME_PHONE,
                ),
            ),
        )
    }

    private fun seedHistory(profileId: Long, changes: String, changedAt: OffsetDateTime) {
        withConnection { connection ->
            connection.prepareStatement(INSERT_HISTORY_SQL).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setLong(2, profileId)
                statement.setString(3, changes)
                statement.setObject(4, changedAt)
                statement.executeUpdate()
            }
        }
    }

    private fun seedTrackedMessage(
        fromId: Long,
        peerId: Long,
        text: String,
        date: OffsetDateTime,
        messageId: Long,
    ) {
        val message = FakeVoProvider.createMessage(
            date = FakeVoProvider.createDate(date),
            fromId = FakeVoProvider.createFromId(fromId),
            groupId = FakeVoProvider.createGroupId(COMMUNITY_ID),
            peerId = FakeVoProvider.createPeerId(peerId),
            conversationMessageId = FakeVoProvider.createConversationMessageId(messageId),
            messageText = FakeVoProvider.createMessageText(text),
        )
        messageNewEventProcessor.process(objectMapper.writeValueAsString(message))
    }

    private fun askAbout(peerId: Long, targetId: Long, label: String, messageId: Long): String {
        val question = "что ты знаешь о [id$targetId|@$label]"
        processProfileInputPort.execute(
            FakeProfileProvider.createProcessProfileInputPortRequest(
                message = requestMessage(peerId, question, messageId),
            ),
        )
        return question
    }

    private fun requestMessage(peerId: Long, text: String, messageId: Long): Message = FakeVoProvider.createMessage(
        date = FakeVoProvider.createDate(REQUEST_DATE),
        fromId = FakeVoProvider.createFromId(ASKER_ID),
        groupId = FakeVoProvider.createGroupId(COMMUNITY_ID),
        peerId = FakeVoProvider.createPeerId(peerId),
        conversationMessageId = FakeVoProvider.createConversationMessageId(messageId),
        messageText = FakeVoProvider.createMessageText(text),
    )

    private fun awaitSentMessage(peerId: Long, expectedText: String) {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertTrue(
                sentMessages().any { it.peerId == peerId && it.text == expectedText },
                "no messages.send to $peerId with text $expectedText",
            )
        }
    }

    private fun sentMessages(): List<SentMessage> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(MESSAGES_SEND_PATH)))
        .map { request ->
            SentMessage(
                peerId = request.formParameter(PEER_ID_PARAM).firstValue().toLong(),
                text = request.formParameter(MESSAGE_PARAM).firstValue(),
            )
        }

    private fun chatCompletionBodies(): List<JsonNode> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)))
        .filter { request -> !request.bodyAsString.contains(EXTRACTION_RUBRIC) }
        .map { request -> objectMapper.readTree(request.bodyAsString) }

    private fun modelMessages(): List<ModelMessage> = chatCompletionBodies().flatMap { body ->
        val messages = body.path(MESSAGES_FIELD)
        (0 until messages.size()).map { index ->
            val message = messages.path(index)
            ModelMessage(
                role = message.path(ROLE_FIELD).asText(),
                content = message.path(CONTENT_FIELD).asText(),
            )
        }
    }

    private fun systemPrompt(): String = modelMessages().single { it.role == SYSTEM_ROLE }.content

    private fun userPrompt(): String = modelMessages().single { it.role == USER_ROLE }.content

    private fun stubMessagesSend() {
        wireMock.stubFor(
            post(urlPathEqualTo(MESSAGES_SEND_PATH)).willReturn(
                jsonResponse("""{"response":12345}"""),
            ),
        )
    }

    private fun stubUsersGet() {
        wireMock.stubFor(
            post(urlPathEqualTo(USERS_GET_PATH)).willReturn(jsonResponse(LIVE_PROFILE_REPLY)),
        )
    }

    private fun stubChatReply(content: String) {
        wireMock.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(jsonResponse(chatReplyBody(content))))
    }

    private fun chatReplyBody(content: String): String = objectMapper.writeValueAsString(
        mapOf(
            "id" to "chatcmpl-profile",
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

    private fun jsonResponse(body: String): ResponseDefinitionBuilder = aResponse()
        .withStatus(200)
        .withHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
        .withBody(body)

    private fun countRows(table: String): Int = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM $table").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    private fun profileRows(id: Long): Int = withConnection { connection ->
        connection.prepareStatement("SELECT count(*) FROM $VK_USER_PROFILES_TABLE WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    private fun clearData() {
        deleteRows(DELETE_STATISTICS_SQL, SEEDED_USER_IDS)
        deleteRows(DELETE_CHAT_STATISTICS_SQL, SEEDED_USER_IDS)
        deleteRows(DELETE_HOUR_STATISTICS_SQL, SEEDED_USER_IDS)
        deleteRows(DELETE_HISTORY_SQL, SEEDED_USER_IDS)
        deleteRows(DELETE_PROFILES_SQL, SEEDED_USER_IDS)
        deleteRows(DELETE_MESSAGES_SQL, SEEDED_PEER_IDS)
        SEEDED_USER_IDS.zip(SEEDED_PEER_IDS).forEach { (userId, peerId) ->
            knowledgeFixtures.deleteKnowledge(userId, peerId)
        }
    }

    private fun deleteRows(sql: String, ids: List<Long>) {
        withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                ids.forEachIndexed { index, id -> statement.setLong(index + 1, id) }
                statement.executeUpdate()
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    private data class SentMessage(val peerId: Long, val text: String)

    private data class ModelMessage(val role: String, val content: String)

    private companion object {
        const val COMMUNITY_ID = 232_142_875L
        const val ASKER_ID = 7_620_010L

        const val DOSSIER_USER_ID = 7_620_001L
        const val LIVE_USER_ID = 7_620_002L
        const val PROMPT_USER_ID = 7_620_003L

        const val DOSSIER_PEER_ID = 2_620_000_001L
        const val OTHER_CHAT_PEER_ID = 2_620_000_009L
        const val PROMPT_PEER_ID = 2_620_000_002L
        const val LIVE_PEER_ID = 2_620_000_003L

        val SEEDED_USER_IDS = listOf(DOSSIER_USER_ID, LIVE_USER_ID, PROMPT_USER_ID)
        val SEEDED_PEER_IDS = listOf(DOSSIER_PEER_ID, PROMPT_PEER_ID, LIVE_PEER_ID)

        const val DOSSIER_LABEL = "pasha"
        const val PROMPT_LABEL = "oleg"
        const val LIVE_LABEL = "maxim"

        const val DOSSIER_NAME = "Паша Селезнев"
        const val LIVE_NAME = "Максим Иванов"
        const val COUNTRY = "Беларусь"
        const val BIRTH_DATE = "01.01.1990"
        const val PHONE = "+375291234567"
        const val HOME_PHONE = "+375171234567"

        const val PROFILE_HEADER = "Профиль:"
        const val KNOWLEDGE_HEADER = "Из общения:"
        const val TOPICS_HEADER = "Темы чата:"
        const val REQUESTER_HEADER = "Кто запрашивает:"
        const val STATISTICS_HEADER = "Статистика:"
        const val HISTORY_HEADER = "История изменений:"
        const val CHAT_HEADER = "В этом чате:"

        const val HISTORY_CHANGES = """{"city": {"old": "Москва", "new": "Минск"}}"""
        const val HISTORY_LINE = "Город: «Москва» → «Минск»"

        const val SEEDED_MESSAGE_COUNT = 3L
        const val FIRST_SEEDED_ID = 1001L
        const val SECOND_SEEDED_ID = 1002L
        const val THIRD_SEEDED_ID = 1003L

        const val DOSSIER_REQUEST_MESSAGE_ID = 2001L
        const val PROMPT_REQUEST_MESSAGE_ID = 2002L
        const val LIVE_REQUEST_MESSAGE_ID = 2003L

        const val DOSSIER_REPLY = "Паша Селезнев — участник чата."
        const val PROMPT_REPLY = "Олег Петров — участник чата."
        const val LIVE_REPLY = "Максим Иванов — участник чата."

        const val MESSAGES_FIELD = "messages"
        const val ROLE_FIELD = "role"
        const val CONTENT_FIELD = "content"
        const val SYSTEM_ROLE = "system"
        const val USER_ROLE = "user"
        const val PEER_ID_PARAM = "peer_id"
        const val MESSAGE_PARAM = "message"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val JSON_CONTENT_TYPE = "application/json"

        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
        const val MESSAGES_SEND_PATH = "/method/messages.send"
        const val EXTRACTION_RUBRIC = "Извлеки факты о людях"
        const val USERS_GET_PATH = "/method/users.get"

        const val VK_USER_PROFILES_TABLE = "vk_user_profiles"
        const val VK_USER_PROFILE_HISTORY_TABLE = "vk_user_profile_history"
        const val USER_KNOWLEDGE_SUMMARY_TABLE = "user_knowledge_summary"
        const val MESSAGES_TABLE = "messages"

        const val AWAIT_SECONDS = 120L
        const val POLL_SECONDS = 1L

        val SEEDED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-11-01T09:00:00Z")
        val HISTORY_CHANGED_AT: OffsetDateTime = OffsetDateTime.parse("2026-11-02T10:00:00Z")
        val REQUEST_DATE: OffsetDateTime = OffsetDateTime.parse("2026-11-10T12:00:00Z")

        val LIVE_PROFILE_REPLY = """
            {
                "response": [
                    {
                        "id": $LIVE_USER_ID,
                        "first_name": "Максим",
                        "last_name": "Иванов",
                        "screen_name": "maxim",
                        "bdate": "02.02.1992",
                        "city": {"id": 2, "title": "Гомель"}
                    }
                ]
            }
        """.trimIndent()

        const val INSERT_HISTORY_SQL =
            "INSERT INTO vk_user_profile_history (id, profile_id, changes, changed_at) VALUES (?, ?, ?::jsonb, ?)"

        const val DELETE_STATISTICS_SQL = "DELETE FROM user_statistics WHERE user_id IN (?, ?, ?)"
        const val DELETE_CHAT_STATISTICS_SQL = "DELETE FROM user_chat_statistics WHERE user_id IN (?, ?, ?)"
        const val DELETE_HOUR_STATISTICS_SQL = "DELETE FROM user_hour_statistics WHERE user_id IN (?, ?, ?)"
        const val DELETE_HISTORY_SQL = "DELETE FROM vk_user_profile_history WHERE profile_id IN (?, ?, ?)"
        const val DELETE_PROFILES_SQL = "DELETE FROM vk_user_profiles WHERE id IN (?, ?, ?)"
        const val DELETE_MESSAGES_SQL = "DELETE FROM messages WHERE peer_id IN (?, ?, ?)"
    }
}
