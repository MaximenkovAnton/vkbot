package com.simarel.vkbot.processor.adapter.output.ai

import com.simarel.vkbot.share.domain.model.Message
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped

@RegisterAiService
@SystemMessage(
    """
    You are a helpful ai chatbot in a chat. Your task is to answer user questions in Russian.
    Use the context of forwarded messages if provided.
    The format of forwarded messages will be: [sender]: [message]
""",
)
@ApplicationScoped
interface UserAnswerAiService {
    @UserMessage(
        """
        {messageContext}
        User message: {userMessage}
    """,
    )
    fun answerUser(
        @V("userMessage") userMessage: String,
        @V("messageContext") messageContext: String,
    ): String

    // Default implementation that extracts context from message object
    fun answerUser(message: Message): String {
        val context = extractMessageContext(message)
        return answerUser(message.messageText.value, context)
    }

    private fun extractMessageContext(message: Message): String {
        if (message.forwardedMessages.isEmpty()) {
            return ""
        }

        val context = StringBuilder()
        context.appendLine("[Пересланные сообщения]")
        context.appendLine()

        fun addForwarded(msg: Message, level: Int = 0) {
            val prefix = "  ".repeat(level)
            context.appendLine("$prefix[${msg.fromId.value}]: ${msg.messageText.value}")

            msg.forwardedMessages.forEach { addForwarded(it, level + 1) }
        }

        message.forwardedMessages.forEach { addForwarded(it) }

        context.appendLine()
        context.appendLine("[Текущее сообщение]")

        return context.toString()
    }
}
