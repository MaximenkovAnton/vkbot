package com.simarel.vkbot.receiver.adapter.output.mapper

import com.simarel.vkbot.share.domain.model.Message
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.JsonObject
import java.time.Instant
import java.time.ZoneOffset

@ApplicationScoped
class MessageMapper {
    fun toDomain(body: JsonObject): Message {
        val messageJson = body.getJsonObject("object").getJsonObject("message")
        return Message.Companion.of(
            groupId = body.getJsonNumber("group_id").longValue(),
            date = Instant.ofEpochMilli(messageJson.getJsonNumber("date").longValue())
                .atOffset(ZoneOffset.UTC),
            fromId = messageJson.getJsonNumber("from_id").longValue(),
            peerId = messageJson.getJsonNumber("peer_id").longValue(),
            conversationMessageId = messageJson.getJsonNumber("conversation_message_id").longValue(),
            messageText = messageJson.getString("text"),
        )
    }
}