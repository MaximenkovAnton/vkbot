package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPort
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPort
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPort
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.ai.testfixtures.FakeNewsDigestProvider
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.persistence.adapter.input.event.MessageNewEventProcessor
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

class KnowledgeE2eTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "vk.longpoll.enabled" to "false",
        "knowledge.batch-size" to "1",
        "knowledge.batch-interval" to "1s",
        "statistics.timezone" to "Europe/Minsk",
    )
}

@QuarkusTest
@TestProfile(KnowledgeE2eTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class UserKnowledgeIntegrationTest {

    private val testId: Long = TEST_SEQUENCE.incrementAndGet()
    private val userId: Long = USER_ID_BASE + testId
    private val peerId: Long = PEER_ID_BASE + testId

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var messageNewEventProcessor: MessageNewEventProcessor

    @Inject
    lateinit var userProfiles: JooqVkUserProfileRepository

    @Inject
    lateinit var processProfileInputPort: ProcessProfileInputPort

    @Inject
    lateinit var processUserKnowledgeInputPort: ProcessUserKnowledgeInputPort

    @Inject
    lateinit var processNewsDigestInputPort: ProcessNewsDigestInputPort

    @Inject
    lateinit var runDailyGreetings: RunDailyGreetingsInputPort

    @Inject
    lateinit var findUserKnowledgePort: FindUserKnowledgePort

    @Inject
    lateinit var saveUserFactsPort: SaveUserFactsPort

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    private lateinit var fixtures: UserKnowledgeReadFixtures

    @BeforeEach
    fun setUp() {
        fixtures = UserKnowledgeReadFixtures(dataSource)
        WireMockTestResource.resetToDefaultStubs()
        clearKnowledgeData()
        stubMessagesSend()
        stubUsersGet()
        stubDecision()
        stubChatReply(USER_SUMMARY_RUBRIC, USER_SUMMARY_TEXT)
        stubChatReply(CHAT_SUMMARY_RUBRIC, CHAT_SUMMARY_TEXT)
        stubChatReply(ANSWER_RUBRIC, ANSWER_REPLY)
        stubChatReply(PROFILE_RUBRIC, PROFILE_REPLY)
        stubChatReply(NEWS_RUBRIC, NEWS_REPLY)
        stubChatReply(GREETING_RUBRIC, GREETING_TEXT)
    }

    @AfterEach
    fun tearDown() {
        clearKnowledgeData()
    }

    @Test
    fun `message fact is stored with evidence and produces both chat summaries`() {
        storeProfile()
        stubExtraction(factDraft(UserFactCategory.INTEREST, FACT_KEY, INTEREST_VALUE, FACT_MESSAGE_ID))

        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)

        val fact = awaitFacts().single { it.factKey == FACT_KEY }
        assertEquals(INTEREST_VALUE, fact.value)
        assertEquals(UserFactStatus.CONFIRMED, fact.status)
        assertEquals(ONE_EVIDENCE, fact.evidenceCount)
        awaitSummaries()
        assertEquals(USER_SUMMARY_TEXT, fixtures.summaryRow(userId)["summary_text"])
        assertEquals(CHAT_SUMMARY_TEXT, singleText(CHAT_SUMMARY_SQL))
        assertEquals(ONE_CALL, chatCallCount(EXTRACTION_RUBRIC))
        assertTrue(
            promptFor(EXTRACTION_RUBRIC).contains("[msg=$FACT_MESSAGE_ID|from=$userId|"),
            "extraction prompt must carry the conversation message id the model has to cite",
        )
        assertEquals(ONE_CALL, chatCallCount(USER_SUMMARY_RUBRIC))
        assertEquals(ONE_CALL, chatCallCount(CHAT_SUMMARY_RUBRIC))
    }

    @Test
    fun `extraction request without pending messages keeps the cursor for the next message`() {
        storeProfile()
        stubExtraction(factDraft(UserFactCategory.INTEREST, FACT_KEY, INTEREST_VALUE, FACT_MESSAGE_ID))

        processUserKnowledgeInputPort.execute(
            FakeKnowledgeProvider.createProcessUserKnowledgeInputPortRequest(
                payload = FakeEventProvider.createUserKnowledgeRequestedPayload(
                    peerId = peerId,
                    upToConversationMessageId = FACT_MESSAGE_ID,
                ),
            ),
        )

        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)

        awaitFacts()
        assertEquals(ONE_CALL, fixtures.counts(userId, peerId)["knowledge_extraction_cursors"])
    }

    @Test
    fun `extracted fact is shown in the profile dossier from chat`() {
        storeProfile()
        stubExtraction(factDraft(UserFactCategory.INTEREST, FACT_KEY, INTEREST_VALUE, FACT_MESSAGE_ID))
        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)
        awaitFacts()
        awaitSummaries()

        askForDossier()

        awaitSentMessage(PROFILE_REPLY)
        val prompt = promptFor(PROFILE_RUBRIC)
        assertTrue(prompt.contains(KNOWLEDGE_HEADER), prompt)
        assertTrue(prompt.contains(USER_SUMMARY_TEXT), prompt)
        assertTrue(prompt.contains(NAME_LABEL + VALUE_SEPARATOR + USER_NAME), prompt)
    }

    @Test
    fun `extracted fact is placed in the answer prompt as a user knowledge block`() {
        storeProfile()
        stubExtraction(factDraft(UserFactCategory.INTEREST, FACT_KEY, INTEREST_VALUE, FACT_MESSAGE_ID))
        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)
        awaitFacts()
        awaitSummaries()

        deliverMessage(QUESTION_MESSAGE_ID, QUESTION_MESSAGE_TEXT, QUESTION_DATE)

        awaitSentMessage(ANSWER_REPLY)
        val prompt = promptFor(ANSWER_RUBRIC)
        assertTrue(prompt.contains(USER_KNOWLEDGE_OPEN), prompt)
        assertTrue(prompt.contains(USER_KNOWLEDGE_CLOSE), prompt)
        assertTrue(prompt.contains(USER_SUMMARY_TEXT), prompt)
    }

    @Test
    fun `extracted fact and chat summary reach the news digest prompt`() {
        storeProfile()
        stubExtraction(factDraft(UserFactCategory.INTEREST, FACT_KEY, INTEREST_VALUE, FACT_MESSAGE_ID))
        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)
        awaitFacts()
        awaitSummaries()

        processNewsDigestInputPort.execute(
            FakeNewsDigestProvider.createProcessNewsDigestInputPortRequest(message = digestRequestMessage()),
        )

        awaitSentMessage(NEWS_REPLY)
        val prompt = promptFor(NEWS_RUBRIC)
        assertTrue(prompt.contains(NEWS_INTERESTS_HEADER), prompt)
        assertTrue(prompt.contains(USER_SUMMARY_TEXT), prompt)
        assertTrue(prompt.contains(CHAT_TOPICS_HEADER), prompt)
        assertTrue(prompt.contains(CHAT_SUMMARY_TEXT), prompt)
    }

    @Test
    fun `extracted birthday fact is congratulated once when the vk date is hidden`() {
        storeProfile()
        stubExtraction(
            factDraft(UserFactCategory.PROFILE_ATTRIBUTE, BIRTHDAY_KEY, BIRTHDAY_VALUE, FACT_MESSAGE_ID),
        )
        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)
        val birthdayFact = awaitFacts().single { it.factKey == BIRTHDAY_KEY }
        assertEquals(NORMALIZED_BIRTHDAY, birthdayFact.value)
        assertEquals(UserFactStatus.CONFIRMED, birthdayFact.status)

        runGreetings()

        awaitSentMessage(GREETING_TEXT)
        assertEquals(ONE_CALL, greetingMessages())
        assertEquals(ONE_CALL, greetingLogRows())

        runGreetings()

        assertEquals(ONE_CALL, greetingMessages(), "second run must not send a duplicate")
        assertEquals(ONE_CALL, greetingLogRows())
    }

    @Test
    fun `redelivered knowledge event keeps one fact card and one evidence`() {
        storeProfile()
        stubExtraction(factDraft(UserFactCategory.INTEREST, FACT_KEY, INTEREST_VALUE, FACT_MESSAGE_ID))
        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)
        awaitFacts()
        awaitSummaries()

        execute(DELETE_CURSOR_SQL, peerId)
        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)

        awaitCursor()
        val counts = fixtures.counts(userId, peerId)
        assertEquals(TWO_CALLS, chatCallCount(EXTRACTION_RUBRIC))
        assertEquals(ONE_CALL, counts["user_facts"], counts.toString())
        assertEquals(ONE_CALL, counts["user_fact_evidence"], counts.toString())
        assertEquals(ONE_CALL, counts["user_knowledge_summary"], counts.toString())
        assertEquals(ONE_CALL, counts["chat_knowledge_summary"], counts.toString())
    }

    @Test
    fun `extraction llm failure keeps the message and writes no knowledge`() {
        storeProfile()
        stubExtractionFailure()

        deliverMessage(FACT_MESSAGE_ID, FACT_MESSAGE_TEXT, FACT_DATE)

        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertTrue(chatCallCount(EXTRACTION_RUBRIC) >= ONE_CALL, "extraction was not called")
        }
        val counts = fixtures.counts(userId, peerId)
        assertEquals(ONE_CALL, messageCount(), "the message must stay stored")
        assertEquals(0, counts["user_facts"], counts.toString())
        assertEquals(0, counts["user_fact_evidence"], counts.toString())
        assertEquals(0, counts["user_knowledge_summary"], counts.toString())
        assertEquals(0, counts["knowledge_extraction_cursors"], counts.toString())
    }

    @Test
    fun `stale profile summary is not used in the answer prompt`() {
        storeProfile()
        val summarizedAt = OffsetDateTime.now().minusHours(SUMMARY_AGE_HOURS)
        val factAt = summarizedAt.plusHours(1)
        fixtures.insertFact(userId, INTEREST_VALUE, factAt, factKey = FACT_KEY, peerId = peerId)
        fixtures.insertSummary(userId, STALE_SUMMARY_TEXT, factAt, summarizedAt)
        stubExtraction()

        deliverMessage(QUESTION_MESSAGE_ID, QUESTION_MESSAGE_TEXT, QUESTION_DATE)

        awaitSentMessage(ANSWER_REPLY)
        val prompt = promptFor(ANSWER_RUBRIC)
        assertTrue(prompt.contains(USER_KNOWLEDGE_OPEN), prompt)
        assertTrue(prompt.contains(FACT_HEADER), prompt)
        assertTrue(prompt.contains(FACT_LINE_PREFIX + INTEREST_VALUE), prompt)
        assertFalse(prompt.contains(STALE_SUMMARY_TEXT), prompt)
    }

    @Test
    fun `answer prompt without knowledge keeps the pre-change shape`() {
        storeProfile()
        stubExtraction()

        deliverMessage(QUESTION_MESSAGE_ID, QUESTION_MESSAGE_TEXT, QUESTION_DATE)

        awaitSentMessage(ANSWER_REPLY)
        val prompt = promptFor(ANSWER_RUBRIC)
        assertFalse(prompt.contains(USER_KNOWLEDGE_OPEN), prompt)
        assertTrue(prompt.contains(PROFILES_OPEN), prompt)
        assertTrue(prompt.contains(CHAT_HISTORY_OPEN), prompt)
        assertTrue(prompt.contains(CURRENT_MESSAGE_OPEN), prompt)
        assertTrue(prompt.substringAfter(PROFILES_CLOSE).substringBefore(CHAT_HISTORY_OPEN).isBlank(), prompt)
        assertEquals(0, fixtures.counts(userId, peerId)["user_facts"])
    }

    @Test
    fun `candidate birthday fact does not produce a greeting`() {
        storeProfile()
        insertGreetingMessage()
        saveCandidateBirthday()
        runGreetings()

        assertEquals(0, greetingMessages(), "a candidate fact must not be congratulated")
        assertEquals(0, greetingLogRows())
    }

    private fun storeProfile() {
        userProfiles.persist(
            FakeModelProvider.createVkUserProfile(
                id = userId,
                firstName = USER_FIRST_NAME,
                lastName = USER_LAST_NAME,
                screenName = USER_SCREEN_NAME,
                birthDate = null,
                city = USER_CITY,
                lastUpdated = OffsetDateTime.now(),
            ),
        )
    }

    private fun saveCandidateBirthday() {
        saveUserFactsPort.execute(
            FakePersistencePortProvider.createSaveUserFactsRequest(
                candidates = listOf(
                    FakeModelProvider.createUserFactCandidate(
                        userId = userId,
                        peerId = peerId,
                        category = UserFactCategory.PROFILE_ATTRIBUTE,
                        factKey = BIRTHDAY_KEY,
                        value = BIRTHDAY_VALUE,
                        sourceKind = KnowledgeSourceKind.INFERENCE,
                        confidence = CANDIDATE_CONFIDENCE,
                        messageId = UUID.randomUUID(),
                        conversationMessageId = CANDIDATE_MESSAGE_ID,
                        saidById = userId,
                        observedAt = FACT_DATE,
                    ),
                ),
            ),
        )
    }

    private fun deliverMessage(conversationMessageId: Long, text: String, date: OffsetDateTime) {
        val message = FakeVoProvider.createMessage(
            date = FakeVoProvider.createDate(date),
            fromId = FakeVoProvider.createFromId(userId),
            groupId = FakeVoProvider.createGroupId(COMMUNITY_ID),
            peerId = FakeVoProvider.createPeerId(peerId),
            conversationMessageId = FakeVoProvider.createConversationMessageId(conversationMessageId),
            messageText = FakeVoProvider.createMessageText(text),
        )
        messageNewEventProcessor.process(objectMapper.writeValueAsString(message))
    }

    private fun insertGreetingMessage() {
        execute(
            INSERT_MESSAGE_SQL,
            UUID.randomUUID(),
            FACT_DATE,
            userId,
            COMMUNITY_ID,
            peerId,
            CANDIDATE_MESSAGE_ID,
            FACT_MESSAGE_TEXT,
        )
    }

    private fun askForDossier() {
        val question = "что ты знаешь о [id$userId|@$USER_SCREEN_NAME]"
        processProfileInputPort.execute(
            FakeProfileProvider.createProcessProfileInputPortRequest(
                message = requestMessage(DOSSIER_MESSAGE_ID, question),
            ),
        )
    }

    private fun digestRequestMessage() = requestMessage(DIGEST_MESSAGE_ID, NEWS_REQUEST_TEXT)

    private fun requestMessage(conversationMessageId: Long, text: String) = FakeVoProvider.createMessage(
        date = FakeVoProvider.createDate(OffsetDateTime.now()),
        fromId = FakeVoProvider.createFromId(userId),
        groupId = FakeVoProvider.createGroupId(COMMUNITY_ID),
        peerId = FakeVoProvider.createPeerId(peerId),
        conversationMessageId = FakeVoProvider.createConversationMessageId(conversationMessageId),
        messageText = FakeVoProvider.createMessageText(text),
    )

    private fun runGreetings(): RunDailyGreetingsInputPort.Response = runDailyGreetings
        .execute(FakeGreetingProvider.createRunDailyGreetingsRequest(LocalDate.parse(GREETING_DATE)))

    private fun awaitFacts(): List<UserFact> {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertTrue(knowledgeOf(userId).facts.isNotEmpty(), "no facts stored for userId=$userId")
        }
        return knowledgeOf(userId).facts
    }

    private fun awaitSummaries() {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).untilAsserted {
            val counts = fixtures.counts(userId, peerId)
            assertEquals(ONE_CALL, counts["user_knowledge_summary"], counts.toString())
            assertEquals(ONE_CALL, counts["chat_knowledge_summary"], counts.toString())
        }
    }

    private fun awaitCursor() {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertEquals(ONE_CALL, fixtures.counts(userId, peerId)["knowledge_extraction_cursors"])
        }
    }

    private fun knowledgeOf(userId: Long): UserKnowledge {
        val fromId = FakeVoProvider.createFromId(userId)
        return findUserKnowledgePort.execute(
            FakePersistencePortProvider.createFindUserKnowledgeRequest(userIds = listOf(fromId)),
        ).knowledge[fromId] ?: FakeModelProvider.createUserKnowledge()
    }

    private fun awaitSentMessage(text: String) {
        await().atMost(AWAIT_SECONDS, TimeUnit.SECONDS).pollInterval(POLL_SECONDS, TimeUnit.SECONDS).untilAsserted {
            assertTrue(sentTexts().contains(text), "no messages.send with text '$text', sent: ${sentTexts()}")
        }
    }

    private fun sentTexts(): List<String> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(MESSAGES_SEND_PATH)))
        .map { request -> request.formParameter(MESSAGE_PARAM).firstValue() }

    private fun greetingMessages(): Int = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(MESSAGES_SEND_PATH)))
        .count { request ->
            request.formParameter(PEER_ID_PARAM).firstValue().toLong() == peerId &&
                request.formParameter(MESSAGE_PARAM).firstValue() == GREETING_TEXT
        }

    private fun greetingLogRows(): Int = countRows(GREETING_LOG_SQL, peerId)

    private fun messageCount(): Int = countRows(MESSAGES_COUNT_SQL, peerId)

    private fun singleText(sql: String): String = textValue(sql, peerId)

    private fun chatCallCount(rubric: String): Int = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).withRequestBody(containing(rubric)))
        .size

    private fun promptFor(rubric: String): String {
        val request = wireMock
            .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).withRequestBody(containing(rubric)))
            .last()
        return objectMapper.readTree(request.bodyAsString)["messages"].last()["content"].asText()
    }

    private fun stubChatReply(rubric: String, content: String) {
        wireMock.stubFor(
            post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .withRequestBody(containing(rubric))
                .willReturn(jsonResponse(chatReplyBody(content))),
        )
    }

    private fun stubExtraction(vararg drafts: String) {
        stubChatReply(EXTRACTION_RUBRIC, """{"facts":[${drafts.joinToString(",")}]}""")
    }

    private fun stubExtractionFailure() {
        wireMock.stubFor(
            post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .withRequestBody(containing(EXTRACTION_RUBRIC))
                .willReturn(aResponse().withStatus(MODEL_ERROR_STATUS).withBody(MODEL_ERROR_BODY)),
        )
    }

    private fun factDraft(
        category: UserFactCategory,
        key: String,
        value: String,
        sourceMessageId: Long,
        confidence: Double = SELF_STATEMENT_CONFIDENCE,
    ): String = """{"subjectId":$userId,"subjectName":null,"category":"${category.name}",""" +
        """"key":"$key","value":"$value","valuePayload":{},"confidence":$confidence,""" +
        """"sourceKind":"SELF_STATEMENT","sourceConversationMessageId":$sourceMessageId}"""

    private fun stubMessagesSend() {
        wireMock.stubFor(
            post(urlPathEqualTo(MESSAGES_SEND_PATH)).willReturn(jsonResponse(MESSAGES_SEND_REPLY)),
        )
    }

    private fun stubUsersGet() {
        wireMock.stubFor(post(urlPathEqualTo(USERS_GET_PATH)).willReturn(jsonResponse(usersGetReply())))
    }

    private fun stubDecision() {
        wireMock.stubFor(post(urlPathEqualTo(SYSTEM_ONE_PATH)).willReturn(jsonResponse(DECISION_REPLY)))
    }

    private fun usersGetReply(): String = """
        {
            "response": [
                {
                    "id": $userId,
                    "first_name": "$USER_FIRST_NAME",
                    "last_name": "$USER_LAST_NAME",
                    "screen_name": "$USER_SCREEN_NAME",
                    "city": {"id": 1, "title": "$USER_CITY"}
                }
            ]
        }
    """.trimIndent()

    private fun chatReplyBody(content: String): String = objectMapper.writeValueAsString(
        mapOf(
            "id" to "chatcmpl-knowledge-e2e",
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

    private fun countRows(sql: String, vararg params: Any?): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    private fun textValue(sql: String, vararg params: Any?): String = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
            statement.executeQuery().use { rows ->
                assertTrue(rows.next(), "no row for $sql")
                rows.getString(1)
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

    private fun clearKnowledgeData() {
        fixtures.deleteKnowledge(userId, peerId)
        execute(DELETE_MESSAGES_SQL, peerId)
        execute(DELETE_SUMMARIES_SQL, peerId)
        execute(DELETE_STATISTICS_SQL, userId)
        execute(DELETE_CHAT_STATISTICS_SQL, userId)
        execute(DELETE_HOUR_STATISTICS_SQL, userId)
        execute(DELETE_HISTORY_SQL, userId)
        execute(DELETE_PROFILE_SQL, userId)
        execute(DELETE_GREETING_LOG_SQL, peerId)
    }

    private companion object {
        const val COMMUNITY_ID = 232_142_875L

        val TEST_SEQUENCE = AtomicLong()
        const val USER_ID_BASE = 7_950_000L
        const val PEER_ID_BASE = 2_950_000_000L

        const val USER_FIRST_NAME = "Иван"
        const val USER_LAST_NAME = "Петров"
        const val USER_SCREEN_NAME = "ivan_knowledge"
        const val USER_NAME = "Иван Петров"
        const val USER_CITY = "Минск"

        const val FACT_KEY = "interest_football"
        const val BIRTHDAY_KEY = "birthday"
        const val INTEREST_VALUE = "футбол"
        const val BIRTHDAY_VALUE = "14.05"
        const val NORMALIZED_BIRTHDAY = "ДР: 14.05"

        const val USER_SUMMARY_TEXT = "Иван любит футбол и играет по средам"
        const val CHAT_SUMMARY_TEXT = "В чате обсуждали футбол и отпуск"
        const val STALE_SUMMARY_TEXT = "Устаревшая сводка про шахматы"
        const val ANSWER_REPLY = "Ответ с знаниями из чата"
        const val PROFILE_REPLY = "Досье с знаниями из чата"
        const val NEWS_REPLY = "Сводка новостей с интересами"
        const val GREETING_TEXT = "С днём рождения, Иван Петров!"

        const val FACT_MESSAGE_ID = 910_001L
        const val QUESTION_MESSAGE_ID = 910_002L
        const val DOSSIER_MESSAGE_ID = 910_003L
        const val DIGEST_MESSAGE_ID = 910_004L
        const val CANDIDATE_MESSAGE_ID = 910_005L

        const val FACT_MESSAGE_TEXT = "Обожаю футбол, играю каждую среду"
        const val QUESTION_MESSAGE_TEXT = "что я люблю, @simarel?"
        const val NEWS_REQUEST_TEXT = "что нового за вчера, @simarel?"
        const val GREETING_DATE = "2026-05-14"

        const val ONE_CALL = 1
        const val TWO_CALLS = 2
        const val ONE_EVIDENCE = 1
        const val SELF_STATEMENT_CONFIDENCE = 0.95
        const val CANDIDATE_CONFIDENCE = 0.6
        const val SUMMARY_AGE_HOURS = 2L

        const val AWAIT_SECONDS = 120L
        const val POLL_SECONDS = 1L

        const val OK_STATUS = 200
        const val MODEL_ERROR_STATUS = 500
        const val MODEL_ERROR_BODY = "model is down"

        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
        const val MESSAGES_SEND_PATH = "/method/messages.send"
        const val USERS_GET_PATH = "/method/users.get"
        const val SYSTEM_ONE_PATH = "/v1/systemone"
        const val PEER_ID_PARAM = "peer_id"
        const val MESSAGE_PARAM = "message"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val JSON_CONTENT_TYPE = "application/json"

        const val EXTRACTION_RUBRIC = "Извлеки факты о людях из сообщений ниже."
        const val USER_SUMMARY_RUBRIC = "Ты — составитель сводки профиля человека"
        const val CHAT_SUMMARY_RUBRIC = "Составь сводку чата по сообщениям ниже."
        const val ANSWER_RUBRIC = "You are a helpful ai chatbot in a chat."
        const val PROFILE_RUBRIC = "Ты — помощник, который собирает короткое человекочитаемое досье"
        const val NEWS_RUBRIC = "Ты — помощник, который кратко пересказывает"
        const val GREETING_RUBRIC = "Ты — дружелюбный бот сообщества VK"

        const val USER_KNOWLEDGE_OPEN = "[USER_KNOWLEDGE]"
        const val USER_KNOWLEDGE_CLOSE = "[/USER_KNOWLEDGE]"
        const val PROFILES_OPEN = "<профили_участников>"
        const val PROFILES_CLOSE = "</профили_участников>"
        const val CHAT_HISTORY_OPEN = "<история_чата>"
        const val CURRENT_MESSAGE_OPEN = "<текущее_сообщение>"
        const val KNOWLEDGE_HEADER = "Из общения:"
        const val FACT_HEADER = "Интересы и увлечения:"
        const val NEWS_INTERESTS_HEADER = "Интересы автора запроса:"
        const val FACT_LINE_PREFIX = "- "
        const val CHAT_TOPICS_HEADER = "Темы чата:"
        const val NAME_LABEL = "Имя"
        const val VALUE_SEPARATOR = ": "

        const val MESSAGES_SEND_REPLY = """{"response":12345}"""

        const val CHAT_SUMMARY_SQL = "select summary_text from chat_knowledge_summary where peer_id = ?"
        const val MESSAGES_COUNT_SQL = "select count(*) from messages where peer_id = ?"
        const val GREETING_LOG_SQL = "select count(*) from greeting_log where peer_id = ?"
        const val DELETE_CURSOR_SQL = "delete from knowledge_extraction_cursors where peer_id = ?"
        const val DELETE_MESSAGES_SQL = "delete from messages where peer_id = ?"
        const val DELETE_SUMMARIES_SQL = "delete from summaries where peer_id = ?"
        const val DELETE_STATISTICS_SQL = "delete from user_statistics where user_id = ?"
        const val DELETE_CHAT_STATISTICS_SQL = "delete from user_chat_statistics where user_id = ?"
        const val DELETE_HOUR_STATISTICS_SQL = "delete from user_hour_statistics where user_id = ?"
        const val DELETE_HISTORY_SQL = "delete from vk_user_profile_history where profile_id = ?"
        const val DELETE_PROFILE_SQL = "delete from vk_user_profiles where id = ?"
        const val DELETE_GREETING_LOG_SQL = "delete from greeting_log where peer_id = ?"

        const val INSERT_MESSAGE_SQL = "insert into messages " +
            "(id, date, from_id, group_id, peer_id, conversation_message_id, message_text, forwarded_messages) " +
            "values (?, ?, ?, ?, ?, ?, ?, null)"

        val FACT_DATE: OffsetDateTime = OffsetDateTime.now().minusDays(1)
        val QUESTION_DATE: OffsetDateTime = OffsetDateTime.now()

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
