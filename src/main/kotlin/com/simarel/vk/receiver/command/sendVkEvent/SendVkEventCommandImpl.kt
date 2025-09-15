package com.simarel.vk.receiver.command.sendVkEvent

import com.simarel.vk.receiver.domain.vo.VkCallbackEvent
import com.simarel.vk.share.port.output.PublishEventOutputPort
import com.simarel.vk.share.domain.Event
import com.simarel.vk.share.domain.vo.Payload
import com.simarel.vk.share.port.output.PublishEventOutputPortRequest
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SendVkEventCommandImpl(
    val publishEventOutputPort: PublishEventOutputPort
) : SendVkEventCommand {
    val response = PublishVkEventCommandResponse()
    override fun execute(request: PublishVkEventCommandRequest): PublishVkEventCommandResponse {
        val event = mapVkEventToEvent(request.vkEvent.type())
        if(event != null) {
            publishEventOutputPort.execute(
                request = PublishEventOutputPortRequest(
                    event = event,
                    payload = Payload(request.vkEvent.value.toString())
                )
            )
        }
        return response
    }

    fun mapVkEventToEvent(type: VkCallbackEvent): Event? {
        return when(type){
            VkCallbackEvent.MESSAGE_NEW -> Event.MESSAGE_RECEIVED
            else -> {
                Log.error("No event mapping for type: $type")
                null
            }
        }
    }
}