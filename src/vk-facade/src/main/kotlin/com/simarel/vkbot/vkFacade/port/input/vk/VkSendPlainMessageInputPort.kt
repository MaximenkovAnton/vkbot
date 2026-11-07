package com.simarel.vkbot.vkFacade.port.input.vk

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface VkSendPlainMessageInputPort :
    InputPort<
        VkSendPlainMessageInputPort.Request,
        VkSendPlainMessageInputPort.Response,
        > {
    data class Request(val peerId: PeerId, val messageText: MessageText) : InputPortRequest

    data object Response : InputPortResponse
}
