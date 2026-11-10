package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface FindMessagesBetweenDatesPort :
    OutputPort<
        FindMessagesBetweenDatesPort.Request,
        FindMessagesBetweenDatesPort.Response,
        > {
    data class Request(
        val peerId: PeerId,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
        val limit: Int,
    ) : OutputPortRequest

    data class Response(val messages: List<MessageEntity>) : OutputPortResponse
}
