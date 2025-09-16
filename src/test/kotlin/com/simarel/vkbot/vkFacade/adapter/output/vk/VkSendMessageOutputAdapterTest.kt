package com.simarel.vkbot.vkFacade.adapter.output.vk

import com.simarel.vkbot.objectProvider.fake.adapter.output.vk.FakeVkClient
import com.simarel.vkbot.objectProvider.fake.port.output.vk.FakeVkSendMessageOutputProvider
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkError
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkResponseDto
import com.simarel.vkbot.vkFacade.adapter.output.vk.exception.VkException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VkSendMessageOutputAdapterTest {

    @Test
    fun `send vk message happy path`() {
        val vkClient = FakeVkClient()
        val vkSendMessageOutputAdapter = VkSendMessageOutputAdapter(vkClient)
        val request = FakeVkSendMessageOutputProvider.createRequest()

        vkSendMessageOutputAdapter.execute(request)

        assertEquals(1, vkClient.sendMessageParameterCalls.size)
        val call = vkClient.sendMessageParameterCalls.first()
        assertEquals(request.messageText.value, call.message)
        assertEquals(request.peerId.value, call.peerId)
        assertEquals(0, call.rand)
    }

    @Test
    fun `send vk message error handling`() {
        val vkClient = FakeVkClient(VkResponseDto(VkError(error_code = 1, "error happened")))
        val vkSendMessageOutputAdapter = VkSendMessageOutputAdapter(vkClient)
        val request = FakeVkSendMessageOutputProvider.createRequest()

        assertThrows<VkException> {
            vkSendMessageOutputAdapter.execute(request)
        }
        assertEquals(1, vkClient.sendMessageParameterCalls.size)
        val call = vkClient.sendMessageParameterCalls.first()
        assertEquals(request.messageText.value, call.message)
        assertEquals(request.peerId.value, call.peerId)
        assertEquals(0, call.rand)
    }

}