package com.simarel.vkbot.ai.port.output.vane

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface VaneSearchOutputPort :
    OutputPort<
            VaneSearchRequest,
            VaneSearchResponse,
            >

data class VaneSearchRequest(
    val query: String,
    val sources: List<String>,
    val optimizationMode: String,
) : OutputPortRequest

data class VaneSearchResponse(
    val message: String,
    val sources: List<VaneSource>,
) : OutputPortResponse

data class VaneSource(
    val content: String,
    val title: String,
    val url: String,
)
