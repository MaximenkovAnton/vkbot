package com.simarel.vkbot.receiver.domain.vo

@JvmInline
value class VkResponse(val value: String) {
    override fun toString(): String {
        return value
    }
}