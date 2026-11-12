package com.simarel.vkbot.ai.usecase.knowledge

import com.simarel.vkbot.ai.command.knowledge.GenerateUserKnowledgeSummaryCommand
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessUserKnowledgeSummaryUsecase(
    private val generateUserKnowledgeSummaryCommand: GenerateUserKnowledgeSummaryCommand,
) : ProcessUserKnowledgeSummaryInputPort {

    override fun execute(
        request: ProcessUserKnowledgeSummaryInputPortRequest,
    ): ProcessUserKnowledgeSummaryInputPortResponse {
        generateUserKnowledgeSummaryCommand.execute(
            GenerateUserKnowledgeSummaryCommand.Request(request.payload),
        )
        return ProcessUserKnowledgeSummaryInputPortResponse
    }
}
