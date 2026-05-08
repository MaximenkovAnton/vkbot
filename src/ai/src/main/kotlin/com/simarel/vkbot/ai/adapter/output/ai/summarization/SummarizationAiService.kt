package com.simarel.vkbot.ai.adapter.output.ai.summarization

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class
)
@SystemMessage("{config:aiSumarizationSystemprompt}")
interface SummarizationAiService {

    @UserMessage(
        """        
        Write a summary of the text delimited by ---

        ---
        {messages}
        ---

        Верни ответ СТРОГО в формате JSON с полями:
        {
          "shortSummary": "краткое описание основных тем и решений в нескольких предложениях",
          "fullSummary": "подробное описание обсуждения"
        }
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
