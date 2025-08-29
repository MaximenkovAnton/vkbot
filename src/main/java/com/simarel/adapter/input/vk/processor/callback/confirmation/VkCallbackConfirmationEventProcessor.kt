package com.simarel.adapter.input.vk.processor.callback.confirmation

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackConfirmationEventProcessor: VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.CONFIRMATION

    override fun execute(body: JsonObject): String {
        return "code"
    }
}