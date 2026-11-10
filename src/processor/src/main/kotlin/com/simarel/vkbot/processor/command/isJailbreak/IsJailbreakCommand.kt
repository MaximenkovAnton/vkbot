package com.simarel.vkbot.processor.command.isJailbreak

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

interface IsJailbreakCommand : Command<IsJailbreakCommand.IsJailbreakRequest, IsJailbreakCommand.IsJailbreakResponse> {
    data class IsJailbreakRequest(val jailbreakProbability: Double) : CommandRequest
    data class IsJailbreakResponse(val isJailbreak: Boolean) : CommandResponse
}

@ApplicationScoped
open class IsJailbreakCommandImpl(
    @ConfigProperty(name = "decision.jailbreak-threshold", defaultValue = "0.9")
    private val jailbreakThreshold: Double,
) : IsJailbreakCommand {
    override fun execute(request: IsJailbreakCommand.IsJailbreakRequest): IsJailbreakCommand.IsJailbreakResponse {
        val isJailbreak = request.jailbreakProbability >= jailbreakThreshold
        return IsJailbreakCommand.IsJailbreakResponse(isJailbreak)
    }
}
