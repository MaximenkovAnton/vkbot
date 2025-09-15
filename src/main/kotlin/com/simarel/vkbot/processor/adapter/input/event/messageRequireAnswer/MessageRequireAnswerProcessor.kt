package com.simarel.vkbot.processor.adapter.input.event.messageRequireAnswer

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.processor.domain.model.Message
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.share.adapter.input.EventProcessor
import com.simarel.vkbot.share.domain.Event
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageRequireAnswerProcessor(
    val objectMapper: ObjectMapper,
    val messageRequireAnswerInputPort: MessageRequireAnswerInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        messageRequireAnswerInputPort.execute(MessageRequireAnswerInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_REQUIRE_ANSWER
}