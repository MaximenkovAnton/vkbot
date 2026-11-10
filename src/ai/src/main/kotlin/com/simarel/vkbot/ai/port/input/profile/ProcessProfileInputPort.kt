package com.simarel.vkbot.ai.port.input.profile

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ProcessProfileInputPort :
    InputPort<
        ProcessProfileInputPortRequest,
        ProcessProfileInputPortResponse,
        >

data class ProcessProfileInputPortRequest(
    val message: Message,
) : InputPortRequest

object ProcessProfileInputPortResponse : InputPortResponse
