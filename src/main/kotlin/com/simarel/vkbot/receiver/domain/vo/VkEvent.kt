package com.simarel.vkbot.receiver.domain.vo

import jakarta.json.JsonObject

@JvmInline
value class VkEvent(val value: JsonObject) {
    fun type(): VkCallbackEvent {
        return value
            .getString("type")
            ?.let { VkCallbackEvent.mapOrUnknown(it) }
            ?: VkCallbackEvent.UNKNOWN
    }
}