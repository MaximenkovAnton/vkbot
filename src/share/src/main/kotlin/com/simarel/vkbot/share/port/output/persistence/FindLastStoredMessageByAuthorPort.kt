package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindLastStoredMessageByAuthorPort :
    OutputPort<
        FindLastStoredMessageByAuthorPort.Request,
        FindLastStoredMessageByAuthorPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val fromId: Long,
        val beforeConversationMessageId: Long,
    ) : OutputPortRequest

    data class Response(val message: StoredMessage?) : OutputPortResponse
}
