package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface UpsertChatKnowledgeSummaryPort :
    OutputPort<
        UpsertChatKnowledgeSummaryPort.Request,
        UpsertChatKnowledgeSummaryPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val summaryText: String,
        val firstConversationMessageId: Long,
        val lastConversationMessageId: Long,
        val messagesCovered: Int,
    ) : OutputPortRequest

    class Response : OutputPortResponse
}
