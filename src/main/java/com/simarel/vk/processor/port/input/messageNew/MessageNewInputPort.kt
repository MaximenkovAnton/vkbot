package com.simarel.vk.processor.port.input.messageNew

import com.simarel.vk.processor.domain.model.Message
import com.simarel.vk.share.port.Port
import com.simarel.vk.share.port.input.InputPortRequest
import com.simarel.vk.share.port.input.InputPortResponse

interface MessageNewInputPort: Port<MessageNewInputPortRequest, MessageNewInputPortResponse>

@JvmInline
value class MessageNewInputPortRequest(val message: Message): InputPortRequest

@JvmInline
value class MessageNewInputPortResponse(val value: String): InputPortResponse
