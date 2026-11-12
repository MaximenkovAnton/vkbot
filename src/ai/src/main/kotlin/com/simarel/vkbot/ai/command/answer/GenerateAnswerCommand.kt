package com.simarel.vkbot.ai.command.answer

import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.context.ConversationContextOutputPort
import com.simarel.vkbot.ai.port.output.context.ConversationContextRequest
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
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
@Suppress("TooGenericExceptionCaught")
class GenerateAnswerCommandImpl(
    private val aiPort: GenerateAnswerOutputPort,
    private val conversationContextPort: ConversationContextOutputPort,
    private val findUserKnowledgePort: FindUserKnowledgePort,
) : GenerateAnswerCommand {

    private val logger: System.Logger = System.getLogger(GenerateAnswerCommandImpl::class.java.name)

    override fun execute(
        request: GenerateAnswerCommandRequest,
    ): GenerateAnswerCommandResponse {
        val message = request.message

        val context = conversationContextPort.execute(ConversationContextRequest(message))
        val userKnowledge = readUserKnowledge(message)

        val aiRequest = GenerateAnswerOutputPortRequest(
            currentMessage = context.currentMessage,
            chatHistory = context.chatHistory,
            userProfiles = context.userProfiles,
            groupProfiles = context.groupProfiles,
            userKnowledge = userKnowledge,
        )
        val aiResponse = aiPort.execute(aiRequest)
        return GenerateAnswerCommandResponse(MessageText.of(aiResponse.answer))
    }

    private fun readUserKnowledge(message: Message): UserKnowledge = try {
        findUserKnowledgePort.execute(FindUserKnowledgePort.Request(listOf(message.fromId)))
            .knowledge[message.fromId] ?: EMPTY_USER_KNOWLEDGE
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (e: RuntimeException) {
        logger.log(
            System.Logger.Level.WARNING,
            "User knowledge read failed for peerId=${message.peerId.value}: ${e.javaClass.simpleName}",
        )
        EMPTY_USER_KNOWLEDGE
    }

    private companion object {
        private val EMPTY_USER_KNOWLEDGE = UserKnowledge(emptyList(), null, false)
    }
}
