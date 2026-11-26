package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface PurgeIncomingMessagesPort :
    OutputPort<
        PurgeIncomingMessagesPort.Request,
        PurgeIncomingMessagesPort.Response,
        > {
    data class Request(val until: OffsetDateTime) : OutputPortRequest

    data class Response(val deleted: Int) : OutputPortResponse
}
