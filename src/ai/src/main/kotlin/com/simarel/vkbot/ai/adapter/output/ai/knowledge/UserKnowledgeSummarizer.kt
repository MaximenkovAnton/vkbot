package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiKnowledgeSummarySystemprompt}")
interface UserKnowledgeSummarizer {

    @UserMessage("{facts}")
    fun summarize(@V("facts") facts: String): String
}
