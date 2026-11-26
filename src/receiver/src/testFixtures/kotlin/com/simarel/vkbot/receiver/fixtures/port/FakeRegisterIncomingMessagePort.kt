package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.share.port.output.persistence.RegisterIncomingMessagePort
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import java.util.concurrent.ConcurrentLinkedQueue

class FakeRegisterIncomingMessagePort(
    var response: RegisterIncomingMessagePort.Response =
        FakePersistencePortProvider.createRegisterIncomingMessageResponse(),
    val journal: MutableList<String> = mutableListOf(),
) : RegisterIncomingMessagePort {
    val executeCalls = ConcurrentLinkedQueue<RegisterIncomingMessagePort.Request>()
    var failure: RuntimeException? = null

    override fun execute(request: RegisterIncomingMessagePort.Request): RegisterIncomingMessagePort.Response {
        executeCalls.add(request)
        journal.add(REGISTER)
        failure?.let { throw it }
        return response
    }

    private companion object {
        const val REGISTER = "register"
    }
}
