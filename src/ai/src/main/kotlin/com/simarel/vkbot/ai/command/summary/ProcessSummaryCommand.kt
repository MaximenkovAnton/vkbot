package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse

interface ProcessSummaryCommand : Command<ProcessSummaryCommand.ProcessSummaryRequest, ProcessSummaryCommand.ProcessSummaryResponse> {
    data class ProcessSummaryRequest(
        val peerId: Long,
        val conversationMessageId: Long,
    ) : CommandRequest

    object ProcessSummaryResponse : CommandResponse
}
