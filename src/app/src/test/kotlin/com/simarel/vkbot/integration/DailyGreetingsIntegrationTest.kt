package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.notContaining
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.sql.Date
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@TestProfile(IntegrationTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class DailyGreetingsIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runDailyGreetings: RunDailyGreetingsInputPort

    @Inject
    lateinit var userProfiles: JooqVkUserProfileRepository

    @Inject
    lateinit var messages: JooqMessageRepository

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    @BeforeEach
    fun setUp() {
        WireMockTestResource.resetToDefaultStubs()
        clearDatabase()
        wireMock.stubFor(
            post(urlPathEqualTo(MESSAGES_SEND_PATH)).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"response":12345}"""),
            ),
        )
    }

    @Test
    fun `birthday greeting is sent once per chat with names in the text and no forward block`() {
        seedProfile(101, "Анна", "Иванова", "12.09.1990")
        seedProfile(102, "Борис", "Петров", "12.09.1985")
        seedMessage(fromId = 101, peerId = GROUP_CHAT_ONE)
        seedMessage(fromId = 101, peerId = DM_PEER)
        seedMessage(fromId = 102, peerId = GROUP_CHAT_ONE)
        stubChatReply(
            "Поздравляем, Анна Иванова и Борис Петров!",
            matchingJsonPath(USER_PROMPT, containing("Борис Петров")),
        )
        stubChatReply("Поздравляем, Анна Иванова!", matchingJsonPath(USER_PROMPT, notContaining("Борис Петров")))

        val response = runFor(LocalDate.parse("2026-09-12"))

        assertEquals(2, response.planned)
        assertEquals(2, response.sent)
        assertEquals(0, response.failed)
        val sent = sentMessages()
        assertEquals(2, sent.size)
        assertEquals("Поздравляем, Анна Иванова и Борис Петров!", sent.single { it.peerId == GROUP_CHAT_ONE }.text)
        assertEquals("Поздравляем, Анна Иванова!", sent.single { it.peerId == DM_PEER }.text)
        assertTrue(sent.none { it.body.contains("forward") })
        assertTrue(sent.all { it.randomId != 0 })
        val prompts = chatPrompts()
        assertEquals(2, prompts.size)
        assertTrue(prompts.any { it.contains("Анна Иванова") && it.contains("Борис Петров") })
        assertTrue(prompts.any { it.contains("Анна Иванова") && !it.contains("Борис Петров") })
    }

    @Test
    fun `new year greeting goes to every group chat and never to a direct message`() {
        seedMessage(fromId = 201, peerId = GROUP_CHAT_ONE)
        seedMessage(fromId = 202, peerId = GROUP_CHAT_TWO)
        seedMessage(fromId = 203, peerId = 203)
        stubChatReply("С Новым годом!")

        val response = runFor(LocalDate.parse("2026-01-01"))

        assertEquals(2, response.planned)
        assertEquals(2, response.sent)
        val sent = sentMessages()
        assertEquals(setOf(GROUP_CHAT_ONE, GROUP_CHAT_TWO), sent.map { it.peerId }.toSet())
        assertTrue(sent.all { it.text.contains("С Новым годом!") })
        assertEquals(2, chatPrompts().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["2026-09-13", "2024-09-12"])
    fun `programmer day greeting is sent on the 256th day of the year`(date: String) {
        seedMessage(fromId = 301, peerId = GROUP_CHAT_ONE)
        stubChatReply("С Днём программиста!")

        val response = runFor(LocalDate.parse(date))

        assertEquals(1, response.sent)
        val sent = sentMessages().single()
        assertEquals(GROUP_CHAT_ONE, sent.peerId)
        assertTrue(sent.text.contains("С Днём программиста!"))
        assertTrue(chatPrompts().single().contains("День программиста"))
    }

    @Test
    fun `holiday and birthday on the same date produce two messages`() {
        seedProfile(401, "Анна", "Иванова", "01.01.1990")
        seedMessage(fromId = 401, peerId = GROUP_CHAT_ONE)
        stubChatReply("С Новым годом!", matchingJsonPath(USER_PROMPT, notContaining("Анна Иванова")))
        stubChatReply(
            "С днём рождения, Анна Иванова!",
            matchingJsonPath(USER_PROMPT, containing("Анна Иванова")),
        )

        val response = runFor(LocalDate.parse("2026-01-01"))

        assertEquals(2, response.planned)
        assertEquals(2, response.sent)
        assertEquals(0, response.failed)
        val sent = sentMessages()
        assertEquals(2, sent.size)
        assertTrue(sent.all { it.peerId == GROUP_CHAT_ONE })
        assertTrue(sent.any { it.text == "С Новым годом!" })
        assertTrue(sent.any { it.text == "С днём рождения, Анна Иванова!" })
        val prompts = chatPrompts()
        assertEquals(2, prompts.size)
        assertTrue(prompts.any { it.contains("Новый год") && !it.contains("Анна Иванова") })
        assertTrue(prompts.any { it.contains("День рождения") && it.contains("Анна Иванова") })
    }

    @Test
    fun `repeated run on the same date sends nothing and keeps one greeting_log row`() {
        seedMessage(fromId = 501, peerId = GROUP_CHAT_ONE)
        stubChatReply("С Днём программиста!")
        val date = LocalDate.parse("2026-09-13")

        val first = runFor(date)
        val second = runFor(date)

        assertEquals(1, first.sent)
        assertEquals(1, second.planned)
        assertEquals(0, second.sent)
        assertEquals(1, sentMessages().size)
        assertEquals(1, greetingLogRows(date, GROUP_CHAT_ONE))
    }

    @Test
    fun `concurrent runs on the same date send one message`() {
        seedMessage(fromId = 511, peerId = GROUP_CHAT_ONE)
        stubChatReply("С Днём программиста!")
        val date = LocalDate.parse("2026-09-13")
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val runs = (1..2).map {
                executor.submit<RunDailyGreetingsInputPort.Response> {
                    start.await()
                    runFor(date)
                }
            }
            start.countDown()
            assertEquals(1, runs.sumOf { it.get(60, TimeUnit.SECONDS).sent })
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1, sentMessages().size)
        assertEquals(1, greetingLogRows(date, GROUP_CHAT_ONE))
    }

    @Test
    fun `ai failure falls back to the deterministic template and still sends`() {
        seedMessage(fromId = 601, peerId = GROUP_CHAT_ONE)
        wireMock.stubFor(
            post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(
                aResponse().withStatus(500).withBody("model is down"),
            ),
        )

        val response = runFor(LocalDate.parse("2026-09-13"))

        assertEquals(1, response.planned)
        assertEquals(1, response.sent)
        assertEquals(0, response.failed)
        assertEquals("С Днём программиста!", sentMessages().single().text)
    }

    @Test
    fun `date without holidays and birthdays sends no messages`() {
        seedMessage(fromId = 701, peerId = GROUP_CHAT_ONE)
        stubChatReply("Поздравляем!")

        val response = runFor(LocalDate.parse("2026-09-12"))

        assertEquals(0, response.planned)
        assertEquals(0, response.sent)
        assertTrue(sentMessages().isEmpty())
        assertTrue(chatPrompts().isEmpty())
    }

    @Test
    fun `february 29 birthday is congratulated on march 1 of a non-leap year but not on february 28`() {
        seedProfile(801, "Иван", "Петров", "29.2")
        seedMessage(fromId = 801, peerId = GROUP_CHAT_ONE)
        stubChatReply("С днём рождения, Иван Петров!")

        val marchFirst = runFor(LocalDate.parse("2025-03-01"))

        assertEquals(1, marchFirst.sent)
        assertTrue(chatPrompts().single().contains("Иван Петров"))
        assertEquals("С днём рождения, Иван Петров!", sentMessages().single().text)

        val februaryTwentyEighth = runFor(LocalDate.parse("2025-02-28"))

        assertEquals(0, februaryTwentyEighth.sent)
        assertEquals(1, sentMessages().size)
    }

    private fun runFor(date: LocalDate): RunDailyGreetingsInputPort.Response = runDailyGreetings
        .execute(FakeGreetingProvider.createRunDailyGreetingsRequest(date))

    private fun seedProfile(id: Long, firstName: String, lastName: String, birthDate: String) {
        userProfiles.persist(
            FakeModelProvider.createVkUserProfile(
                id = id,
                firstName = firstName,
                lastName = lastName,
                birthDate = birthDate,
            ),
        )
    }

    private fun seedMessage(fromId: Long, peerId: Long) {
        messages.persist(
            MessageEntity().apply {
                id = UUID.randomUUID()
                date = OffsetDateTime.now()
                groupId = COMMUNITY_ID
                this.fromId = fromId
                this.peerId = peerId
                conversationMessageId = 1
                messageText = "seed"
            },
        )
    }

    private fun sentMessages(): List<SentMessage> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(MESSAGES_SEND_PATH)))
        .map { request ->
            SentMessage(
                peerId = request.formParameter("peer_id").firstValue().toLong(),
                text = request.formParameter("message").firstValue(),
                randomId = request.formParameter("random_id").firstValue().toInt(),
                body = request.bodyAsString,
            )
        }

    private fun chatPrompts(): List<String> = wireMock
        .findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)))
        .map { request ->
            objectMapper.readTree(request.bodyAsString).path("messages").path(1).path("content").asText()
        }

    private fun greetingLogRows(date: LocalDate, peerId: Long): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM greeting_log WHERE greeting_date = ? AND peer_id = ?")
            .use { statement ->
                statement.setDate(1, Date.valueOf(date))
                statement.setLong(2, peerId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
    }

    private fun clearDatabase() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM greeting_log")
                statement.executeUpdate("DELETE FROM messages")
                statement.executeUpdate("DELETE FROM vk_user_profiles")
            }
        }
    }

    private fun stubChatReply(content: String, vararg bodyMatchers: StringValuePattern) {
        val mapping = post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
        bodyMatchers.forEach { mapping.withRequestBody(it) }
        wireMock.stubFor(
            mapping.willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(chatCompletion(content)),
            ),
        )
    }

    private fun chatCompletion(content: String): String = objectMapper.writeValueAsString(
        mapOf(
            "id" to "chatcmpl-greeting",
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

    private data class SentMessage(
        val peerId: Long,
        val text: String,
        val randomId: Int,
        val body: String,
    )

    private companion object {
        const val GROUP_CHAT_ONE = 2_000_000_001L
        const val GROUP_CHAT_TWO = 2_000_000_002L
        const val DM_PEER = 101L
        const val COMMUNITY_ID = 232_142_875L
        const val CHAT_COMPLETIONS_PATH = "/v1/chat/completions"
        const val MESSAGES_SEND_PATH = "/method/messages.send"
        const val USER_PROMPT = "$.messages[-1].content"
    }
}
