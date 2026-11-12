package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeSaveUserFactsPort : SaveUserFactsPort {
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<SaveUserFactsPort.Request>()

    override fun execute(request: SaveUserFactsPort.Request): SaveUserFactsPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return SaveUserFactsPort.Response(saved = request.candidates.size, skipped = 0)
    }
}
