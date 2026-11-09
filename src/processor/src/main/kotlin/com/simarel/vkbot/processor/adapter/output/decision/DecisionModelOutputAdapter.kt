package com.simarel.vkbot.processor.adapter.output.decision

import com.simarel.vkbot.processor.domain.decision.DecisionAnalysis
import com.simarel.vkbot.processor.domain.decision.MessageClass
import com.simarel.vkbot.processor.domain.decision.MessageTextNormalizer
import com.simarel.vkbot.processor.domain.exception.DecisionModelException
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageOutputPort
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageRequest
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageResponse
import dev.langchain4j.model.decision.DecisionModel
import dev.langchain4j.model.decision.request.ChoiceQuestion
import dev.langchain4j.model.decision.request.DecisionRequest
import dev.langchain4j.model.decision.request.YesNoQuestion
import jakarta.enterprise.context.ApplicationScoped

private const val SPAM_QUESTION = "spam"
private const val CLASS_QUESTION = "class"
private const val CALL_FAILED_MESSAGE = "Decision model call failed"
private const val INVALID_RESPONSE_MESSAGE = "Decision model response is invalid"
private const val SPAM_CLASS = "spam"
private const val COMMAND_CLASS = "command"
private const val HAM_CLASS = "ham"
private const val SPAM_QUESTION_TEXT = "Это спам, реклама, накрутка или мошенничество?"
private const val CLASS_QUESTION_TEXT = "Определи тип сообщения в чате с ботом."
private const val COMMAND_CRITERIA =
    "Просьба к боту выполнить действие: /start, /help, погода, напомни, " +
        "переведи, включи, статистика"
private val CLASS_CRITERIA = mapOf(
    SPAM_CLASS to "Реклама, обман, накрутка, казино, крипта, заработок, продажа баз, фишинг-ссылка",
    COMMAND_CLASS to COMMAND_CRITERIA,
    HAM_CLASS to "Обычное общение между людьми, без рекламы и без обращения к боту",
)

@ApplicationScoped
open class DecisionModelOutputAdapter(
    private val decisionModel: DecisionModel,
) : AnalyzeMessageOutputPort {

    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: AnalyzeMessageRequest): AnalyzeMessageResponse {
        val normalized = MessageTextNormalizer.normalize(request.messageText.value)
        if (normalized.isEmpty()) {
            return AnalyzeMessageResponse(
                DecisionAnalysis(
                    messageClass = MessageClass.HAM,
                    classConfidence = null,
                    classProbabilities = emptyMap(),
                    spamProbability = 0.0,
                ),
            )
        }

        val response = try {
            decisionModel.decide(buildRequest(normalized))
        } catch (e: RuntimeException) {
            throw DecisionModelException(CALL_FAILED_MESSAGE, e)
        }

        val spamProbability = try {
            response.yesNo(SPAM_QUESTION).probability()
        } catch (e: RuntimeException) {
            throw DecisionModelException(CALL_FAILED_MESSAGE, e)
        }

        val classAnswer = try {
            response.choice(CLASS_QUESTION)
        } catch (e: RuntimeException) {
            throw DecisionModelException(CALL_FAILED_MESSAGE, e)
        }

        val probabilities = classAnswer.probabilities()
        if (probabilities.keys != CLASS_CRITERIA.keys) {
            throw DecisionModelException(
                INVALID_RESPONSE_MESSAGE,
                IllegalArgumentException("unexpected class probabilities"),
            )
        }

        return AnalyzeMessageResponse(
            DecisionAnalysis(
                messageClass = toMessageClass(classAnswer.value()),
                classConfidence = classAnswer.confidence(),
                classProbabilities = probabilities.mapKeys { toMessageClass(it.key) },
                spamProbability = spamProbability,
            ),
        )
    }

    private fun buildRequest(input: String): DecisionRequest = DecisionRequest.builder()
        .input(input)
        .question(SPAM_QUESTION, YesNoQuestion.from(SPAM_QUESTION_TEXT))
        .question(CLASS_QUESTION, ChoiceQuestion.from(CLASS_QUESTION_TEXT, CLASS_CRITERIA))
        .build()

    private fun toMessageClass(value: String): MessageClass = when (value) {
        SPAM_CLASS -> MessageClass.SPAM
        COMMAND_CLASS -> MessageClass.COMMAND
        HAM_CLASS -> MessageClass.HAM
        else -> throw DecisionModelException(
            INVALID_RESPONSE_MESSAGE,
            IllegalArgumentException("unknown message class"),
        )
    }
}
