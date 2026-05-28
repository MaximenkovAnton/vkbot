package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.PublishEventOutputPort
import com.simarel.vkbot.share.port.output.PublishEventOutputPortRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PublishVkEventCommandImpl(
    private val publishEventOutputPort: PublishEventOutputPort,
) : PublishVkEventCommand {
    private val response = PublishVkEventCommandResponse()

    override fun execute(request: PublishVkEventCommandRequest): PublishVkEventCommandResponse {
        publishEventOutputPort.execute(
            PublishEventOutputPortRequest(
                event = Event.MESSAGE_NEW,
                payload = Payload(request.message),
            ),
        )
        return response
    }
}
