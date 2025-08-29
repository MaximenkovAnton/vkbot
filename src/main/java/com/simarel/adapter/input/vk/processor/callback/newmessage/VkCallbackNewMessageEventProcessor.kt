package com.simarel.adapter.input.vk.processor.callback.newmessage

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackNewMessageEventProcessor : VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.MESSAGE_NEW

    override fun execute(body: JsonObject): String {
        return "ok"
    }
}