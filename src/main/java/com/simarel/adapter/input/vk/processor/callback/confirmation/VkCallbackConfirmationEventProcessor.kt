package com.simarel.adapter.input.vk.processor.callback.confirmation

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
open class VkCallbackConfirmationEventProcessor: VkCallbackEventProcessor {

    @ConfigProperty(name = "vk.confirmation-code")
    lateinit var secret: String

    override fun event() = VkCallbackEvent.CONFIRMATION

    override fun execute(body: JsonObject): String {
        return secret
    }
}