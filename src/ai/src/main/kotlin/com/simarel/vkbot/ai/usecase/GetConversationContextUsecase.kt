package com.simarel.vkbot.ai.usecase

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.output.ai.ChatMessage
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.persistence.PersistenceDataOutputPort
import jakarta.enterprise.context.ApplicationScoped

data class ConversationContext(
    val currentMessage: Message,
    val chatHistory: List<ChatMessage>,
    val userProfiles: Map<FromId, VkUserProfile>,
    val groupProfiles: Map<FromId, VkGroupProfile>,
)

@ApplicationScoped
class GetConversationContextUsecase(
    private val persistencePort: PersistenceDataOutputPort,
    private val objectMapper: ObjectMapper,
) {

    fun execute(currentMessage: Message): ConversationContext {
        val peerId = currentMessage.peerId
        val currentMessageId = currentMessage.conversationMessageId

        // 1. Получить предыдущие 20 сообщений (до текущего) через REST API
        val previousMessages = persistencePort.findMessagesBefore(
            peerId = peerId.value,
            beforeConversationMessageId = currentMessageId.value,
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
        val (groupFromIds, userIds) = allFromIds.partition { it.value < 0 }

        // 6. Получить профили через REST API
        val userProfiles = persistencePort.findUserProfilesByIds(userIds)
        val groupProfiles = persistencePort.findGroupProfilesByIds(groupFromIds)

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

    private fun extractAllFromIds(entity: StoredMessage): List<FromId> {
        val result = mutableListOf(FromId.of(entity.fromId))
        entity.forwardedMessages?.let { json ->
            val forwarded = parseForwardedMessages(json)
            forwarded.forEach { msg ->
                result.addAll(extractAllFromIds(msg))
            }
        }
        return result
    }

    private fun parseForwardedMessages(json: String): List<Message> {
        val node = objectMapper.readTree(json)
        return if (node.isArray) {
            objectMapper.convertValue(node, object : TypeReference<List<Message>>() {})
        } else {
            listOf(objectMapper.convertValue(node, Message::class.java))
        }
    }

    private fun StoredMessage.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = ConversationMessageId.of(this.conversationMessageId),
            fromId = FromId.of(this.fromId),
            text = MessageText.of(this.messageText ?: ""),
            date = Date.of(this.date),
            forwardedMessages = this.forwardedMessages?.let { parseForwardedMessages(it) } ?: emptyList()
        )
    }
}
