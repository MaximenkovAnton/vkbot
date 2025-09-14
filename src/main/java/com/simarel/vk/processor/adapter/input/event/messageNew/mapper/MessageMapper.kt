package com.simarel.vk.processor.adapter.input.event.messageNew.mapper

import com.fasterxml.jackson.databind.JsonNode
import com.simarel.vk.processor.domain.model.Message
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.time.ZoneOffset

@ApplicationScoped
class MessageMapper {
    fun toDomain(body: JsonNode): Message {
        val messageJson = body.get("object").get("message")
        return Message.Companion.of(
            groupId = body.get("group_id").longValue(),
            date = Instant.ofEpochMilli(messageJson.get("date").longValue())
                .atOffset(ZoneOffset.UTC),
            fromId = messageJson.get("from_id").longValue(),
            peerId = messageJson.get("peer_id").longValue(),
            conversationMessageId = messageJson.get("conversation_message_id").longValue(),
            messageText = messageJson.get("text").asText(),
        )
    }
}