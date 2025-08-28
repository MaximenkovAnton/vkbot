package com.simarel.port.input

import com.simarel.usecase.vk.callback.VkCallbackEvent
import io.vertx.core.json.JsonObject

interface VkCallbackEventProcessor {
    fun execute(body: JsonObject)
    fun response(): String
    fun event(): VkCallbackEvent
}