package com.simarel.vk.processor.port.input.messageRequireAnswer

import com.simarel.vk.processor.domain.model.Message
import com.simarel.vk.share.port.Port
import com.simarel.vk.share.port.input.InputPortRequest
import com.simarel.vk.share.port.input.InputPortResponse

interface MessageRequireAnswerInputPort: Port<MessageRequireAnswerInputPortRequest, MessageRequireAnswerInputPortResponse>

@JvmInline
value class MessageRequireAnswerInputPortRequest(val message: Message): InputPortRequest

@JvmInline
value class MessageRequireAnswerInputPortResponse(val value: String): InputPortResponse
