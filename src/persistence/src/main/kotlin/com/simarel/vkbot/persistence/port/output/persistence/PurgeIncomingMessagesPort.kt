package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface PurgeIncomingMessagesPort :
    OutputPort<
        PurgeIncomingMessagesPort.PurgeIncomingMessagesRequest,
        PurgeIncomingMessagesPort.PurgeIncomingMessagesResponse,
        > {
    data class PurgeIncomingMessagesRequest(val until: OffsetDateTime) : OutputPortRequest

    data class PurgeIncomingMessagesResponse(val deleted: Int) : OutputPortResponse
}
