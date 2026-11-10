package com.simarel.vkbot.ai.command.profile

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message

interface ProcessProfileCommand :
    Command<
        ProcessProfileCommand.ProcessProfileRequest,
        ProcessProfileCommand.ProcessProfileResponse,
        > {
    data class ProcessProfileRequest(
        val message: Message,
    ) : CommandRequest

    object ProcessProfileResponse : CommandResponse
}
