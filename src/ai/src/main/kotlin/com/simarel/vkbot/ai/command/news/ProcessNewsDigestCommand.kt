package com.simarel.vkbot.ai.command.news

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message

interface ProcessNewsDigestCommand :
    Command<
        ProcessNewsDigestCommand.ProcessNewsDigestRequest,
        ProcessNewsDigestCommand.ProcessNewsDigestResponse,
        > {
    data class ProcessNewsDigestRequest(
        val message: Message,
    ) : CommandRequest

    object ProcessNewsDigestResponse : CommandResponse
}
