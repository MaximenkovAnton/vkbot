package com.simarel.vkbot.processor.adapter.output.decision

import com.simarel.vkbot.processor.domain.decision.MessageClass
import com.simarel.vkbot.processor.domain.exception.DecisionModelException
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageResponse
import com.simarel.vkbot.processor.testfixtures.adapter.output.decision.FakeDecisionModel
import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import dev.langchain4j.exception.ContentFilteredException
import dev.langchain4j.exception.InvalidDecisionResponseException
import dev.langchain4j.model.decision.request.ChoiceQuestion
import dev.langchain4j.model.decision.request.TextInput
import dev.langchain4j.model.decision.request.YesNoQuestion
import dev.langchain4j.model.decision.response.ChoiceAnswer
import dev.langchain4j.model.decision.response.DecisionResponse
import dev.langchain4j.model.decision.response.RefusalAnswer
import dev.langchain4j.model.decision.response.YesNoAnswer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val CALL_FAILED_MESSAGE = "Decision model call failed"
private const val INVALID_RESPONSE_MESSAGE = "Decision model response is invalid"
private const val SPAM_QUESTION = "spam"
private const val CLASS_QUESTION = "class"
private const val SPAM_TEXT = "Это спам, реклама, накрутка или мошенничество?"
private const val CLASS_TEXT = "Определи тип сообщения в чате с ботом."
private const val CLEAN_TEXT = "Купи п0дпис4ик0в и лайки дешев0, накрутка ВК"
private const val DOUBLE_SPACED_INPUT =
    "К  а  з  и  н  о     о  н  л  а  й  н  ,     б  о  н  у  с     5  0  0  %     н  а     " +
        "п  е  р  в  ы  й     д  е  п  о  з  и  т"
private const val DOUBLE_SPACED_FIRST_PASS =
    "К а з и н о о н л а й н б о н у с 5 0 0 % н а п е р в ы й д е п о з и т"
private val CLASS_CRITERIA = mapOf(
    "spam" to "Реклама, обман, накрутка, казино, крипта, заработок, продажа баз, фишинг-ссылка",
    "command" to
        "Просьба к боту выполнить действие: /start, /help, погода, напомни, переведи, включи, статистика",
    "ham" to "Обычное общение между людьми, без рекламы и без обращения к боту",
)
private val DEFAULT_PROBABILITIES = mapOf("spam" to 0.959, "command" to 0.009, "ham" to 0.032)

class DecisionModelOutputAdapterTest {

    @Test
    fun `execute builds normalized input and exactly two questions`() {
        val fake = FakeDecisionModel().apply { response = validResponse() }

        execute(fake, DOUBLE_SPACED_INPUT)

        assertEquals(1, fake.calls)
        val request = fake.requests.single()
        assertEquals(DOUBLE_SPACED_FIRST_PASS, (request.input() as TextInput).text())
        assertEquals(setOf(SPAM_QUESTION, CLASS_QUESTION), request.questions().keys)
        assertEquals(SPAM_TEXT, (request.questions().getValue(SPAM_QUESTION) as YesNoQuestion).text())
        val classQuestion = request.questions().getValue(CLASS_QUESTION) as ChoiceQuestion
        assertEquals(CLASS_TEXT, classQuestion.text())
        assertEquals(CLASS_CRITERIA, classQuestion.options())
    }

