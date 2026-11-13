package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import com.simarel.vkbot.ai.domain.exception.ChatKnowledgeSummaryException
import com.simarel.vkbot.ai.port.output.knowledge.ChatKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext

private const val MAX_ATTEMPTS = 3
private const val EMPTY_RESPONSE_MESSAGE = "Empty chat knowledge summary response"
private const val ATTEMPT_FAILED_MESSAGE = "Chat knowledge summary generation failed"

@ApplicationScoped
class ChatKnowledgeSummaryAiAdapter(
    private val chatKnowledgeSummarizer: ChatKnowledgeSummarizer,
) : ChatKnowledgeSummaryPort {

    private val logger: System.Logger = System.getLogger(ChatKnowledgeSummaryAiAdapter::class.java.name)

    @ActivateRequestContext
    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: ChatKnowledgeSummaryPort.Request): ChatKnowledgeSummaryPort.Response {
        var lastException: Exception? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val failure: Exception = try {
                val text = chatKnowledgeSummarizer.summarize(request.messages.joinToString("\n"))
                if (text.isBlank()) {
                    ChatKnowledgeSummaryException(EMPTY_RESPONSE_MESSAGE, null)
                } else {
                    return ChatKnowledgeSummaryPort.Response(text)
                }
            } catch (e: ChatKnowledgeSummaryException) {
                e
            } catch (e: Exception) {
                ChatKnowledgeSummaryException(ATTEMPT_FAILED_MESSAGE, e)
            }
            lastException = failure
            logger.log(
                System.Logger.Level.WARNING,
                "Chat knowledge summary attempt $attempt failed: ${failure.javaClass.simpleName}",
            )
        }

        throw ChatKnowledgeSummaryException(
            "Failed to generate chat knowledge summary after $MAX_ATTEMPTS attempts",
            lastException,
        )
    }
}
