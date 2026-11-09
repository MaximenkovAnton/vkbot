package com.simarel.vkbot.processor.testfixtures.port.output.decision

import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageOutputPort
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageRequest
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageResponse
import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import java.util.concurrent.ConcurrentLinkedQueue

class FakeAnalyzeMessageOutputPort : AnalyzeMessageOutputPort {

    val executeCalls = ConcurrentLinkedQueue<AnalyzeMessageRequest>()
    var response: AnalyzeMessageResponse = FakeDecisionProvider.createAnalyzeMessageResponse(
        FakeDecisionProvider.createAnalysis(spamProbability = 0.0),
    )
    var failure: Throwable? = null

    override fun execute(request: AnalyzeMessageRequest): AnalyzeMessageResponse {
        executeCalls.add(request)
        failure?.let { throw it }
        return response
    }
}
