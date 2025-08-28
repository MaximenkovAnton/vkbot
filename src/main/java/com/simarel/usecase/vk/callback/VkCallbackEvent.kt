package com.simarel.usecase.vk.callback

enum class VkCallbackEvent {
    MESSAGE_NEW,
    CONFIRMATION,
    UNKNOWN;

    companion object {
        private val entriesMap = VkCallbackEvent.entries.associateBy { it.name.lowercase() }

        fun mapOrUnknown(event: String) = entriesMap.getOrDefault(event, UNKNOWN)
    }
}