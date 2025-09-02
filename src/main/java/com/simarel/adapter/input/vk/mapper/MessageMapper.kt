package com.simarel.adapter.input.vk.mapper

import com.simarel.domain.model.Message
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.time.ZoneOffset

@ApplicationScoped
class MessageMapper {
    fun toDomain(body: JsonObject): Message {
        val messageJson = body.getJsonObject("object").getJsonObject("message")
        return Message.of(
            groupId = body.getLong("group_id"),
            date = Instant.ofEpochMilli(messageJson.getLong("date")).atOffset(ZoneOffset.UTC),
            fromId = messageJson.getLong("from_id"),
            peerId = messageJson.getLong("peer_id"),
            conversationMessageId = messageJson.getLong("conversation_message_id"),
            messageText = messageJson.getString("text"),
        )
    }
}