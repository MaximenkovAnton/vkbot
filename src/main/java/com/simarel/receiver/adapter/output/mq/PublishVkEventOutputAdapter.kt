package com.simarel.receiver.adapter.output.mq

import com.simarel.receiver.domain.vo.VkCallbackEvent
import com.simarel.receiver.port.output.PublishVkEventOutputPort
import com.simarel.receiver.port.output.PublishVkEventOutputPortRequest
import com.simarel.receiver.port.output.PublishVkEventOutputPortResponse
import com.simarel.share.domain.Event
import io.quarkus.logging.Log
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata


@ApplicationScoped
class PublishVkEventOutputAdapter(
    @Channel("events-exchange") val emitter: Emitter<String>,
): PublishVkEventOutputPort {
    val response = PublishVkEventOutputPortResponse()
    override fun execute(request: PublishVkEventOutputPortRequest): PublishVkEventOutputPortResponse {
        val routingKey = mapEventTypeToRoutingKey(request.vkEvent.type())
        if(routingKey == null) return response

        val metadata = OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey(routingKey.name)
            .build()

        emitter.send(Message.of(request.vkEvent.value.toString(), Metadata.of(metadata)))
        return response
    }

    fun mapEventTypeToRoutingKey(type: VkCallbackEvent): Event? {
        return when(type){
            VkCallbackEvent.MESSAGE_NEW -> Event.MESSAGE_RECEIVED
            else -> {
                Log.error("No event mapping for type: $type")
                null
            }
        }
    }
}