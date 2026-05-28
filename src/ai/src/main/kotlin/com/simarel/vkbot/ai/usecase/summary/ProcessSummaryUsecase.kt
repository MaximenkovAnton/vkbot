package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.command.summary.ProcessSummaryCommand
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessSummaryUsecase(
    private val processSummaryCommand: ProcessSummaryCommand,
) : ProcessSummaryInputPort {

    override fun execute(request: ProcessSummaryInputPortRequest): ProcessSummaryInputPortResponse {
        processSummaryCommand.execute(
            ProcessSummaryCommand.ProcessSummaryRequest(request.peerId, request.conversationMessageId)
        )
        return ProcessSummaryInputPortResponse
    }
}
