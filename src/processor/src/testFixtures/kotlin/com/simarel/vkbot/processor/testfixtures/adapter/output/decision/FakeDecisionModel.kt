package com.simarel.vkbot.processor.testfixtures.adapter.output.decision

import dev.langchain4j.model.decision.DecisionModel
import dev.langchain4j.model.decision.request.DecisionRequest
import dev.langchain4j.model.decision.response.DecisionResponse

class FakeDecisionModel : DecisionModel {

    val requests = mutableListOf<DecisionRequest>()
    var calls = 0
    var response: DecisionResponse? = null
    var failure: RuntimeException? = null
    var validateResponse: Boolean = true

    override fun decide(request: DecisionRequest): DecisionResponse = if (validateResponse) super.decide(request) else doDecide(request)

    override fun doDecide(request: DecisionRequest): DecisionResponse {
        calls++
        requests.add(request)
        failure?.let { throw it }
        return checkNotNull(response) { "Either a response or a failure must be set" }
    }
}
