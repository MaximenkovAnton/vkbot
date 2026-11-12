package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.event.UserKnowledgeRequestedPayload

interface ProcessUserKnowledgeCommand :
    Command<
        ProcessUserKnowledgeCommand.Request,
        ProcessUserKnowledgeCommand.Response,
        > {
    data class Request(val payload: UserKnowledgeRequestedPayload) : CommandRequest
    object Response : CommandResponse
}
