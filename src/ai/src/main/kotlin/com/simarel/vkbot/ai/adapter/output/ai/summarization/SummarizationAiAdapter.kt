package com.simarel.vkbot.ai.adapter.output.ai.summarization

import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationResponse
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext

private const val MAX_RETRIES = 3

@ApplicationScoped
class SummarizationAiAdapter(
    private val summarizationAiService: SummarizationAiService,
) : SummarizationOutputPort {

    @ActivateRequestContext
    override fun execute(request: SummarizationRequest): SummarizationResponse {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val response = summarizationAiService.generateSummary(request.messages)
                Log.debugf("Successfully generated summary on attempt %d", attempt)
                return SummarizationResponse(
                    shortSummary = response.shortSummary,
                    fullSummary = response.fullSummary,
                )
            } catch (e: Exception) {
                lastException = e
                Log.warnf("Failed to generate summary on attempt %d: %s", attempt, e.message)
                if (attempt < MAX_RETRIES) {
                    Log.debug("Retrying summary generation...")
                }
            }
        }

        Log.errorf("Failed to generate summary after %d attempts", MAX_RETRIES)
        throw lastException
            ?: IllegalStateException("Failed to generate summary after $MAX_RETRIES attempts")
    }
}
