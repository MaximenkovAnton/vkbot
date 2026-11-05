package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.receiver.fixtures.FakeLongPollPortProvider
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeGetLongPollServerPort(
    private val responses: List<GetLongPollServerPort.Response> = listOf(FakeLongPollPortProvider.createSession()),
) : GetLongPollServerPort {
    val executeCalls = ConcurrentLinkedQueue<GetLongPollServerPort.Request>()
    private val queued = ConcurrentLinkedQueue(responses)

    override fun execute(request: GetLongPollServerPort.Request): GetLongPollServerPort.Response {
        executeCalls.add(request)
        return queued.poll() ?: responses.last()
    }
}
