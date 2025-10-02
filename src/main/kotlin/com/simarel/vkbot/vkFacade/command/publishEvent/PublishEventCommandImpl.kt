package com.simarel.vkbot.vkFacade.command.publishEvent

import com.simarel.vkbot.share.port.output.PublishEventOutputPort
import com.simarel.vkbot.share.port.output.PublishEventOutputPortRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PublishEventCommandImpl(val publishEventOutputPort: PublishEventOutputPort) : PublishEventCommand {
    val response = PublishEventResponse()
    override fun execute(request: PublishEventRequest): PublishEventResponse {
        publishEventOutputPort.execute(
            PublishEventOutputPortRequest(
                event = request.event,
                payload = request.payload,
            )
        )
        return response
    }
}
