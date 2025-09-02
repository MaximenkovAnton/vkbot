package com.simarel.adapter.input.vk.processor.callback.newmessage

import com.simarel.adapter.input.vk.mapper.MessageMapper
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import com.simarel.port.input.vk.VkMessageNewInputPort
import com.simarel.port.input.vk.VkMessageNewInputRequest
import io.vertx.core.json.JsonObject
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackNewMessageEventProcessor(
    val messageMapper: MessageMapper,
    val vkMessageNewInputPort: VkMessageNewInputPort,
) : VkCallbackEventProcessor {

    override fun event() = VkCallbackEvent.MESSAGE_NEW

    override fun execute(body: JsonObject): String {
        val message = messageMapper.toDomain(body)
        val response = vkMessageNewInputPort.execute(VkMessageNewInputRequest(message))
        return response.value
    }
}