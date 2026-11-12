package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.knowledge.StoredUserKnowledgeSummary
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserKnowledgeSummariesByUserIdsPort :
    OutputPort<
        FindUserKnowledgeSummariesByUserIdsPort.Request,
        FindUserKnowledgeSummariesByUserIdsPort.Response,
        > {
    data class Request(val userIds: List<Long>) : OutputPortRequest

    data class Response(val summaries: List<StoredUserKnowledgeSummary>) : OutputPortResponse
}
