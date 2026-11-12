package com.simarel.vkbot.ai.port.input.knowledge

import com.simarel.vkbot.share.domain.event.UserKnowledgeRequestedPayload
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ProcessUserKnowledgeInputPort :
    InputPort<
        ProcessUserKnowledgeInputPortRequest,
        ProcessUserKnowledgeInputPortResponse,
        >

data class ProcessUserKnowledgeInputPortRequest(
    val payload: UserKnowledgeRequestedPayload,
) : InputPortRequest

object ProcessUserKnowledgeInputPortResponse : InputPortResponse
