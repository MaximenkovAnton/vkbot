package com.simarel.command.sendMessage

import com.simarel.port.output.vk.VkSendMessageOutputPort
import com.simarel.port.output.vk.VkSendMessageOutputRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SendVkMessageCommandImpl(val vkSendMessageOutputPort: VkSendMessageOutputPort) : SendVkMessageCommand {
    val response = SendVkMessageCommandResponse()
    override fun execute(request: SendVkMessageCommandRequest): SendVkMessageCommandResponse {
        vkSendMessageOutputPort.execute(VkSendMessageOutputRequest(
            peerId = request.peerId, messageText = request.message
        ))
        return response
    }
}
