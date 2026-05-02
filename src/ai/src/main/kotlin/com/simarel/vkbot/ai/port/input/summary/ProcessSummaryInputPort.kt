package com.simarel.vkbot.ai.port.input.summary

import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ProcessSummaryInputPort :
    InputPort<
            ProcessSummaryInputPortRequest,
            ProcessSummaryInputPortResponse,
            >

data class ProcessSummaryInputPortRequest(
    val peerId: Long,
    val conversationMessageId: Long,
) : InputPortRequest

object ProcessSummaryInputPortResponse : InputPortResponse
