package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommand
import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakePublishVkEventCommand : PublishVkEventCommand {
    val executeCalls = ConcurrentLinkedQueue<PublishVkEventCommandRequest>()
    private val response = PublishVkEventCommandResponse()

    override fun execute(request: PublishVkEventCommandRequest): PublishVkEventCommandResponse {
        executeCalls.add(request)
        return response
    }
}
