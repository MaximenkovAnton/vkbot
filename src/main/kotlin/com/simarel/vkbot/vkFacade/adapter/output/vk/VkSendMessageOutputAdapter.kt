package com.simarel.vkbot.vkFacade.adapter.output.vk

import com.simarel.vkbot.vkFacade.adapter.output.vk.exception.VkException
import com.simarel.vkbot.vkFacade.port.output.vk.VkSendMessageOutputPort
import com.simarel.vkbot.vkFacade.port.output.vk.VkSendMessageOutputRequest
import com.simarel.vkbot.vkFacade.port.output.vk.VkSendMessageOutputResponse
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient


@ApplicationScoped
class VkSendMessageOutputAdapter(@RestClient val vkClient: VkClient): VkSendMessageOutputPort {
    val response = VkSendMessageOutputResponse()
    override fun execute(request: VkSendMessageOutputRequest): VkSendMessageOutputResponse {
        val vkResponse = vkClient.sendMessage(peerId = request.peerId.value, message = request.messageText.value, rand = 0)
        if(vkResponse.error != null) {
            throw VkException("${vkResponse.error.error_msg}(${vkResponse.error.error_code})")
        }
        return response
    }
}