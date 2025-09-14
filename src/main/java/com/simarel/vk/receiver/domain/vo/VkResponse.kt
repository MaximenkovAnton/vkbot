package com.simarel.vk.receiver.domain.vo

@JvmInline
value class VkResponse(val value: String) {
    override fun toString(): String {
        return value
    }
}