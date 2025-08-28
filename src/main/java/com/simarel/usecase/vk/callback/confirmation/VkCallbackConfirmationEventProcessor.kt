package com.simarel.usecase.vk.callback.confirmation

import com.simarel.port.input.VkCallbackEventProcessor
import com.simarel.usecase.vk.callback.VkCallbackEvent
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackConfirmationEventProcessor: VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.CONFIRMATION

    override fun response() = "code"

    override fun execute(body: JsonObject) {
        TODO("Not yet implemented")
    }
}