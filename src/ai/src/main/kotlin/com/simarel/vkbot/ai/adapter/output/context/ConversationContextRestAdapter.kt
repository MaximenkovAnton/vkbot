package com.simarel.vkbot.ai.adapter.output.context

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.output.ai.ChatMessage
import com.simarel.vkbot.ai.port.output.context.ConversationContextOutputPort
import com.simarel.vkbot.ai.port.output.context.ConversationContextRequest
import com.simarel.vkbot.ai.port.output.context.ConversationContextResponse
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

@ApplicationScoped
class ConversationContextRestAdapter(
    private val persistencePort: PersistenceDataOutputPort,
    private val objectMapper: ObjectMapper,
) : ConversationContextOutputPort {

    override fun execute(request: ConversationContextRequest): ConversationContextResponse {
        val currentMessage = request.message
        val peerId = currentMessage.peerId
        val currentMessageId = currentMessage.conversationMessageId

        val previousMessages = persistencePort.findMessagesBefore(
            peerId = peerId.value,
            beforeConversationMessageId = currentMessageId.value,
            limit = 20
        )

        val historyFromIds = previousMessages.flatMap { msg ->
            extractAllFromIds(msg)
        }

        val currentMessageFromIds = extractAllFromIds(currentMessage)

        val allFromIds = (historyFromIds + currentMessageFromIds).distinct()

        val (groupFromIds, userIds) = allFromIds.partition { it.value < 0 }

        val userProfiles = persistencePort.findUserProfilesByIds(userIds)
        val groupProfiles = persistencePort.findGroupProfilesByIds(groupFromIds)

        return ConversationContextResponse(
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
