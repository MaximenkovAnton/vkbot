package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import com.simarel.vkbot.ai.domain.exception.UserKnowledgeSummaryException
import com.simarel.vkbot.ai.port.output.knowledge.UserKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext

private const val MAX_ATTEMPTS = 3
private const val EMPTY_RESPONSE_MESSAGE = "Empty user knowledge summary response"
private const val ATTEMPT_FAILED_MESSAGE = "User knowledge summary generation failed"

@ApplicationScoped
class UserKnowledgeSummaryAiAdapter(
    private val userKnowledgeSummarizer: UserKnowledgeSummarizer,
) : UserKnowledgeSummaryPort {

    private val logger: System.Logger = System.getLogger(UserKnowledgeSummaryAiAdapter::class.java.name)

    @ActivateRequestContext
    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: UserKnowledgeSummaryPort.Request): UserKnowledgeSummaryPort.Response {
        var lastException: Exception? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val failure: Exception = try {
                val text = userKnowledgeSummarizer.summarize(request.facts)
                if (text.isBlank()) {
                    UserKnowledgeSummaryException(EMPTY_RESPONSE_MESSAGE, null)
                } else {
                    return UserKnowledgeSummaryPort.Response(text)
                }
            } catch (e: UserKnowledgeSummaryException) {
                e
            } catch (e: Exception) {
                UserKnowledgeSummaryException(ATTEMPT_FAILED_MESSAGE, e)
            }
            lastException = failure
            logger.log(
                System.Logger.Level.WARNING,
                "User knowledge summary attempt $attempt failed: ${failure.javaClass.simpleName}",
            )
        }

        throw UserKnowledgeSummaryException(
            "Failed to generate user knowledge summary after $MAX_ATTEMPTS attempts",
            lastException,
        )
    }
}
