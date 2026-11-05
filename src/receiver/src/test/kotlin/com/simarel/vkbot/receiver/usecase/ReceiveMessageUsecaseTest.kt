package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.fixtures.FakePublishVkEventCommand
import com.simarel.vkbot.receiver.fixtures.FakeVkUpdateInputPortProvider
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortResponse
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiveMessageUsecaseTest {

    @Test
    fun `execute publishes the message of the update`() {
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(publishCommand)
        val message = FakeVoProvider.createMessage()
        val request = FakeVkUpdateInputPortProvider.createRequest(
            type = VkUpdateType.MESSAGE_NEW,
            message = message,
        )

        val result = usecase.execute(request)

        assertEquals(VkUpdateInputPortResponse, result)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(message, publishCommand.executeCalls.first().message)
    }

    @Test
    fun `execute without a message publishes nothing`() {
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(publishCommand)
        val request = FakeVkUpdateInputPortProvider.createRequest(type = VkUpdateType.UNKNOWN)

        val result = usecase.execute(request)

        assertEquals(VkUpdateInputPortResponse, result)
        assertTrue(publishCommand.executeCalls.isEmpty())
    }
}
