package com.simarel.vkbot.ai.adapter.input.event.messageRequireAnswer

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MessageRequireAnswerEventProcessor(
    private val objectMapper: ObjectMapper,
    private val inputPort: MessageRequireAnswerInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        inputPort.execute(MessageRequireAnswerInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_REQUIRE_ANSWER
}
