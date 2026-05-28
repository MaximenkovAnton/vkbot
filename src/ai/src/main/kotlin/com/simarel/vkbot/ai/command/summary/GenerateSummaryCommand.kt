package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import java.util.UUID

interface GenerateSummaryCommand : Command<GenerateSummaryCommand.GenerateSummaryRequest, GenerateSummaryCommand.GenerateSummaryResponse> {
    data class GenerateSummaryRequest(
        val peerId: Long,
        val firstMessageId: Long,
        val lastMessageId: Long,
    ) : CommandRequest

    data class GenerateSummaryResponse(
        val summaryId: UUID,
    ) : CommandResponse
}
