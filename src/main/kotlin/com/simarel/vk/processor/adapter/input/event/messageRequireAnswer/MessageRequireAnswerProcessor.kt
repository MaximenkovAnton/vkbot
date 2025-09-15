package com.simarel.vk.processor.adapter.input.event.messageRequireAnswer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vk.processor.domain.model.Message
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vk.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vk.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vk.share.adapter.input.EventProcessor
import com.simarel.vk.share.domain.Event
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageRequireAnswerProcessor(
    val objectMapper: ObjectMapper,
    val messageRequireAnswerInputPort: MessageRequireAnswerInputPort,
) : EventProcessor {

    override fun process(request: JsonNode) {
        val message = objectMapper.readValue(request.toString(), Message::class.java)
        messageRequireAnswerInputPort.execute(MessageRequireAnswerInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_REQUIRE_ANSWER
}