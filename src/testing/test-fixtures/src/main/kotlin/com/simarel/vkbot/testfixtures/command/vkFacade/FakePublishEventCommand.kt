package com.simarel.vkbot.testfixtures.command.vkFacade

import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakePublishEventCommand : PublishEventCommand {
    val executeCalls = ConcurrentLinkedQueue<PublishEventRequest>()
    private val response = PublishEventResponse()

    override fun execute(request: PublishEventRequest): PublishEventResponse {
        executeCalls.add(request)
        return response
    }
}
