package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.receiver.command.sendVkEvent.mapper.MessageMapper
import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.PublishEventOutputPort
import com.simarel.vkbot.share.port.output.PublishEventOutputPortRequest
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PublishVkEventCommandImpl(
    private val publishEventOutputPort: PublishEventOutputPort,
    private val messageMapper: MessageMapper,
) : PublishVkEventCommand {
    private val response = PublishVkEventCommandResponse()
    override fun execute(request: PublishVkEventCommandRequest): PublishVkEventCommandResponse {
        val event = mapVkEventToEvent(request.vkEvent.type())
        if (event != null) {
            val message = messageMapper.toDomain(request.vkEvent.value)
            publishEventOutputPort.execute(
                request = PublishEventOutputPortRequest(
                    event = event,
                    payload = Payload(message),
                ),
            )
        }
        return response
    }

    private fun mapVkEventToEvent(type: VkCallbackEvent): Event? = when (type) {
        VkCallbackEvent.MESSAGE_NEW -> Event.MESSAGE_NEW
        else -> {
            Log.error("No event mapping for type: $type")
            null
        }
    }
}
