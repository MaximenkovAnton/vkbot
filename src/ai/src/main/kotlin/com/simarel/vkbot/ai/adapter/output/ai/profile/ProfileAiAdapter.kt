package com.simarel.vkbot.ai.adapter.output.ai.profile

import com.simarel.vkbot.ai.domain.exception.ProfileGenerationException
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationPort
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationRequest
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext

private const val MAX_RETRIES = 3
private const val EMPTY_RESPONSE_MESSAGE = "Empty profile answer response"

@ApplicationScoped
class ProfileAiAdapter(
    private val profileAiService: ProfileAiService,
) : ProfileAnswerGenerationPort {

    private val logger = System.getLogger(ProfileAiAdapter::class.java.name)

    @ActivateRequestContext
    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: ProfileAnswerGenerationRequest): ProfileAnswerGenerationResponse {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            val failure: Exception = try {
                val text = profileAiService.answer(request.question, request.data)
                if (text.isNotBlank()) {
                    return ProfileAnswerGenerationResponse(text)
                }
                ProfileGenerationException(EMPTY_RESPONSE_MESSAGE, null)
            } catch (e: Exception) {
                e
            }
            lastException = failure
            logger.log(
                System.Logger.Level.WARNING,
                "Profile answer attempt $attempt failed: ${failure.javaClass.simpleName}",
            )
        }

        throw ProfileGenerationException(
            "Failed to generate profile answer after $MAX_RETRIES attempts",
            lastException,
        )
    }
}
