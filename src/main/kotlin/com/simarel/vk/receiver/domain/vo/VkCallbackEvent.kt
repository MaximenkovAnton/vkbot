package com.simarel.vk.receiver.domain.vo

enum class VkCallbackEvent {
    MESSAGE_NEW,
    CONFIRMATION,
    UNKNOWN;

    companion object {
        private val entriesMap = entries.associateBy { it.name.lowercase() }

        fun mapOrUnknown(event: String) = entriesMap.getOrDefault(event, UNKNOWN)
    }
}