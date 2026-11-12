package com.simarel.vkbot.ai.port.input.knowledge

import com.simarel.vkbot.share.domain.event.UserKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ProcessUserKnowledgeSummaryInputPort :
    InputPort<
        ProcessUserKnowledgeSummaryInputPortRequest,
        ProcessUserKnowledgeSummaryInputPortResponse,
        >

data class ProcessUserKnowledgeSummaryInputPortRequest(
    val payload: UserKnowledgeSummaryRequestedPayload,
) : InputPortRequest

object ProcessUserKnowledgeSummaryInputPortResponse : InputPortResponse
