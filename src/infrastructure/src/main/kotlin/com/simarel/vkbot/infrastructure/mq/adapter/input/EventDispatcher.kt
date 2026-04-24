package com.simarel.vkbot.infrastructure.mq.adapter.input

import com.simarel.vkbot.share.port.input.EventProcessor
import io.quarkus.arc.All
import io.quarkus.logging.Log
import io.smallrye.common.annotation.RunOnVirtualThread
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.concurrent.CompletionStage

@ApplicationScoped
class EventDispatcher {
    private val processors: Map<String, EventProcessor>

    @Inject
    constructor(@All processors: MutableList<EventProcessor>) {
        this.processors = processors.associateBy { it.event().name }
    }

    @Incoming("all-events-queue")
    @RunOnVirtualThread
    fun dispatch(message: Message<String>): CompletionStage<Void> {
        val routingKey = message.getMetadata(IncomingRabbitMQMetadata::class.java)
            .map { it.routingKey }
            .orElse(null)

        val processor = routingKey?.let { processors[it] }

        return if (processor == null) {
            handleUnknownType(routingKey, message)
            message.ack()
        } else {
            try {
                processor.process(message.payload)
                message.ack()
            } catch (e: Exception) {
                Log.error("Processing failed", e)
                message.nack(e)
            }
        }
    }

    private fun handleUnknownType(routingKey: String?, message: Message<String>) {
        Log.error("Unknown message (type: ${routingKey ?: "unknown"}): ${message.payload}")
    }
}
