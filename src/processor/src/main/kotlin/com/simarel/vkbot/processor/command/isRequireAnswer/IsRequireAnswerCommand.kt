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
        if (!message.fromId.isGroupChat()) {
            return true // direct message to bot
        }
        if (message.messageText.startsWith("!") || message.messageText.startsWith("\\")) {
            return false // command for controlling bot
        }
        if (!message.fromId.isHuman()) {
            return false // not a human
        }
        if (message.messageText.contains(botMention)) {
            return true // direct call to bot
        }
        // Check the last forwarded message (reply_to if present, otherwise last fwd_message)
        if (message.forwardedMessages.lastOrNull()?.fromId?.value == -botId) {
            return true // reply to bot's message or forwarded bot's message with comment
        }
        return false
    }
}
