package com.simarel.vkbot.processor.command.knowledge

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.UserKnowledgeRequestedPayload
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

interface RequestKnowledgeExtractionCommand :
    Command<
        RequestKnowledgeExtractionCommand.Request,
        RequestKnowledgeExtractionCommand.Response,
        > {
    data class Request(val message: Message) : CommandRequest
    object Response : CommandResponse
}

@ApplicationScoped
@Suppress("LongParameterList", "TooGenericExceptionCaught")
class RequestKnowledgeExtractionCommandImpl(
    private val publishEventCommand: PublishEventCommand,
    private val hasPendingKnowledgeBatchPort: HasPendingKnowledgeBatchPort,
    @ConfigProperty(name = "knowledge.enabled", defaultValue = "true")
    private val enabled: Boolean,
    @ConfigProperty(name = "knowledge.batch-size", defaultValue = "25")
    private val batchSize: Int,
    @ConfigProperty(name = "knowledge.batch-interval", defaultValue = "30m")
    private val batchInterval: Duration,
    @ConfigProperty(name = "knowledge.excluded-chat-ids", defaultValue = "")
    excludedChatIdsConfig: String,
    @ConfigProperty(name = "vk.bot.id")
    private val botId: Long,
) : RequestKnowledgeExtractionCommand {

    private val logger: System.Logger = System.getLogger(RequestKnowledgeExtractionCommandImpl::class.java.name)

    private val excludedChatIds: Set<Long> = excludedChatIdsConfig.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::toLong)
        .toSet()

    override fun execute(
        request: RequestKnowledgeExtractionCommand.Request,
    ): RequestKnowledgeExtractionCommand.Response {
        val message = request.message
        val peerId = message.peerId.value
        return try {
            trigger(message)
            RequestKnowledgeExtractionCommand.Response
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: RuntimeException) {
            logger.log(
                System.Logger.Level.WARNING,
                "Knowledge extraction trigger failed for peerId=$peerId: ${e.javaClass.simpleName}",
            )
            RequestKnowledgeExtractionCommand.Response
        }
    }

    private fun trigger(message: Message) {
        if (!enabled || !message.fromId.isHuman() || message.fromId.value == botId) {
            return
        }
        val peerId = message.peerId.value
        if (peerId in excludedChatIds) {
            return
        }
        val hasPending = hasPendingKnowledgeBatchPort.execute(
            HasPendingKnowledgeBatchPort.Request(
                peerId = peerId,
                minCount = batchSize,
                minInterval = batchInterval,
            ),
        ).hasPending
        if (!hasPending) {
            return
        }
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.USER_KNOWLEDGE_REQUESTED,
                payload = Payload(
                    UserKnowledgeRequestedPayload(
                        peerId = peerId,
                        upToConversationMessageId = message.conversationMessageId.value,
                    ),
                ),
            ),
        )
    }
}
