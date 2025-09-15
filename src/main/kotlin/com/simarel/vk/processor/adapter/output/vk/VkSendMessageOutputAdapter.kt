package com.simarel.vk.processor.adapter.output.vk

import com.simarel.vk.processor.port.output.vk.VkSendMessageOutputPort
import com.simarel.vk.processor.port.output.vk.VkSendMessageOutputRequest
import com.simarel.vk.processor.port.output.vk.VkSendMessageOutputResponse
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient


@ApplicationScoped
class VkSendMessageOutputAdapter(@RestClient val vkClient: VkClient): VkSendMessageOutputPort {
    val response = VkSendMessageOutputResponse()
    override fun execute(request: VkSendMessageOutputRequest): VkSendMessageOutputResponse {
        vkClient.sendMessage(peerId = request.peerId.value, message = request.messageText.value, rand = 0)
        return response
    }
}