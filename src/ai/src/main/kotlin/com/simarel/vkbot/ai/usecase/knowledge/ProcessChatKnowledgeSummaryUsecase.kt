package com.simarel.vkbot.ai.usecase.knowledge

import com.simarel.vkbot.ai.command.knowledge.GenerateChatKnowledgeSummaryCommand
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessChatKnowledgeSummaryUsecase(
    private val generateChatKnowledgeSummaryCommand: GenerateChatKnowledgeSummaryCommand,
) : ProcessChatKnowledgeSummaryInputPort {

    override fun execute(
        request: ProcessChatKnowledgeSummaryInputPortRequest,
    ): ProcessChatKnowledgeSummaryInputPortResponse {
        generateChatKnowledgeSummaryCommand.execute(
            GenerateChatKnowledgeSummaryCommand.Request(request.payload),
        )
        return ProcessChatKnowledgeSummaryInputPortResponse
    }
}
