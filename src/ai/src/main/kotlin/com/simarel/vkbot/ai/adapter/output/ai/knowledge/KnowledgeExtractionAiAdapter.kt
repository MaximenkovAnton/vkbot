package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.domain.exception.KnowledgeExtractionException
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionPort
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionRequest
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext

private const val MAX_ATTEMPTS = 3
private const val EMPTY_RESPONSE_MESSAGE = "Empty knowledge extraction response"
private const val NO_FACTS_MESSAGE = "Knowledge extraction returned no facts field"
private const val ATTEMPT_FAILED_MESSAGE = "Knowledge extraction attempt failed"
private const val CODE_FENCE = "```"

@ApplicationScoped
class KnowledgeExtractionAiAdapter(
    private val knowledgeFactExtractor: KnowledgeFactExtractor,
    private val objectMapper: ObjectMapper,
) : KnowledgeExtractionPort {

    private val logger: System.Logger = System.getLogger(KnowledgeExtractionAiAdapter::class.java.name)

    @ActivateRequestContext
    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: KnowledgeExtractionRequest): KnowledgeExtractionResponse {
        var lastException: Exception? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val failure: Exception = try {
                val text = knowledgeFactExtractor.extract(request.messages.joinToString("\n"))
                if (text.isBlank()) {
                    KnowledgeExtractionException(EMPTY_RESPONSE_MESSAGE, null)
                } else {
                    val parsed = objectMapper.readValue(
                        stripCodeFence(text.trim()),
                        KnowledgeExtractionResponse::class.java,
                    )
                    val facts = parsed.facts ?: throw KnowledgeExtractionException(NO_FACTS_MESSAGE, null)
                    return KnowledgeExtractionResponse(facts)
                }
            } catch (e: KnowledgeExtractionException) {
                e
            } catch (e: Exception) {
                KnowledgeExtractionException(ATTEMPT_FAILED_MESSAGE, e)
            }
            lastException = failure
            logger.log(
                System.Logger.Level.WARNING,
                "Knowledge extraction attempt $attempt failed: ${failure.javaClass.simpleName}",
            )
        }

        throw KnowledgeExtractionException(
            "Failed to extract knowledge after $MAX_ATTEMPTS attempts",
            lastException,
        )
    }

    private fun stripCodeFence(text: String): String {
        if (!text.startsWith(CODE_FENCE)) {
            return text
        }
        return text.substringAfter('\n', "")
            .trimEnd()
            .removeSuffix(CODE_FENCE)
            .trim()
    }
}
