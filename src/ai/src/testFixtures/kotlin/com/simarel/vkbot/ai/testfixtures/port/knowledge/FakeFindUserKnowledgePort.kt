package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindUserKnowledgePort : FindUserKnowledgePort {
    var knowledge: Map<FromId, UserKnowledge> = emptyMap()
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<FindUserKnowledgePort.Request>()

    override fun execute(request: FindUserKnowledgePort.Request): FindUserKnowledgePort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return FindUserKnowledgePort.Response(knowledge = knowledge)
    }
}
