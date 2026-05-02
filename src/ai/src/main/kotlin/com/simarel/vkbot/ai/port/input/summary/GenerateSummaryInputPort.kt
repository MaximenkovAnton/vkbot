package com.simarel.vkbot.ai.port.input.summary

import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse
import java.util.UUID

interface GenerateSummaryInputPort :
    InputPort<
            GenerateSummaryInputPortRequest,
            GenerateSummaryInputPortResponse,
            >

data class GenerateSummaryInputPortRequest(
    val peerId: Long,
    val firstMessageId: Long,
    val lastMessageId: Long,
) : InputPortRequest

data class GenerateSummaryInputPortResponse(
    val summaryId: UUID,
) : InputPortResponse
