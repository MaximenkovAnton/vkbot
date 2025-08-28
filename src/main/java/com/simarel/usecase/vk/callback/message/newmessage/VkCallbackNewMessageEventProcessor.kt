package com.simarel.usecase.vk.callback.message.newmessage

import com.simarel.port.input.VkCallbackEventProcessor
import com.simarel.usecase.vk.callback.VkCallbackEvent
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackNewMessageEventProcessor : VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.MESSAGE_NEW

    override fun response() = "ok"

    override fun execute(body: JsonObject) {}
}