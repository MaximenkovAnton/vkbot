package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindPendingKnowledgeBatchPort :
    OutputPort<
        FindPendingKnowledgeBatchPort.Request,
        FindPendingKnowledgeBatchPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val upToConversationMessageId: Long,
        val limit: Int,
    ) : OutputPortRequest

    data class Response(val messages: List<StoredMessage>) : OutputPortResponse
}
