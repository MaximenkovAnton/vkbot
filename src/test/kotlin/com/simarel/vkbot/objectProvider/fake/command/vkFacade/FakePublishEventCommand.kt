package com.simarel.vkbot.objectProvider.fake.command.vkFacade

import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakePublishEventCommand : PublishEventCommand {
    val executeCalls = ConcurrentLinkedQueue<PublishEventRequest>()
    private val response = PublishEventResponse()

    override fun execute(request: PublishEventRequest): PublishEventResponse {
        executeCalls.add(request)
        return response
    }
}
