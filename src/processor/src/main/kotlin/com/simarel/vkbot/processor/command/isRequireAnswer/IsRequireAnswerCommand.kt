package com.simarel.vkbot.processor.command.isRequireAnswer

import com.simarel.vkbot.share.domain.model.Message
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
open class IsRequireAnswerCommand(
    @ConfigProperty(name = "vk.bot.id")
    private val botId: Long,
    @ConfigProperty(name = "vk.bot.mention", defaultValue = "@simarel")
    private val botMention: String,
) {
    open fun execute(message: Message): Boolean {
        if (!message.peerId.isGroupChat()) {
            return true
        }
        if (message.messageText.startsWith("!") || message.messageText.startsWith("\\")) {
            return false
        }
        if (!message.fromId.isHuman()) {
            return false
        }
        if (message.messageText.contains(botMention)) {
            return true
        }
        if (message.forwardedMessages.lastOrNull()?.fromId?.value == botId) {
            return true
        }
        return false
    }
}
