package com.simarel.vkbot.ai.command.answer

import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.service.ConversationContextService
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.MessageText
import jakarta.enterprise.context.ApplicationScoped

interface GenerateAnswerCommand :
    Command<
            GenerateAnswerCommandRequest,
            GenerateAnswerCommandResponse,
            >

@JvmInline
value class GenerateAnswerCommandRequest(val message: Message) : CommandRequest

@JvmInline
value class GenerateAnswerCommandResponse(val responseText: MessageText) : CommandResponse

@ApplicationScoped
class GenerateAnswerCommandImpl(
    private val aiPort: GenerateAnswerOutputPort,
    private val conversationContextService: ConversationContextService,
) : GenerateAnswerCommand {

    override fun execute(
        request: GenerateAnswerCommandRequest,
    ): GenerateAnswerCommandResponse {
        val message = request.message

        val context = conversationContextService.getContext(message)

        val aiRequest = GenerateAnswerOutputPortRequest(
            currentMessage = context.currentMessage,
            chatHistory = context.chatHistory,
            userProfiles = context.userProfiles,
            groupProfiles = context.groupProfiles,
        )
        val aiResponse = aiPort.execute(aiRequest)
        return GenerateAnswerCommandResponse(MessageText.of(aiResponse.answer))
    }
}
