package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindStoredMessagesBeforePort : OutputPort<FindStoredMessagesBeforePort.Request, FindStoredMessagesBeforePort.Response> {
    data class Request(val peerId: Long, val beforeConversationMessageId: Long, val limit: Int) : OutputPortRequest
    data class Response(val messages: List<StoredMessage>) : OutputPortResponse
}
