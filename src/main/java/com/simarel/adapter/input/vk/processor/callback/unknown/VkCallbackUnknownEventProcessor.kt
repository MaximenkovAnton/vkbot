package com.simarel.adapter.input.vk.processor.callback.unknown

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import com.simarel.port.input.vk.callback.VkUnknownPort
import com.simarel.port.input.vk.callback.VkUnknownRequest
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class VkCallbackUnknownEventProcessor(val vkUnknownPort: VkUnknownPort): VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.UNKNOWN

    override fun execute(body: JsonObject): String {
        return vkUnknownPort.execute(VkUnknownRequest(body)).value
    }
}