package com.simarel.vkbot.ai.usecase.knowledge

import com.simarel.vkbot.ai.command.knowledge.ProcessUserKnowledgeCommand
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessUserKnowledgeUsecase(
    private val processUserKnowledgeCommand: ProcessUserKnowledgeCommand,
) : ProcessUserKnowledgeInputPort {

    override fun execute(request: ProcessUserKnowledgeInputPortRequest): ProcessUserKnowledgeInputPortResponse {
        processUserKnowledgeCommand.execute(ProcessUserKnowledgeCommand.Request(request.payload))
        return ProcessUserKnowledgeInputPortResponse
    }
}
