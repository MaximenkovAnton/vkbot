package com.simarel.adapter.input.vk.processor.callback.unknown

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import io.quarkus.logging.Log
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class VkCallbackUnknownEventProcessor: VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.UNKNOWN

    override fun execute(body: JsonObject): String {
        Log.warn("Unknown event: $body")
        return "ok"
    }
}