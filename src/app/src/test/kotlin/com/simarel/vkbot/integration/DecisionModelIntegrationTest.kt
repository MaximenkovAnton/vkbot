package com.simarel.vkbot.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.simarel.vkbot.processor.domain.decision.MessageClass
import com.simarel.vkbot.processor.domain.exception.DecisionModelException
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageOutputPort
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageRequest
import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import dev.langchain4j.model.ModelProvider
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.decision.DecisionModel
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaConfig
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

private const val SYSTEM_ONE_PATH = "/v1/systemone"
private const val SPAM_TEXT = "Это спам, реклама, накрутка или мошенничество?"
private const val CLASS_TEXT = "Определи тип сообщения в чате с ботом."
private const val CLEAN_TEXT = "Купи п0дпис4ик0в и лайки дешев0, накрутка ВК"
private const val DOUBLE_SPACED_INPUT =
    "К  а  з  и  н  о     о  н  л  а  й  н  ,     б  о  н  у  с     5  0  0  %     н  а     " +
        "п  е  р  в  ы  й     д  е  п  о  з  и  т"
private const val DOUBLE_SPACED_FIRST_PASS =
    "К а з и н о о н л а й н б о н у с 5 0 0 % н а п е р в ы й д е п о з и т"
private const val SPAM_CRITERIA = "Реклама, обман, накрутка, казино, крипта, заработок, продажа баз, " +
    "фишинг-ссылка"
private const val COMMAND_CRITERIA =
    "Просьба к боту выполнить действие: /start, /help, погода, напомни, переведи, включи, статистика"
private const val HAM_CRITERIA = "Обычное общение между людьми, без рекламы и без обращения к боту"
private val VALID_DECISION_RESPONSE = """
    {
      "model": "test-decision",
      "answers": {
        "spam": {"type": "noul", "noul": 0.908},
        "class": {
          "type": "choice",
          "choice": "spam",
          "probabilities": {"spam": 0.959, "command": 0.009, "ham": 0.032},
          "confidence": 0.959
        }
      },
      "usage": {"input_tokens": 199, "output_tokens": 0}
    }
""".trimIndent()

@QuarkusTest
@TestProfile(IntegrationTestProfile::class)
@QuarkusTestResource(WireMockTestResource::class)
class DecisionModelIntegrationTest {

    @Inject
    lateinit var analyzeMessageOutputPort: AnalyzeMessageOutputPort

    @Inject
    lateinit var decisionModel: DecisionModel

    @Inject
    lateinit var chatModel: ChatModel

    @Inject
    lateinit var ollamaConfig: LangChain4jOllamaConfig

    private val wireMock: WireMockServer
        get() = WireMockTestResource.wireMockServer

    @BeforeEach
    fun setup() {
        WireMockTestResource.resetToDefaultStubs()
    }

    @Test
    fun `decision port returns the analysis from the real ollama decision model`() {
        stubValidDecision()

        val analysis = analyzeMessageOutputPort.execute(request(CLEAN_TEXT)).analysis

        assertEquals(MessageClass.SPAM, analysis.messageClass)
        assertEquals(0.959, analysis.classConfidence ?: Double.NaN)
        assertEquals(0.908, analysis.spamProbability)
        assertEquals(
            mapOf(
                MessageClass.SPAM to 0.959,
                MessageClass.COMMAND to 0.009,
                MessageClass.HAM to 0.032,
            ),
            analysis.classProbabilities,
        )
    }

    @Test
    fun `decision model uses ollama and chat model uses openai`() {
        assertEquals(ModelProvider.OLLAMA, decisionModel.provider())
        assertEquals(ModelProvider.OPEN_AI, chatModel.provider())
    }

    @Test
    fun `decision model logging inherits the global log flags`() {
        assertEquals(Optional.of(true), ollamaConfig.defaultConfig().logRequests())
        assertEquals(Optional.of(true), ollamaConfig.defaultConfig().logResponses())
    }

    @Test
    fun `decision port sends normalized text and both questions to system one`() {
        stubValidDecision()

        analyzeMessageOutputPort.execute(request(DOUBLE_SPACED_INPUT))

        wireMock.verify(
            1,
            postRequestedFor(urlEqualTo(SYSTEM_ONE_PATH))
                .withRequestBody(matchingJsonPath("$.state", equalTo(DOUBLE_SPACED_FIRST_PASS)))
                .withRequestBody(matchingJsonPath("$.model", equalTo("test-decision")))
                .withRequestBody(matchingJsonPath("$.questions.spam.type", equalTo("noul")))
                .withRequestBody(matchingJsonPath("$.questions.spam.instructions", equalTo(SPAM_TEXT)))
                .withRequestBody(matchingJsonPath("$.questions.class.type", equalTo("choice")))
                .withRequestBody(matchingJsonPath("$.questions.class.instructions", equalTo(CLASS_TEXT)))
                .withRequestBody(matchingJsonPath("$.questions.class.criteria.spam", equalTo(SPAM_CRITERIA)))
                .withRequestBody(matchingJsonPath("$.questions.class.criteria.command", equalTo(COMMAND_CRITERIA)))
                .withRequestBody(matchingJsonPath("$.questions.class.criteria.ham", equalTo(HAM_CRITERIA))),
        )
    }

    @Test
    fun `decision port fails with DecisionModelException on http error`() {
        wireMock.stubFor(post(urlEqualTo(SYSTEM_ONE_PATH)).willReturn(aResponse().withStatus(500)))

        val exception = assertThrows(DecisionModelException::class.java) {
            analyzeMessageOutputPort.execute(request(CLEAN_TEXT))
        }

        assertEquals("Decision model call failed", exception.message)
        assertNotNull(exception.cause)
    }

    @Test
    fun `decision port fails with DecisionModelException on malformed response`() {
        wireMock.stubFor(
            post(urlEqualTo(SYSTEM_ONE_PATH)).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("not json"),
            ),
        )

        val exception = assertThrows(DecisionModelException::class.java) {
            analyzeMessageOutputPort.execute(request(CLEAN_TEXT))
        }

        assertEquals("Decision model call failed", exception.message)
        assertNotNull(exception.cause)
    }

    private fun stubValidDecision() {
        wireMock.stubFor(
            post(urlEqualTo(SYSTEM_ONE_PATH)).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VALID_DECISION_RESPONSE),
            ),
        )
    }

    private fun request(
        text: String,
    ): AnalyzeMessageRequest = FakeDecisionProvider.createAnalyzeMessageRequest(
        FakeVoProvider.createMessageText(text),
    )
}
