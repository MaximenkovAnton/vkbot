package com.simarel.vkbot.ai.command.answer

import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.usecase.GetConversationContextUsecase
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
    private val getConversationContextUsecase: GetConversationContextUsecase,
) : GenerateAnswerCommand {

    override fun execute(
        request: GenerateAnswerCommandRequest,
    ): GenerateAnswerCommandResponse {
        val message = request.message

        // Получить контекст из persistence (20 предыдущих сообщений + профили всех участников)
        val context = getConversationContextUsecase.execute(message)

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
