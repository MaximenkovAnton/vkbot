package com.simarel.vkbot.objectProvider.fake.domain

import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.domain.vo.VkEvent
import jakarta.json.Json

object FakeVkProvider {
    const val SECRET = "test_secret"
    fun createVkEvent(type: VkCallbackEvent = VkCallbackEvent.MESSAGE_NEW): VkEvent {
        val jsonObject = when (type) {
            VkCallbackEvent.MESSAGE_NEW -> Json.createObjectBuilder()
                .add("type", VkCallbackEvent.MESSAGE_NEW.name.lowercase())
                .add("group_id", FakeVoProvider.createGroupId().value)
                .add(
                    "object",
                    Json.createObjectBuilder()
                        .add(
                            "message",
                            Json.createObjectBuilder()
                                .add("text", FakeVoProvider.createMessageText().value)
                                .add("from_id", FakeVoProvider.createFromId().value)
                                .add("peer_id", FakeVoProvider.createPeerId().value)
                                .add("conversation_message_id", FakeVoProvider.createConversationMessageId().value)
                                .add("date", FakeVoProvider.createDate().value.second)
                        )
                )
                .add("secret", SECRET)
                .build()

            VkCallbackEvent.CONFIRMATION -> Json.createObjectBuilder()
                .add("type", VkCallbackEvent.CONFIRMATION.name.lowercase())
                .add("group_id", FakeVoProvider.createGroupId().value)
                .add("secret", SECRET)
                .build()

            VkCallbackEvent.UNKNOWN -> Json.createObjectBuilder()
                .add("type", "unknown_event")
                .add("group_id", FakeVoProvider.createGroupId().value)
                .add("secret", SECRET)
                .build()
        }
        return VkEvent(jsonObject)
    }
}
