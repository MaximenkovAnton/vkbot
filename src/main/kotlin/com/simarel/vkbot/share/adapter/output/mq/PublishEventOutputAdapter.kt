package com.simarel.vkbot.share.adapter.output.mq

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.port.output.PublishEventOutputPort
import com.simarel.vkbot.share.port.output.PublishEventOutputPortRequest
import com.simarel.vkbot.share.port.output.PublishEventOutputPortResponse
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata

@ApplicationScoped
class PublishEventOutputAdapter(
    @Channel("events-exchange") val emitter: Emitter<String>,
    val objectMapper: ObjectMapper,
) : PublishEventOutputPort {
    val response = PublishEventOutputPortResponse()
    override fun execute(request: PublishEventOutputPortRequest): PublishEventOutputPortResponse {
        val metadata = OutgoingRabbitMQMetadata.Builder()
            .withRoutingKey(request.event.name)
            .build()

        emitter.send(
            Message.of(
                objectMapper.writeValueAsString(request.payload.value),
                Metadata.of(metadata)
            )
        )
        return response
    }
}
