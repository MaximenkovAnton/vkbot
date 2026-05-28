package com.simarel.vkbot.processor.command.isRequireAnswer

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

interface IsRequireAnswerCommand : Command<IsRequireAnswerCommand.IsRequireAnswerRequest, IsRequireAnswerCommand.IsRequireAnswerResponse> {
    data class IsRequireAnswerRequest(val message: Message) : CommandRequest
    data class IsRequireAnswerResponse(val requiresAnswer: Boolean) : CommandResponse
}

@ApplicationScoped
open class IsRequireAnswerCommandImpl(
    @ConfigProperty(name = "vk.bot.id")
    private val botId: Long,
    @ConfigProperty(name = "vk.bot.mention", defaultValue = "@simarel")
    private val botMention: String,
) : IsRequireAnswerCommand {
    override fun execute(request: IsRequireAnswerCommand.IsRequireAnswerRequest): IsRequireAnswerCommand.IsRequireAnswerResponse {
        val message = request.message
        val requiresAnswer = when {
            !message.fromId.isHuman() -> false
            !message.peerId.isGroupChat() -> true
            message.messageText.startsWith("!") || message.messageText.startsWith("\\") -> false
            message.messageText.contains(botMention) -> true
            message.forwardedMessages.lastOrNull()?.fromId?.value == botId -> true
            else -> false
        }
        return IsRequireAnswerCommand.IsRequireAnswerResponse(requiresAnswer)
    }
}
