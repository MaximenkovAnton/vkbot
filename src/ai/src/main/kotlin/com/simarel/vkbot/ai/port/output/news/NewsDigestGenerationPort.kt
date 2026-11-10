package com.simarel.vkbot.ai.port.output.news

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface NewsDigestGenerationPort :
    OutputPort<
        NewsDigestGenerationRequest,
        NewsDigestGenerationResponse,
        >

data class NewsDigestGenerationRequest(
    val data: String,
) : OutputPortRequest

data class NewsDigestGenerationResponse(
    val text: String,
) : OutputPortResponse
