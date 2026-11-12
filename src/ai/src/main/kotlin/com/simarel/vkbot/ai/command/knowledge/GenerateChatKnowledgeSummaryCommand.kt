package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.event.ChatKnowledgeSummaryRequestedPayload

interface GenerateChatKnowledgeSummaryCommand :
    Command<
        GenerateChatKnowledgeSummaryCommand.Request,
        GenerateChatKnowledgeSummaryCommand.Response,
        > {
    data class Request(val payload: ChatKnowledgeSummaryRequestedPayload) : CommandRequest
    object Response : CommandResponse
}
