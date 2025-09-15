package com.simarel.vkbot.infrastructure.adapter.input.mq

import com.simarel.vkbot.share.adapter.input.EventProcessor
import io.quarkus.arc.All
import io.smallrye.common.annotation.RunOnVirtualThread
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@ApplicationScoped
class EventDispatcher {
    val processors: Map<String, EventProcessor>
    val response: CompletionStage<Void> = CompletableFuture.completedFuture(null)
    @Inject
    constructor(@All processors: MutableList<EventProcessor>){
        this.processors = processors.associateBy { it.event().name }
    }

    @Incoming("all-events-queue")
    @RunOnVirtualThread
    fun dispatch(message: Message<String>): CompletionStage<Void> {
        val routingKey = message.metadata?.get(IncomingRabbitMQMetadata::class.java)?.get()?.routingKey
        val processor = routingKey?.let { processors[it] }
        if (processor == null){
            return handleUnknownType(routingKey, message)
        }
        processor.process(message.payload)
        return response
    }

    private fun handleUnknownType(routingKey: String?, message: Message<String>): CompletionStage<Void> {
        System.err.println { "Unknown message (type: ${routingKey ?: "unknown"}): ${message.getPayload()}" }
        return response
    }
}