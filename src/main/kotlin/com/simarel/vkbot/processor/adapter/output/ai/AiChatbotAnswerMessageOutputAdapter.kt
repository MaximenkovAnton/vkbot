package com.simarel.vkbot.processor.adapter.output.ai

import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AiChatbotAnswerMessageOutputAdapter(
    val userAnswerAiService: UserAnswerAiService,
) : AiChatbotAnswerMessageOutputPort {
    companion object {
        private const val MAX_FORWARD_DEPTH = 5
    }

    override fun execute(request: AiChatbotAnswerMessageOutputPortRequest): AiChatbotAnswerMessageOutputPortResponse {
        val context = extractMessageContext(request.message)
        val answer = userAnswerAiService.answerUser(request.message.messageText.value, context)
        return AiChatbotAnswerMessageOutputPortResponse(MessageText.of(answer))
    }

    private fun extractMessageContext(message: com.simarel.vkbot.share.domain.model.Message): String {
        if (message.forwardedMessages.isEmpty()) {
            return ""
        }

        val context = StringBuilder()
        context.appendLine("[Пересланные сообщения]")
        context.appendLine()

        fun addForwarded(msg: com.simarel.vkbot.share.domain.model.Message, level: Int = 0) {
            if (level > MAX_FORWARD_DEPTH) return // Ограничение глубины рекурсии

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
