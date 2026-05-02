package com.simarel.vkbot.persistence.port.input

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface SaveMessageInputPort :
    InputPort<
            SaveMessageInputPortRequest,
            SaveMessageInputPortResponse,
            >

@JvmInline
value class SaveMessageInputPortRequest(val message: Message) : InputPortRequest

object SaveMessageInputPortResponse : InputPortResponse
