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
        val groupId = body.getJsonNumber("group_id")?.longValue()
        return Message.of(
            groupId = groupId,
            date = messageDto?.date?.let {
                Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC)
            },
            fromId = messageDto?.fromId,
            peerId = messageDto?.peerId,
            conversationMessageId = messageJson?.getJsonNumber("conversation_message_id")?.longValue(),
            messageText = messageDto?.text?.ifEmpty { null },
            forwardedMessages = messageDto?.fwdMessages?.map { toDomainMessage(it, groupId) } ?: emptyList(),
        )
    }

    private fun toDomainMessage(messageDto: MessageDto, groupId: Long?): Message = Message.of(
        groupId = groupId,
        date = Instant.ofEpochMilli(messageDto.date).atOffset(ZoneOffset.UTC),
        fromId = messageDto.fromId,
        peerId = messageDto.peerId,
        conversationMessageId = messageDto.conversationMessageId,
        messageText = messageDto.text.ifEmpty { null },
        forwardedMessages = messageDto.fwdMessages?.map { toDomainMessage(it, groupId) } ?: emptyList(),
    )
}
