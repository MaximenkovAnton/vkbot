package com.simarel.vkbot.receiver.adapter.input.dto

import jakarta.json.JsonObject

data class MessageDto(
    val date: Long,
    val fromId: Long,
    val id: Long,
    val text: String,
    val peerId: Long,
    val fwdMessages: List<MessageDto>? = null,
    val conversationMessageId: Long,
) {
    companion object {
        fun fromJson(jsonObject: JsonObject): MessageDto {
            val fwdMessagesArray = jsonObject.getJsonArray("fwd_messages")
            val fwdMessages = fwdMessagesArray?.map { messageJson ->
                fromJson(messageJson as JsonObject)
            }

            return MessageDto(
                date = jsonObject.getJsonNumber("date")?.longValue() ?: 0L,
                fromId = jsonObject.getJsonNumber("from_id")?.longValue() ?: 0L,
                id = jsonObject.getJsonNumber("id")?.longValue() ?: 0L,
                text = jsonObject.getString("text", ""),
                peerId = jsonObject.getJsonNumber("peer_id")?.longValue() ?: 0L,
                conversationMessageId = jsonObject.getJsonNumber("conversation_message_id")?.longValue() ?: 0L,
                fwdMessages = fwdMessages,
            )
        }
    }
}
