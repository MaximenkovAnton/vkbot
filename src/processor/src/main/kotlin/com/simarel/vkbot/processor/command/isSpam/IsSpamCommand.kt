package com.simarel.vkbot.processor.command.isSpam

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

interface IsSpamCommand : Command<IsSpamCommand.IsSpamRequest, IsSpamCommand.IsSpamResponse> {
    data class IsSpamRequest(val spamProbability: Double) : CommandRequest
    data class IsSpamResponse(val isSpam: Boolean) : CommandResponse
}

@ApplicationScoped
open class IsSpamCommandImpl(
    @ConfigProperty(name = "decision.spam-threshold", defaultValue = "0.9")
    private val spamThreshold: Double,
) : IsSpamCommand {
    override fun execute(request: IsSpamCommand.IsSpamRequest): IsSpamCommand.IsSpamResponse {
        val isSpam = request.spamProbability >= spamThreshold
        return IsSpamCommand.IsSpamResponse(isSpam)
    }
}
