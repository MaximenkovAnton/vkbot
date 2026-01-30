package com.simarel.vkbot.receiver.command.sendVkEvent.mapper

import com.simarel.vkbot.receiver.adapter.input.dto.MessageDto
import com.simarel.vkbot.share.domain.model.Message
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.JsonObject
import java.time.Instant
import java.time.ZoneOffset

@ApplicationScoped
class MessageMapper {
    fun toDomain(body: JsonObject): Message {
        val messageJson = body.getJsonObject("object")?.getJsonObject("message")
        val messageDto = messageJson?.let { MessageDto.fromJson(it) }

        return Message.Companion.of(
            groupId = body.getJsonNumber("group_id")?.longValue(),
            date = messageDto?.date?.let {
                Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC)
            },
            fromId = messageDto?.fromId,
            peerId = messageDto?.peerId,
            conversationMessageId = messageJson?.getJsonNumber("conversation_message_id")?.longValue(),
            messageText = messageDto?.text?.ifEmpty { null },
            forwardedMessages = messageDto?.fwdMessages?.map { toDomainMessage(it) } ?: emptyList(),
        )
    }

    private fun toDomainMessage(messageDto: MessageDto): Message = Message.Companion.of(
        groupId = null,
        date = Instant.ofEpochMilli(messageDto.date).atOffset(ZoneOffset.UTC),
        fromId = messageDto.fromId,
        peerId = messageDto.peerId,
        conversationMessageId = null,
        messageText = messageDto.text.ifEmpty { null },
        forwardedMessages = messageDto.fwdMessages?.map { toDomainMessage(it) } ?: emptyList(),
    )
}
