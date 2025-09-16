package com.simarel.vkbot.objectProvider.fake.adapter.output.vk

import com.simarel.vkbot.vkFacade.adapter.output.vk.VkClient
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkResponseDto
import java.util.concurrent.ConcurrentLinkedQueue

class FakeVkClient(val vkResponseDto: VkResponseDto? = null): VkClient {
    val sendMessageParameterCalls = ConcurrentLinkedQueue<SendMessageParameter>()

    override fun sendMessage(
        peerId: Long,
        message: String,
        rand: Int
    ): VkResponseDto {
        sendMessageParameterCalls.add(SendMessageParameter(peerId, message, rand))
        return vkResponseDto ?: VkResponseDto(error = null)
    }

    data class SendMessageParameter(val peerId: Long, val message: String, val rand: Int)
}