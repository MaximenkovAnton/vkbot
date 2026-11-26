package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.share.port.output.persistence.ReleaseLongPollLeasePort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeReleaseLongPollLeasePort : ReleaseLongPollLeasePort {
    val executeCalls = ConcurrentLinkedQueue<ReleaseLongPollLeasePort.Request>()
    var failure: RuntimeException? = null

    override fun execute(request: ReleaseLongPollLeasePort.Request): ReleaseLongPollLeasePort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return ReleaseLongPollLeasePort.Response
    }
}
