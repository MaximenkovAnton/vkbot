package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.share.port.output.persistence.SaveLongPollTsPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeSaveLongPollTsPort(
    val journal: MutableList<String> = mutableListOf(),
) : SaveLongPollTsPort {
    val executeCalls = ConcurrentLinkedQueue<SaveLongPollTsPort.Request>()

    override fun execute(request: SaveLongPollTsPort.Request): SaveLongPollTsPort.Response {
        executeCalls.add(request)
        journal.add(SAVE)
        return SaveLongPollTsPort.Response
    }

    private companion object {
        const val SAVE = "save"
    }
}
