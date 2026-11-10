package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindLastMessageByAuthorPort :
    OutputPort<
        FindLastMessageByAuthorPort.Request,
        FindLastMessageByAuthorPort.Response,
        > {
    data class Request(
        val peerId: PeerId,
        val fromId: FromId,
        val beforeConversationMessageId: ConversationMessageId,
    ) : OutputPortRequest

    data class Response(val message: MessageEntity?) : OutputPortResponse
}
