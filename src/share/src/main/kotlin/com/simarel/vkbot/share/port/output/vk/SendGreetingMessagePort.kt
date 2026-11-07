package com.simarel.vkbot.share.port.output.vk

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SendGreetingMessagePort :
    OutputPort<
        SendGreetingMessagePort.Request,
        SendGreetingMessagePort.Response,
        > {
    data class Request(val peerId: PeerId, val messageText: MessageText) : OutputPortRequest
    data object Response : OutputPortResponse
}
