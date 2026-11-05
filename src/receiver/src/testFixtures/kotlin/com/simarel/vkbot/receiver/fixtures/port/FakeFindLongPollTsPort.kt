package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.share.domain.vo.LongPollTs
import com.simarel.vkbot.share.port.output.persistence.FindLongPollTsPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindLongPollTsPort(
    private val ts: LongPollTs? = null,
) : FindLongPollTsPort {
    val executeCalls = ConcurrentLinkedQueue<FindLongPollTsPort.Request>()

    override fun execute(request: FindLongPollTsPort.Request): FindLongPollTsPort.Response {
        executeCalls.add(request)
        return FindLongPollTsPort.Response(ts)
    }
}
