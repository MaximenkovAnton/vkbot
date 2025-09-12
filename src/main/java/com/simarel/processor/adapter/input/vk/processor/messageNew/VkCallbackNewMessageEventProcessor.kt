package com.simarel.processor.adapter.input.vk.processor.messageNew

import com.fasterxml.jackson.databind.JsonNode
import com.simarel.processor.adapter.input.vk.processor.messageNew.mapper.MessageMapper
import com.simarel.processor.port.input.vk.VkMessageNewInputPort
import com.simarel.processor.port.input.vk.VkMessageNewInputPortRequest
import com.simarel.share.adapter.input.EventProcessor
import com.simarel.share.domain.Event
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkCallbackNewMessageEventProcessor(
    val messageMapper: MessageMapper,
    val vkMessageNewInputPort: VkMessageNewInputPort,
) : EventProcessor {

    override fun process(request: JsonNode) {
        val message = messageMapper.toDomain(request)
        vkMessageNewInputPort.execute(VkMessageNewInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_RECEIVED
}