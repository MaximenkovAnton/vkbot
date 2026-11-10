package com.simarel.vkbot.processor.command.resolveIntent

import com.simarel.vkbot.processor.domain.decision.MessageIntent
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import jakarta.enterprise.context.ApplicationScoped

interface ResolveMessageIntentCommand :
    Command<
        ResolveMessageIntentCommand.ResolveMessageIntentRequest,
        ResolveMessageIntentCommand.ResolveMessageIntentResponse,
        > {
    data class ResolveMessageIntentRequest(val messageText: MessageText) : CommandRequest
    data class ResolveMessageIntentResponse(val intent: MessageIntent?) : CommandResponse
}

@ApplicationScoped
open class ResolveMessageIntentCommandImpl : ResolveMessageIntentCommand {
    override fun execute(
        request: ResolveMessageIntentCommand.ResolveMessageIntentRequest,
    ): ResolveMessageIntentCommand.ResolveMessageIntentResponse {
        val normalizedText = request.messageText.value.trim().lowercase()
        val intent = when {
            normalizedText in HELP_COMMANDS -> MessageIntent.HELP
            HELP_COMMANDS.any { normalizedText.startsWith(it) } -> MessageIntent.GENERAL
            else -> null
        }
        return ResolveMessageIntentCommand.ResolveMessageIntentResponse(intent)
    }

    private companion object {
        val HELP_COMMANDS = listOf("/help", "/помощь")
    }
}
