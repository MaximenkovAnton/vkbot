package com.simarel.vkbot.vkFacade.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.port.input.EventProcessor
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class SendMessageEventProcessor(
    private val objectMapper: ObjectMapper,
    private val vkSendMessageInputPort: VkSendMessageInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val response = objectMapper.readValue(jsonString, ResponseMessage::class.java)
        vkSendMessageInputPort.execute(VkSendMessageInputRequest(response))
    }

    override fun event() = Event.ANSWER_MESSAGE
}
