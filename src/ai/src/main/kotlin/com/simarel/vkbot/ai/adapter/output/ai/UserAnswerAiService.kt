package com.simarel.vkbot.ai.adapter.output.ai

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class
)
@SystemMessage("{config:aiUseranswerSystemprompt}")
@ApplicationScoped
interface UserAnswerAiService {

    @UserMessage(
        """
        <профили_участников>
        {userProfiles}
        {groupProfiles}
        </профили_участников>

        <история_чата>
        {chatHistory}
        </история_чата>

        <текущее_сообщение>
        {currentMessage}
        </текущее_сообщение>
    """,
    )
    fun generateAnswer(
        @V("userProfiles") userProfiles: String,
        @V("groupProfiles") groupProfiles: String,
        @V("chatHistory") chatHistory: String,
        @V("currentMessage") currentMessage: String,
    ): String
}
