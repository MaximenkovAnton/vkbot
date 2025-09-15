package com.simarel.vkbot.vkFacade.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.processor.domain.model.Message
import com.simarel.vkbot.share.adapter.input.EventProcessor
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class SendMessageEventProcessor(
    val objectMapper: ObjectMapper,
    val vkSendMessageInputPort: VkSendMessageInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        vkSendMessageInputPort.execute(VkSendMessageInputRequest(message.peerId, message.messageText))
    }

    override fun event() = Event.SEND_MESSAGE
}