package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.Duration

interface HasPendingKnowledgeBatchPort :
    OutputPort<
        HasPendingKnowledgeBatchPort.Request,
        HasPendingKnowledgeBatchPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val minCount: Int,
        val minInterval: Duration,
    ) : OutputPortRequest

    data class Response(val hasPending: Boolean) : OutputPortResponse
}
