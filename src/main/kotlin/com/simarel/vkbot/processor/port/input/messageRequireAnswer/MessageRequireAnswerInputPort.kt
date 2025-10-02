package com.simarel.vkbot.processor.port.input.messageRequireAnswer

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface MessageRequireAnswerInputPort :
    Port<
        MessageRequireAnswerInputPortRequest,
        MessageRequireAnswerInputPortResponse,
        >

@JvmInline
value class MessageRequireAnswerInputPortRequest(val message: Message) : InputPortRequest

@JvmInline
value class MessageRequireAnswerInputPortResponse(val value: String) : InputPortResponse
