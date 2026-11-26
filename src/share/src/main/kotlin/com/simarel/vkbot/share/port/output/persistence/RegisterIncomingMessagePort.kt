package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.InboxMessageId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface RegisterIncomingMessagePort :
    OutputPort<
        RegisterIncomingMessagePort.Request,
        RegisterIncomingMessagePort.Response,
        > {
    data class Request(
        val peerId: PeerId,
        val conversationMessageId: ConversationMessageId,
        val payload: String,
    ) : OutputPortRequest

    data class Response(val inboxMessageId: InboxMessageId) : OutputPortResponse
}
