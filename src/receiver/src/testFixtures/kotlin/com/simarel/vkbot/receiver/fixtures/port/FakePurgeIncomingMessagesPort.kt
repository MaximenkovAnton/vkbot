package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.share.port.output.persistence.PurgeIncomingMessagesPort
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import java.util.concurrent.ConcurrentLinkedQueue

class FakePurgeIncomingMessagesPort(
    var response: PurgeIncomingMessagesPort.Response =
        FakePersistencePortProvider.createPurgeIncomingMessagesResponse(),
) : PurgeIncomingMessagesPort {
    val executeCalls = ConcurrentLinkedQueue<PurgeIncomingMessagesPort.Request>()
    var failure: RuntimeException? = null

    override fun execute(request: PurgeIncomingMessagesPort.Request): PurgeIncomingMessagesPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return response
    }
}
