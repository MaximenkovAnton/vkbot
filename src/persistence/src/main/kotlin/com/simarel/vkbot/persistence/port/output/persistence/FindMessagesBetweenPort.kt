package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindMessagesBetweenPort : OutputPort<FindMessagesBetweenPort.FindMessagesBetweenRequest, FindMessagesBetweenPort.FindMessagesBetweenResponse> {
    data class FindMessagesBetweenRequest(
        val peerId: PeerId,
        val firstMessageId: ConversationMessageId,
        val lastMessageId: ConversationMessageId,
        val limit: Int,
    ) : OutputPortRequest
    data class FindMessagesBetweenResponse(val messages: List<MessageEntity>) : OutputPortResponse
}
