package com.simarel.vkbot.vkFacade.adapter.input.inprocess

import com.simarel.vkbot.share.port.output.vk.SendGreetingMessagePort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendPlainMessageInputPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SendGreetingMessageInProcessAdapter(
    private val vkSendPlainMessageInputPort: VkSendPlainMessageInputPort,
) : SendGreetingMessagePort {
    override fun execute(request: SendGreetingMessagePort.Request): SendGreetingMessagePort.Response {
        vkSendPlainMessageInputPort.execute(
            VkSendPlainMessageInputPort.Request(
                peerId = request.peerId,
                messageText = request.messageText,
            ),
        )
        return SendGreetingMessagePort.Response
    }
}
