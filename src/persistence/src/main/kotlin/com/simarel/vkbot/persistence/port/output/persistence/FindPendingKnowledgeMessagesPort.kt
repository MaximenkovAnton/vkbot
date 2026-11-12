package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindPendingKnowledgeMessagesPort :
    OutputPort<
        FindPendingKnowledgeMessagesPort.Request,
        FindPendingKnowledgeMessagesPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val upToConversationMessageId: Long,
        val limit: Int,
    ) : OutputPortRequest

    data class Response(val messages: List<StoredMessage>) : OutputPortResponse
}
