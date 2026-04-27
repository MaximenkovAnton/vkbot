package com.simarel.vkbot.ai.usecase

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.output.ai.ChatMessage
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBeforePort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime

data class ConversationContext(
    val currentMessage: Message,
    val chatHistory: List<ChatMessage>,
    val userProfiles: Map<FromId, VkUserProfile>,
    val groupProfiles: Map<FromId, VkGroupProfile>,
)

@ApplicationScoped
class GetConversationContextUsecase(
    private val findMessagesPort: FindMessagesBeforePort,
    private val findUserProfilesPort: FindUserProfilesByIdsPort,
    private val findGroupProfilesPort: FindGroupProfilesByIdsPort,
    private val objectMapper: ObjectMapper,
) {
    fun execute(currentMessage: Message): ConversationContext {
        val peerId = currentMessage.peerId
        val currentMessageId = currentMessage.conversationMessageId

        // 1. Получить предыдущие 20 сообщений (до текущего)
        val previousMessages = findMessagesPort.findMessagesBefore(
            peerId = peerId,
            beforeConversationMessageId = currentMessageId,
            limit = 20
        )

        // 2. Собрать все fromId из истории (с включением forwarded)
        val historyFromIds = previousMessages.flatMap { msg ->
            extractAllFromIds(msg)
        }

        // 3. Собрать все fromId из текущего сообщения (включая все forwarded)
        val currentMessageFromIds = extractAllFromIds(currentMessage)

        // 4. Объединить все ID
        val allFromIds = (historyFromIds + currentMessageFromIds).distinct()

        // 5. Разделить на группы (ID < 0) и пользователей (ID > 0)
        // FromId для групп < 0, но GroupId в БД хранятся > 0
        val (groupFromIds, userIds) = allFromIds.partition { it.value < 0 }

        // 6. Получить профили
        val userProfiles = findUserProfilesPort.findByIds(userIds)
        val groupProfiles = findGroupProfilesPort.findByIds(groupFromIds)

        return ConversationContext(
            currentMessage = currentMessage,
            chatHistory = previousMessages.map { it.toChatMessage() },
            userProfiles = userProfiles.associateBy { it.id },
            groupProfiles = groupProfiles.associateBy { it.id },
        )
    }

    private fun extractAllFromIds(message: Message): List<FromId> {
        val result = mutableListOf(message.fromId)
        message.forwardedMessages.forEach { forwarded ->
            result.addAll(extractAllFromIds(forwarded))
        }
        return result
    }

    private fun extractAllFromIds(entity: MessageEntity): List<FromId> {
        val result = mutableListOf(FromId.of(entity.fromId!!))
        entity.forwardedMessages?.let { json ->
            val forwarded = parseForwardedMessages(json)
            forwarded.forEach { msg ->
                result.addAll(extractAllFromIds(msg))
            }
        }
        return result
    }

    private fun parseForwardedMessages(json: String): List<Message> {
        return objectMapper.readValue(json, object : TypeReference<List<Message>>() {})
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = ConversationMessageId.of(this.conversationMessageId!!),
            fromId = FromId.of(this.fromId!!),
            text = MessageText.of(this.messageText!!),
            date = Date.of(this.date),
            forwardedMessages = this.forwardedMessages?.let { parseForwardedMessages(it) } ?: emptyList()
        )
    }
}
