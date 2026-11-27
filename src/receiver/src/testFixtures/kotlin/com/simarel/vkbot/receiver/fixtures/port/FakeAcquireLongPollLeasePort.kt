package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.share.port.output.persistence.AcquireLongPollLeasePort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeAcquireLongPollLeasePort(
    private val acquiredSequence: List<Boolean> = listOf(true),
    val journal: MutableList<String> = mutableListOf(),
) : AcquireLongPollLeasePort {
    val executeCalls = ConcurrentLinkedQueue<AcquireLongPollLeasePort.Request>()
    var failure: RuntimeException? = null

    override fun execute(request: AcquireLongPollLeasePort.Request): AcquireLongPollLeasePort.Response {
        executeCalls.add(request)
        journal.add(ACQUIRE)
        failure?.let { throw it }
        val index = executeCalls.size - 1
        return AcquireLongPollLeasePort.Response(
            acquired = acquiredSequence.getOrElse(index) { acquiredSequence.last() },
        )
    }

    private companion object {
        const val ACQUIRE = "acquire"
    }
}
