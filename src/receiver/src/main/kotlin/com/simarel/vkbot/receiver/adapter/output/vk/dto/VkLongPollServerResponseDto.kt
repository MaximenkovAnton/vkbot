package com.simarel.vkbot.receiver.adapter.output.vk.dto

import com.fasterxml.jackson.databind.JsonNode

data class VkLongPollServerResponseDto(val response: VkLongPollServerDto?)

data class VkLongPollServerDto(
    val key: String,
    val server: String,
    val ts: JsonNode,
)
