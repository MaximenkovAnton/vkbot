package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindMessagesBeforePort : OutputPort<FindMessagesBeforePort.FindMessagesBeforeRequest, FindMessagesBeforePort.FindMessagesBeforeResponse> {
    data class FindMessagesBeforeRequest(
        val peerId: PeerId,
        val beforeConversationMessageId: ConversationMessageId,
        val limit: Int
    ) : OutputPortRequest
    data class FindMessagesBeforeResponse(val messages: List<MessageEntity>) : OutputPortResponse
}
