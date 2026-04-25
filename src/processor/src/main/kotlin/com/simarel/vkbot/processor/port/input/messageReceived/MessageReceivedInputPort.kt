package com.simarel.vkbot.processor.port.input.messageReceived

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface MessageReceivedInputPort : Port<MessageReceivedInputPortRequest, MessageReceivedInputPortResponse>

@JvmInline
value class MessageReceivedInputPortRequest(val message: Message) : InputPortRequest

@JvmInline
value class MessageReceivedInputPortResponse(val value: String) : InputPortResponse
