package com.simarel.vkbot.processor.port.input.messageNew

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface MessageNewInputPort: Port<MessageNewInputPortRequest, MessageNewInputPortResponse>

@JvmInline
value class MessageNewInputPortRequest(val message: Message): InputPortRequest

@JvmInline
value class MessageNewInputPortResponse(val value: String): InputPortResponse
