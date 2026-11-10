package com.simarel.vkbot.ai.adapter.output.ai.news

import com.simarel.vkbot.ai.domain.exception.NewsDigestGenerationException
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationPort
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationRequest
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext

private const val MAX_RETRIES = 3
private const val EMPTY_RESPONSE_MESSAGE = "Empty news digest response"

@ApplicationScoped
class NewsDigestAiAdapter(
    private val newsDigestAiService: NewsDigestAiService,
) : NewsDigestGenerationPort {

    private val logger = System.getLogger(NewsDigestAiAdapter::class.java.name)

    @ActivateRequestContext
    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: NewsDigestGenerationRequest): NewsDigestGenerationResponse {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            val failure: Exception = try {
                val text = newsDigestAiService.generateDigest(request.data)
                if (text.isNotBlank()) {
                    return NewsDigestGenerationResponse(text)
                }
                NewsDigestGenerationException(EMPTY_RESPONSE_MESSAGE, null)
            } catch (e: Exception) {
                e
            }
            lastException = failure
            logger.log(
                System.Logger.Level.WARNING,
                "News digest attempt $attempt failed: ${failure.javaClass.simpleName}",
            )
        }

        throw NewsDigestGenerationException(
            "Failed to generate news digest after $MAX_RETRIES attempts",
            lastException,
        )
    }
}
