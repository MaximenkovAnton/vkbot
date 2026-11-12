package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionPort
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionRequest
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeKnowledgeExtractionPort : KnowledgeExtractionPort {
    var response: KnowledgeExtractionResponse = KnowledgeExtractionResponse(facts = emptyList())
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<KnowledgeExtractionRequest>()

    override fun execute(request: KnowledgeExtractionRequest): KnowledgeExtractionResponse {
        executeCalls.add(request)
        failure?.let { throw it }
        return response
    }
}
