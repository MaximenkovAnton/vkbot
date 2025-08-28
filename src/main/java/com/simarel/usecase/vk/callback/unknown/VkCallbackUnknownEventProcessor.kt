package com.simarel.usecase.vk.callback.unknown

import com.simarel.port.input.VkCallbackEventProcessor
import com.simarel.usecase.vk.callback.VkCallbackEvent
import io.quarkus.logging.Log
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class VkCallbackUnknownEventProcessor: VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.UNKNOWN

    override fun response() = "ok"

    override fun execute(body: JsonObject) {
        Log.warn("Unknown event: $body")
    }
}