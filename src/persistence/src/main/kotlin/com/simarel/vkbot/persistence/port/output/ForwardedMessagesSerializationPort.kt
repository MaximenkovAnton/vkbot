package com.simarel.vkbot.persistence.port.output

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface ForwardedMessagesSerializationPort :
    OutputPort<
        ForwardedMessagesSerializationPort.Request,
        ForwardedMessagesSerializationPort.Response,
        > {
    data class Request(val messages: List<Message>) : OutputPortRequest

    data class Response(val json: String?) : OutputPortResponse
}
