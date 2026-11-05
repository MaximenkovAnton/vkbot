package com.simarel.vkbot.receiver.port.output

import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

fun interface GetLongPollServerPort : OutputPort<GetLongPollServerPort.Request, GetLongPollServerPort.Response> {
    data object Request : OutputPortRequest

    data class Response(val server: String, val key: String, val ts: String) : OutputPortResponse
}

fun interface CheckLongPollPort : OutputPort<CheckLongPollPort.Request, CheckLongPollPort.Response> {
    data class Request(val server: String, val key: String, val ts: String, val wait: Int) : OutputPortRequest

    data class Response(
        val ts: String?,
        val updates: List<String>,
        val failure: LongPollFailure,
    ) : OutputPortResponse
}
