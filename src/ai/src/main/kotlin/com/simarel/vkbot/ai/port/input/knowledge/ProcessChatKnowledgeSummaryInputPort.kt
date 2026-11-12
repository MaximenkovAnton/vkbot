package com.simarel.vkbot.ai.port.input.knowledge

import com.simarel.vkbot.share.domain.event.ChatKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ProcessChatKnowledgeSummaryInputPort :
    InputPort<
        ProcessChatKnowledgeSummaryInputPortRequest,
        ProcessChatKnowledgeSummaryInputPortResponse,
        >

data class ProcessChatKnowledgeSummaryInputPortRequest(
    val payload: ChatKnowledgeSummaryRequestedPayload,
) : InputPortRequest

object ProcessChatKnowledgeSummaryInputPortResponse : InputPortResponse
