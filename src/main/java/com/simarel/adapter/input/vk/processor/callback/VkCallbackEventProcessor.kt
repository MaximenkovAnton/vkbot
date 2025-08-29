package com.simarel.adapter.input.vk.processor.callback

import io.vertx.core.json.JsonObject

interface VkCallbackEventProcessor {
    fun execute(body: JsonObject): String
    fun event(): VkCallbackEvent
}