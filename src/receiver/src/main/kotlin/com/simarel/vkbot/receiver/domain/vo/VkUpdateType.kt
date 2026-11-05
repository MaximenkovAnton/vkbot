package com.simarel.vkbot.receiver.domain.vo

enum class VkUpdateType {
    MESSAGE_NEW,
    MESSAGE_REPLY,
    UNKNOWN,
    ;

    companion object {
        private val entriesMap = entries.associateBy { it.name.lowercase() }

        fun mapOrUnknown(event: String) = entriesMap.getOrDefault(event.lowercase(), UNKNOWN)
    }
}
