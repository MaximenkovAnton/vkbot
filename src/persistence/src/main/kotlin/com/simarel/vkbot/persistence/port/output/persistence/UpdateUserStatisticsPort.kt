package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

fun interface UpdateUserStatisticsPort :
    OutputPort<
        UpdateUserStatisticsPort.Request,
        UpdateUserStatisticsPort.Response,
        > {

    data class Request(
        val userId: Long,
        val peerId: Long,
        val date: OffsetDateTime,
        val messageTextLength: Int,
    ) : OutputPortRequest

    object Response : OutputPortResponse
}
