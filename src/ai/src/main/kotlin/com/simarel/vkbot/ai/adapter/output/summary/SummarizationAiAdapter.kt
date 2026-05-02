package com.simarel.vkbot.ai.adapter.output.summary

import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationResponse
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SummarizationAiAdapter(
    private val summarizationAiService: SummarizationAiService,
) : SummarizationOutputPort {

    override fun execute(request: SummarizationRequest): SummarizationResponse {
        val response = summarizationAiService.generateSummary(request.messages)
        return SummarizationResponse(
            shortSummary = response.shortSummary,
            fullSummary = response.fullSummary,
        )
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

        Верни ответ в формате JSON с двумя полями:
        - "shortSummary": краткое описание основных тем и решений в 2-3 предложения
        - "fullSummary": подробное описание обсуждения, включая кто участвовал, какие вопросы обсуждались, какие решения приняты

        Сообщения для суммаризации:
        {messages}
    """,
    )
    fun generateSummary(
        @V("messages") messages: String,
    ): SummaryResponse

    data class SummaryResponse(
        val shortSummary: String,
        val fullSummary: String,
    )
}
