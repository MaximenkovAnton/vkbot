package com.simarel.adapter.input.vk.processor.callback.confirmation

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import com.simarel.port.input.vk.callback.VkConfirmationPort
import com.simarel.port.input.vk.callback.VkConfirmationRequest
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackConfirmationEventProcessor(val vkConfirmationPort: VkConfirmationPort): VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.CONFIRMATION

    override fun execute(body: JsonObject): String {
        return vkConfirmationPort.execute(VkConfirmationRequest()).confirmationCode.value
    }
}