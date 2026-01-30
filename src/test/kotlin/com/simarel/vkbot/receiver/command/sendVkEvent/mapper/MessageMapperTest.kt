package com.simarel.vkbot.receiver.command.sendVkEvent.mapper

import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.share.domain.model.Message
import jakarta.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageMapperTest {
    @Test
    fun `toDomain should map JsonObject to Message`() {
        val message = FakeMessageProvider.createMessage()
        val json = Json.createObjectBuilder()
            .add("group_id", message.groupId.value)
            .add(
                "object",
                Json.createObjectBuilder()
                    .add(
                        "message",
                        Json.createObjectBuilder()
                            .add("date", message.date.value.toEpochSecond())
                            .add("from_id", message.fromId.value)
                            .add("peer_id", message.peerId.value)
                            .add("conversation_message_id", message.conversationMessageId.value)
                            .add("text", message.messageText.value),
                    ),
            )
            .build()

        val mapper = MessageMapper()
        val result: Message = mapper.toDomain(json)
        assertEquals(message, result)
    }
}
