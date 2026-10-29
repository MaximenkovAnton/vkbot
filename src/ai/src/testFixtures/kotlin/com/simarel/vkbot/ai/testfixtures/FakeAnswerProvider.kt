package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommandRequest
import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommandResponse
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortResponse
import com.simarel.vkbot.ai.port.output.ai.ChatMessage
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortResponse
import com.simarel.vkbot.ai.port.output.context.ConversationContextRequest
import com.simarel.vkbot.ai.port.output.context.ConversationContextResponse
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.time.OffsetDateTime

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

object FakeAnswerProvider {

    fun createChatMessage(
        id: Long = 20,
        fromId: Long = 30,
        text: String = "chat message",
        date: OffsetDateTime = FIXED_DATE,
        forwardedMessages: List<Message> = emptyList(),
    ) = ChatMessage(
        id = ConversationMessageId.of(id),
        fromId = FromId.of(fromId),
        text = MessageText.of(text),
        date = Date.of(date),
        forwardedMessages = forwardedMessages,
    )

    fun createGenerateAnswerOutputPortRequest(
        currentMessage: Message = FakeVoProvider.createMessage(),
        chatHistory: List<ChatMessage> = listOf(createChatMessage()),
        userProfiles: Map<FromId, VkUserProfile> = emptyMap(),
        groupProfiles: Map<FromId, VkGroupProfile> = emptyMap(),
    ) = GenerateAnswerOutputPortRequest(
        currentMessage = currentMessage,
        chatHistory = chatHistory,
        userProfiles = userProfiles,
        groupProfiles = groupProfiles,
    )

    fun createGenerateAnswerOutputPortResponse(
        answer: String = "generated answer",
    ) = GenerateAnswerOutputPortResponse(answer)

    fun createConversationContextRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = ConversationContextRequest(message)

    fun createConversationContextResponse(
        currentMessage: Message = FakeVoProvider.createMessage(),
        chatHistory: List<ChatMessage> = listOf(createChatMessage()),
        userProfiles: Map<FromId, VkUserProfile> = emptyMap(),
        groupProfiles: Map<FromId, VkGroupProfile> = emptyMap(),
    ) = ConversationContextResponse(
        currentMessage = currentMessage,
        chatHistory = chatHistory,
        userProfiles = userProfiles,
        groupProfiles = groupProfiles,
    )

    fun createGenerateAnswerCommandRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = GenerateAnswerCommandRequest(message)

    fun createGenerateAnswerCommandResponse(
        responseText: MessageText = FakeVoProvider.createMessageText("generated answer"),
    ) = GenerateAnswerCommandResponse(responseText)

    fun createMessageRequireAnswerInputPortRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = MessageRequireAnswerInputPortRequest(message)

    fun createMessageRequireAnswerInputPortResponse(
        value: String = "ok",
    ) = MessageRequireAnswerInputPortResponse(value)
}
