package com.simarel.vkbot.ai.adapter.output.summary

import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationResponse
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
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

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class
)
@SystemMessage("{config:aiSumarizationSystemprompt}")
interface SummarizationAiService {

    @UserMessage(
        """
        Создай суммаризацию следующего диалога.

        Верни ответ СТРОГО в формате JSON с двумя полями:
        {
          "shortSummary": "краткое описание основных тем и решений в 2-3 предложения",
          "fullSummary": "подробное описание обсуждения, включая кто участвовал, какие вопросы обсуждались, какие решения приняты"
        }

        Поля:
        - shortSummary: краткое описание основных тем и решений в 2-3 предложения
        - fullSummary: подробное описание обсуждения, включая кто участвовал, какие вопросы обсуждались, какие решения приняты

        Отвечай ТОЛЬКО валидным JSON. Никакого другого текста, markdown разметки или объяснений.

        Сообщения для суммаризации:
        {messages}
    """,
    )
    fun generateSummary(
        @V("messages") messages: String,
    ): SummaryResponse
}

data class SummaryResponse(
    val shortSummary: String,
    val fullSummary: String,
)
