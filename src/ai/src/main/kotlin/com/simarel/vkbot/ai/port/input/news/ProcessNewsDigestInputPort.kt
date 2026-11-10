package com.simarel.vkbot.ai.port.input.news

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ProcessNewsDigestInputPort :
    InputPort<
        ProcessNewsDigestInputPortRequest,
        ProcessNewsDigestInputPortResponse,
        >

data class ProcessNewsDigestInputPortRequest(
    val message: Message,
) : InputPortRequest

object ProcessNewsDigestInputPortResponse : InputPortResponse
