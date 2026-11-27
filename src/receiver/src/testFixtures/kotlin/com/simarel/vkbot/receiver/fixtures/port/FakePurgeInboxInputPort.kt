package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.receiver.fixtures.FakePurgeInboxInputPortProvider
import com.simarel.vkbot.receiver.port.input.PurgeInboxInputPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakePurgeInboxInputPort(
    var response: PurgeInboxInputPort.Response = FakePurgeInboxInputPortProvider.createResponse(),
) : PurgeInboxInputPort {
    val executeCalls = ConcurrentLinkedQueue<PurgeInboxInputPort.Request>()
    var failure: RuntimeException? = null

    override fun execute(request: PurgeInboxInputPort.Request): PurgeInboxInputPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return response
    }
}
