package com.simarel.vkbot.ai.port.output.summary

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SummarizationOutputPort :
    OutputPort<
            SummarizationRequest,
            SummarizationResponse,
            >

data class SummarizationRequest(
    val messages: String,
) : OutputPortRequest

data class SummarizationResponse(
    val shortSummary: String,
    val fullSummary: String,
) : OutputPortResponse
