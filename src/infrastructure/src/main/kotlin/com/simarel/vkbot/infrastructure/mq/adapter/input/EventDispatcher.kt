package com.simarel.vkbot.infrastructure.mq.adapter.input

import com.simarel.vkbot.infrastructure.config.DlqConfig
import com.simarel.vkbot.share.port.input.EventProcessor
import io.quarkus.arc.All
import io.quarkus.logging.Log
import io.smallrye.common.annotation.RunOnVirtualThread
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.Metadata
import java.util.concurrent.CompletionStage

@ApplicationScoped
class EventDispatcher(
    @All processors: MutableList<EventProcessor>,
    private val dlqConfig: DlqConfig,
) {
    private val processors: Map<String, EventProcessor> = processors.associateBy { it.event().name }
    private val levelConfigByRetryCount: Map<Int, DlqConfig.DlqLevel> =
        dlqConfig.levels().associateBy { it.retryCount() }

    @Inject
    @Channel("retry-5s-out")
    lateinit var retry5sEmitter: Emitter<String>

    @Inject
    @Channel("retry-30s-out")
    lateinit var retry30sEmitter: Emitter<String>

    @Inject
    @Channel("retry-1m-out")
    lateinit var retry1mEmitter: Emitter<String>

    @Inject
    @Channel("retry-15m-out")
    lateinit var retry15mEmitter: Emitter<String>

    @Inject
    @Channel("retry-1h-out")
    lateinit var retry1hEmitter: Emitter<String>

    @Inject
    @Channel("dlq-final-out")
    lateinit var dlqFinalEmitter: Emitter<String>

    private val emittersByQueueName: Map<String, Emitter<String>> by lazy {
        mapOf(
            "retry-5s" to retry5sEmitter,
            "retry-30s" to retry30sEmitter,
            "retry-1m" to retry1mEmitter,
            "retry-15m" to retry15mEmitter,
            "retry-1h" to retry1hEmitter,
            "dlq-final" to dlqFinalEmitter,
        )
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
                return message.ack()
            } catch (e: Exception) {
                Log.error("Processing failed", e)
                return handleRetry(message, routingKey)
            }
        }
    }

    private fun handleUnknownType(routingKey: String?, message: Message<String>) {
        Log.error("Unknown message (type: ${routingKey ?: "unknown"}): ${message.payload}")
    }

    private fun handleRetry(message: Message<String>, routingKey: String?): CompletionStage<Void> {
        val metadata = message.getMetadata(IncomingRabbitMQMetadata::class.java).orElse(null)
        val headerValue = metadata?.getHeader("x-retry-count", Integer::class.javaObjectType)
        val currentRetryCount: Int = headerValue?.orElse(null)?.toInt() ?: 0
        val nextRetryCount = currentRetryCount + 1

        val levelConfig = levelConfigByRetryCount[currentRetryCount]
            ?: levelConfigByRetryCount.values.maxByOrNull { it.retryCount() }!!

        val emitter = emittersByQueueName[levelConfig.queueName()]
            ?: throw IllegalStateException("No emitter configured for queue: ${levelConfig.queueName()}")

        val payload = message.payload
        val newMetadata = OutgoingRabbitMQMetadata.builder()
            .withHeader("x-retry-count", nextRetryCount)
            .withRoutingKey(routingKey ?: "")
            .build()

        emitter.send(Message.of(payload, Metadata.of(newMetadata)))
        Log.debug("Sent message to ${levelConfig.queueName()} with retry count $nextRetryCount")

        return message.ack()
    }
}