    @ParameterizedTest
    @CsvSource("spam, SPAM", "command, COMMAND", "ham, HAM")
    fun `execute maps a valid decision response to the analysis`(choice: String, expectedClass: MessageClass) {
        val fake = FakeDecisionModel().apply { response = validResponse(choice) }

        val analysis = execute(fake, CLEAN_TEXT).analysis

        assertEquals(expectedClass, analysis.messageClass)
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
    fun `execute maps absent confidence to null`() {
        val fake = FakeDecisionModel().apply { response = validResponse(confidence = null) }

        assertNull(execute(fake, CLEAN_TEXT).analysis.classConfidence)
    }

    @Test
    fun `execute does not call the model for blank normalized text`() {
        val fake = FakeDecisionModel()

        val analysis = execute(fake, "   ").analysis

        assertEquals(0, fake.calls)
        assertEquals(MessageClass.HAM, analysis.messageClass)
        assertNull(analysis.classConfidence)
        assertEquals(emptyMap<MessageClass, Double>(), analysis.classProbabilities)
        assertEquals(0.0, analysis.spamProbability)
    }

    @Test
    fun `execute short-circuits text that becomes empty after normalization`() {
        val fake = FakeDecisionModel()

        val analysis = execute(fake, "\u200b").analysis

        assertEquals(0, fake.calls)
        assertEquals(MessageClass.HAM, analysis.messageClass)
        assertEquals(emptyMap<MessageClass, Double>(), analysis.classProbabilities)
    }

    @Test
    fun `execute wraps a failed decision call`() {
        val fake = FakeDecisionModel().apply {
            failure = InvalidDecisionResponseException("The response contains no answer to question 'class'")
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertSame(fake.failure, exception.cause)
        assertEquals(ExceptionStatus.EXTERNAL_FAILURE, exception.status())
    }

    @Test
    fun `execute wraps a RuntimeException from the model`() {
        val fake = FakeDecisionModel().apply { failure = RuntimeException("The response is not json") }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertSame(fake.failure, exception.cause)
    }

    @Test
    fun `execute wraps a refusal answer`() {
        val fake = FakeDecisionModel().apply {
            response = DecisionResponse.builder()
                .answer(SPAM_QUESTION, YesNoAnswer.from(0.908))
                .answer(CLASS_QUESTION, RefusalAnswer.of())
                .build()
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertEquals(ContentFilteredException::class.java, exception.cause?.javaClass)
    }

    @Test
    fun `execute wraps a refusal on the spam question`() {
        val fake = FakeDecisionModel().apply {
            response = DecisionResponse.builder()
                .answer(SPAM_QUESTION, RefusalAnswer.of())
                .answer(CLASS_QUESTION, choiceAnswer("spam", DEFAULT_PROBABILITIES, 0.959))
                .build()
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertEquals(ContentFilteredException::class.java, exception.cause?.javaClass)
    }

    @Test
    fun `execute rejects a response without a required answer`() {
        val fake = FakeDecisionModel().apply {
            response = DecisionResponse.builder()
                .answer(SPAM_QUESTION, YesNoAnswer.from(0.908))
                .build()
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertEquals(InvalidDecisionResponseException::class.java, exception.cause?.javaClass)
    }

    @Test
    fun `execute rejects a choice outside the configured options`() {
        val fake = FakeDecisionModel().apply {
            response = validResponse(choice = "other")
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertEquals(InvalidDecisionResponseException::class.java, exception.cause?.javaClass)
    }

    @Test
    fun `execute rejects an unknown extra class probability`() {
        val fake = FakeDecisionModel().apply {
            response = validResponse(
                classProbabilities = DEFAULT_PROBABILITIES + ("other" to 0.01),
            )
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(CALL_FAILED_MESSAGE, exception.message)
        assertEquals(InvalidDecisionResponseException::class.java, exception.cause?.javaClass)
    }

    @Test
    fun `execute rejects incomplete class probabilities`() {
        val fake = FakeDecisionModel().apply {
            response = validResponse(
                classProbabilities = mapOf("spam" to 0.959, "command" to 0.041),
            )
        }

        val exception = assertThrows(DecisionModelException::class.java) { execute(fake, CLEAN_TEXT) }

        assertEquals(INVALID_RESPONSE_MESSAGE, exception.message)
        assertEquals(IllegalArgumentException::class.java, exception.cause?.javaClass)
    }

    private fun execute(
        fake: FakeDecisionModel,
        text: String,
    ): AnalyzeMessageResponse = DecisionModelOutputAdapter(fake).execute(
        FakeDecisionProvider.createAnalyzeMessageRequest(FakeVoProvider.createMessageText(text)),
    )

    private fun validResponse(
        choice: String = "spam",
        classProbabilities: Map<String, Double> = DEFAULT_PROBABILITIES,
        confidence: Double? = 0.959,
    ): DecisionResponse = DecisionResponse.builder()
        .answer(SPAM_QUESTION, YesNoAnswer.from(0.908))
        .answer(CLASS_QUESTION, choiceAnswer(choice, classProbabilities, confidence))
        .build()

    private fun choiceAnswer(
        choice: String,
        probabilities: Map<String, Double>,
        confidence: Double?,
    ): ChoiceAnswer = ChoiceAnswer.builder()
        .value(choice)
        .probabilities(probabilities)
        .confidence(confidence)
        .build()
}
