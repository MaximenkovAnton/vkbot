package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.receiver.fixtures.FakeLongPollPortProvider
import com.simarel.vkbot.receiver.port.output.CheckLongPollPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeCheckLongPollPort(
    private val responses: List<CheckLongPollPort.Response> = listOf(FakeLongPollPortProvider.createCheckResponse()),
) : CheckLongPollPort {
    val executeCalls = ConcurrentLinkedQueue<CheckLongPollPort.Request>()
    var failure: RuntimeException? = null
    private val queued = ConcurrentLinkedQueue(responses)

    override fun execute(request: CheckLongPollPort.Request): CheckLongPollPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return queued.poll() ?: responses.last()
    }
}
