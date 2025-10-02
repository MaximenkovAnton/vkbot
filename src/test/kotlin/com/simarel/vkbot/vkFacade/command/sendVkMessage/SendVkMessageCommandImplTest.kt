package com.simarel.vkbot.vkFacade.command.sendVkMessage

import com.simarel.vkbot.objectProvider.fake.adapter.output.vk.FakeVkClient
import com.simarel.vkbot.objectProvider.fake.command.vkFacade.FakeSendVkMessageCommandProvider
import com.simarel.vkbot.vkFacade.adapter.output.vk.VkSendMessageOutputAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SendVkMessageCommandImplTest {

    @Test
    fun `execute command successfully`() {
        val vkClient = FakeVkClient()
        val vkOutputAdapter = VkSendMessageOutputAdapter(vkClient)
        val command = SendVkMessageCommandImpl(vkOutputAdapter)
        val request = FakeSendVkMessageCommandProvider.createRequest()

        command.execute(request)

        assertEquals(1, vkClient.sendMessageParameterCalls.size)
        val call = vkClient.sendMessageParameterCalls.first()
        assertEquals(request.message.value, call.message)
        assertEquals(request.peerId.value, call.peerId)
    }
}
