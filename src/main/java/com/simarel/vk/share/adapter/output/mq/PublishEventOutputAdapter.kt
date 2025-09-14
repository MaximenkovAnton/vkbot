package com.simarel.vk.share.adapter.output.mq

import com.simarel.vk.receiver.domain.vo.VkCallbackEvent
import com.simarel.vk.share.port.output.PublishEventOutputPort
import com.simarel.vk.share.port.output.PublishEventOutputPortRequest
import com.simarel.vk.share.port.output.PublishEventOutputPortResponse
import com.simarel.vk.share.domain.Event
import io.quarkus.logging.Log
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata


@ApplicationScoped
class PublishEventOutputAdapter(
    @Channel("events-exchange") val emitter: Emitter<String>,
): PublishEventOutputPort {
    val response = PublishEventOutputPortResponse()
    override fun execute(request: PublishEventOutputPortRequest): PublishEventOutputPortResponse {
        val metadata = OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey(request.event.name)
            .build()

        emitter.send(Message.of(request.payload.value, Metadata.of(metadata)))
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