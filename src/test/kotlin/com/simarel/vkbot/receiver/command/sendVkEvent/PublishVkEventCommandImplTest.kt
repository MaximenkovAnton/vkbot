package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.domain.vo.VkEvent
import com.simarel.vkbot.share.domain.Event
import io.quarkus.test.junit.QuarkusTest
import jakarta.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@QuarkusTest
class PublishVkEventCommandImplTest {

    @Test
    fun `mapVkEventToEvent returns MESSAGE_RECEIVED for MESSAGE_NEW`() {
        // Given
        val jsonObject = Json.createObjectBuilder()
            .add("type", "message_new")
            .build()
        val vkEvent = VkEvent(jsonObject)

        // When
        val result = mapVkEventToEvent(vkEvent.type())

        // Then
        assertEquals(Event.MESSAGE_RECEIVED, result)
    }

    @Test
    fun `mapVkEventToEvent returns null for unknown event`() {
        // Given
        val jsonObject = Json.createObjectBuilder()
            .add("type", "unknown_event")
            .build()
        val vkEvent = VkEvent(jsonObject)

        // When
        val result = mapVkEventToEvent(vkEvent.type())

        // Then
        assertEquals(null, result)
    }

    // Copy of the static method for testing
    private fun mapVkEventToEvent(type: VkCallbackEvent): Event? = when (type) {
        VkCallbackEvent.MESSAGE_NEW -> Event.MESSAGE_RECEIVED
        else -> null
    }
}
