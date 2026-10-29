package com.simarel.vkbot.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneProvidersResponse
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.vane.VaneSearchOutputPort
import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import com.simarel.vkbot.ai.testfixtures.FakeVaneProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.simarel.vkbot.ai.adapter.output.ai.tool.vane.VaneSearchResponse as VaneClientSearchResponse

@QuarkusTest
@TestProfile(IntegrationTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class AiIntegrationTest {

    @Inject
    lateinit var generateAnswerOutputPort: GenerateAnswerOutputPort

    @Inject
    lateinit var summarizationOutputPort: SummarizationOutputPort

    @Inject
    lateinit var vaneSearchOutputPort: VaneSearchOutputPort

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    @BeforeEach
    fun setup() {
        WireMockTestResource.resetToDefaultStubs()
    }

    @Test
    fun `generate answer port returns the answer produced by the model`() {
        stubChatReply("Hello! This is a response from AI.")

        val response = generateAnswerOutputPort.execute(answerRequest())

        assertEquals("Hello! This is a response from AI.", response.answer)
    }

    @Test
    fun `generate answer port sends formatted profiles, chat history and current message to the model`() {
        stubChatReply("ok")

        generateAnswerOutputPort.execute(answerRequest())

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/api/chat"))
                .withRequestBody(containing("- id30: Ivan Petrov (@ivan_petrov)"))
                .withRequestBody(containing("- id-40: Test Group (@testgroup)"))
                .withRequestBody(containing("ID: 1 | Отправитель: Ivan Petrov (id30)"))
                .withRequestBody(containing("история от пользователя"))
                .withRequestBody(containing("текущий вопрос")),
        )
    }

    @Test
    fun `summarization port parses short and full summaries from the model json`() {
        stubChatReply(SUMMARY_JSON)

        val response = summarizationOutputPort.execute(FakeSummaryProvider.createSummarizationRequest())

        assertEquals("short from model", response.shortSummary)
        assertEquals("full from model", response.fullSummary)
    }

    @Test
    fun `summarization port retries model failures and throws after three attempts`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/api/chat")).willReturn(aResponse().withStatus(500).withBody("model is down")),
        )

        assertThrows(RuntimeException::class.java) {
            summarizationOutputPort.execute(FakeSummaryProvider.createSummarizationRequest())
        }
        wireMock.verify(
            PORT_ATTEMPTS * MODEL_ATTEMPTS_PER_PORT_ATTEMPT,
            postRequestedFor(urlPathEqualTo("/api/chat")),
        )
    }

    @Test
    fun `vane port returns the message and sources mapped from the provider response`() {
        stubVaneProviders()
        stubVaneSearch()

        val response = vaneSearchOutputPort.execute(
            FakeVaneProvider.createVanePortSearchRequest(
                query = "kotlin coroutines",
                sources = listOf("academic"),
                optimizationMode = "quality",
            ),
        )

        assertEquals("Vane answer", response.message)
        assertEquals("source content", response.sources.single().content)
        assertEquals("Source title", response.sources.single().title)
        assertEquals("https://example.com/source", response.sources.single().url)
        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/api/search"))
                .withRequestBody(containing("\"query\":\"kotlin coroutines\""))
                .withRequestBody(containing("\"sources\":[\"academic\"]"))
                .withRequestBody(containing("\"optimizationMode\":\"quality\""))
                .withRequestBody(containing("\"chatModel\":{\"providerId\":\"vane-provider\",\"key\":\"chat-key\"}"))
                .withRequestBody(containing("\"embeddingModel\":{\"providerId\":\"vane-provider\""))
                .withRequestBody(containing("\"key\":\"embedding-key\"")),
        )
    }

    @Test
    fun `vane port fails when no provider has both chat and embedding models`() {
        stubVaneProviders(
            FakeVaneProvider.createVaneProvidersResponse(
                providers = listOf(FakeVaneProvider.createVaneProvider(embeddingModels = null)),
            ),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            vaneSearchOutputPort.execute(FakeVaneProvider.createVanePortSearchRequest())
        }

        assertEquals("No providers configured in Vane", exception.message)
    }

    private fun answerRequest(): GenerateAnswerOutputPortRequest {
        val user = FakeModelProvider.createVkUserProfile(
            id = 30,
            firstName = "Ivan",
            lastName = "Petrov",
            screenName = "ivan_petrov",
        )
        val group = FakeModelProvider.createVkGroupProfile(
            id = -40,
            name = "Test Group",
            screenName = "testgroup",
        )
        return FakeAnswerProvider.createGenerateAnswerOutputPortRequest(
            currentMessage = FakeVoProvider.createMessage(
                fromId = FakeVoProvider.createFromId(30),
                messageText = FakeVoProvider.createMessageText("текущий вопрос"),
            ),
            chatHistory = listOf(
                FakeAnswerProvider.createChatMessage(id = 1, fromId = 30, text = "история от пользователя"),
                FakeAnswerProvider.createChatMessage(id = 2, fromId = -40, text = "история от группы"),
            ),
            userProfiles = mapOf(user.id to user),
            groupProfiles = mapOf(group.id to group),
        )
    }

    private fun stubChatReply(content: String) {
        wireMock.stubFor(
            post(urlPathEqualTo("/api/chat")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"message":{"role":"assistant","content":"$content"},"done":true}"""),
            ),
        )
    }

    private fun stubVaneProviders(
        response: VaneProvidersResponse = FakeVaneProvider.createVaneProvidersResponse(),
    ) {
        wireMock.stubFor(get(urlPathEqualTo("/api/providers")).willReturn(jsonResponse(response)))
    }

    private fun stubVaneSearch(
        response: VaneClientSearchResponse = FakeVaneProvider.createVaneClientSearchResponse(),
    ) {
        wireMock.stubFor(post(urlPathEqualTo("/api/search")).willReturn(jsonResponse(response)))
    }

    private fun jsonResponse(body: Any): ResponseDefinitionBuilder = aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(objectMapper.writeValueAsString(body))

    private companion object {
        const val PORT_ATTEMPTS = 3
        const val MODEL_ATTEMPTS_PER_PORT_ATTEMPT = 3
        const val SUMMARY_JSON = """{\"shortSummary\":\"short from model\",\"fullSummary\":\"full from model\"}"""
    }
}
