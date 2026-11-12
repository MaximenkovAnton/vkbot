package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.event.UserKnowledgeSummaryRequestedPayload

interface GenerateUserKnowledgeSummaryCommand :
    Command<
        GenerateUserKnowledgeSummaryCommand.Request,
        GenerateUserKnowledgeSummaryCommand.Response,
        > {
    data class Request(val payload: UserKnowledgeSummaryRequestedPayload) : CommandRequest
    object Response : CommandResponse
}
