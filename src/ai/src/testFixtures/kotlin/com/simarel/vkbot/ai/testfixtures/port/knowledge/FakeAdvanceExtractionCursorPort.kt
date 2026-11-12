package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeAdvanceExtractionCursorPort : AdvanceExtractionCursorPort {
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<AdvanceExtractionCursorPort.Request>()

    override fun execute(request: AdvanceExtractionCursorPort.Request): AdvanceExtractionCursorPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return AdvanceExtractionCursorPort.Response()
    }
}
