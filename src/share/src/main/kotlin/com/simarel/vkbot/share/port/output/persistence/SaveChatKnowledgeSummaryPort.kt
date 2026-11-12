package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveChatKnowledgeSummaryPort :
    OutputPort<
        SaveChatKnowledgeSummaryPort.Request,
        SaveChatKnowledgeSummaryPort.Response,
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
