package com.simarel.vkbot.ai.adapter.output.ai

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped

@RegisterAiService
@SystemMessage("{config:aiUseranswerSystemprompt}")
@ApplicationScoped
interface UserAnswerAiService {

    @UserMessage(
        """
        {messageContext}
        {userMessage}
    """,
    )
    fun generateAnswer(
        @MemoryId memoryId: String,
        @V("userMessage") userMessage: String,
        @V("messageContext") messageContext: String,
    ): String
}
