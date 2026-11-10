package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface FindStoredMessagesBetweenDatesPort :
    OutputPort<
        FindStoredMessagesBetweenDatesPort.Request,
        FindStoredMessagesBetweenDatesPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
        val limit: Int,
    ) : OutputPortRequest

    data class Response(val messages: List<StoredMessage>) : OutputPortResponse
}
