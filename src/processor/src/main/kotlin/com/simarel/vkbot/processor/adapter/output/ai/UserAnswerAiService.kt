package com.simarel.vkbot.processor.adapter.output.ai

import com.simarel.vkbot.share.domain.model.Message
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
    fun answerUser(
        @MemoryId memoryId: String,
        @V("userMessage") userMessage: String,
        @V("messageContext") messageContext: String,
    ): String

    fun answerUser(message: Message): String {
        return answerUser(
            memoryId = message.peerId.value.toString(),
            userMessage = message.messageText.value,
            messageContext = message.forwardedContextString()
        )
    }

}
