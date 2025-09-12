package com.simarel.receiver.adapter.input.vk

import jakarta.json.JsonObject

fun interface VkMediatorRouter {
    fun callback(event: JsonObject): String
}