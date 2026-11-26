package com.simarel.vkbot.receiver.port.input

import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse
import java.time.OffsetDateTime

interface PurgeInboxInputPort : InputPort<PurgeInboxInputPort.Request, PurgeInboxInputPort.Response> {
    data class Request(val now: OffsetDateTime) : InputPortRequest

    data class Response(val deleted: Int) : InputPortResponse
}
