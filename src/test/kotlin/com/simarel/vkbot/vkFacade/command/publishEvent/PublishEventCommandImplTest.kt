package com.simarel.vkbot.vkFacade.command.publishEvent

import com.simarel.vkbot.objectProvider.fake.adapter.output.mq.FakePublishEventOutputPort
import com.simarel.vkbot.objectProvider.fake.command.vkFacade.FakePublishEventCommandProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PublishEventCommandImplTest {

    @Test
    fun `execute command successfully`() {
        val publishEventPort = FakePublishEventOutputPort()
        val command = PublishEventCommandImpl(publishEventPort)
        val request = FakePublishEventCommandProvider.createRequest()

        command.execute(request)

        assertEquals(1, publishEventPort.executeCalls.size)
        val call = publishEventPort.executeCalls.first()
        assertEquals(request.event, call.event)
        assertEquals(request.payload, call.payload)
    }
}
