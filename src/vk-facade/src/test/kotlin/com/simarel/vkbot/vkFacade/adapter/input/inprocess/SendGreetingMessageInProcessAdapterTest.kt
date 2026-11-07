package com.simarel.vkbot.vkFacade.adapter.input.inprocess

import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendPlainMessageInputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SendGreetingMessageInProcessAdapterTest {

    private val peerId = FakeVoProvider.createPeerId(value = 2_000_000_001)
    private val messageText = FakeVoProvider.createMessageText(value = "С Новым годом!")

    @Test
    fun `adapter forwards the greeting to the plain message input port`() {
        val port = RecordingVkSendPlainMessageInputPort()

        SendGreetingMessageInProcessAdapter(port).execute(
            FakeGreetingPortProvider.createSendGreetingMessageRequest(
                peerId = peerId,
                messageText = messageText,
            ),
        )

        val forwarded = port.requests.single()
        assertEquals(peerId, forwarded.peerId)
        assertEquals(messageText, forwarded.messageText)
    }

    private class RecordingVkSendPlainMessageInputPort : VkSendPlainMessageInputPort {
        val requests = mutableListOf<VkSendPlainMessageInputPort.Request>()

        override fun execute(request: VkSendPlainMessageInputPort.Request): VkSendPlainMessageInputPort.Response {
            requests.add(request)
            return VkSendPlainMessageInputPort.Response
        }
    }
}
