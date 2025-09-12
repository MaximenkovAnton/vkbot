package com.simarel.receiver.command.sendVkEvent

import com.simarel.receiver.port.output.PublishVkEventOutputPort
import com.simarel.receiver.port.output.PublishVkEventOutputPortRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SendVkEventCommandImpl(
    val sendVkEventOutputPort: PublishVkEventOutputPort
) : SendVkEventCommand {
    val response = PublishVkEventCommandResponse()
    override fun execute(request: PublishVkEventCommandRequest): PublishVkEventCommandResponse {
        sendVkEventOutputPort.execute(
            PublishVkEventOutputPortRequest(
                request.vkEvent
            )
        )
        return response
    }
}