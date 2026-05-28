package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.command.summary.GenerateSummaryCommand
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateSummaryUsecase(
    private val generateSummaryCommand: GenerateSummaryCommand,
) : GenerateSummaryInputPort {

    override fun execute(request: GenerateSummaryInputPortRequest): GenerateSummaryInputPortResponse {
        val result = generateSummaryCommand.execute(
            GenerateSummaryCommand.GenerateSummaryRequest(
                peerId = request.peerId,
                firstMessageId = request.firstMessageId,
                lastMessageId = request.lastMessageId,
            )
        )
        return GenerateSummaryInputPortResponse(result.summaryId)
    }
}
