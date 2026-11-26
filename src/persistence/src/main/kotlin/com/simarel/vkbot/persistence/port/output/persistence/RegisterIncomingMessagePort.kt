package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface RegisterIncomingMessagePort :
    OutputPort<
        RegisterIncomingMessagePort.RegisterIncomingMessageRequest,
        RegisterIncomingMessagePort.RegisterIncomingMessageResponse,
        > {
    data class RegisterIncomingMessageRequest(
        val peerId: Long,
        val conversationMessageId: Long,
        val payload: String,
    ) : OutputPortRequest

    data class RegisterIncomingMessageResponse(val inboxMessageId: Long) : OutputPortResponse
}
