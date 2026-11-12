package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserKnowledgePort :
    OutputPort<
        FindUserKnowledgePort.Request,
        FindUserKnowledgePort.Response,
        > {
    data class Request(val userIds: List<FromId>) : OutputPortRequest

    data class Response(val knowledge: Map<FromId, UserKnowledge>) : OutputPortResponse
}
