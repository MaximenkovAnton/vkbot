package com.simarel.vkbot.ai.service

import com.simarel.vkbot.ai.usecase.summary.GenerateSummaryUsecase
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.adapter.output.client.persistence.PersistenceService
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class ChatSummarizationService(
    private val generateSummaryUsecase: GenerateSummaryUsecase,
    private val publishEventCommand: PublishEventCommand,
    @RestClient private val persistenceClient: PersistenceService,
) {

    @Inject
    @ConfigProperty(name = "summary.enabled-chats", defaultValue = "")
    lateinit var enabledChatsConfig: String

    @Inject
    @ConfigProperty(name = "summary.threshold", defaultValue = "100")
    var summaryThreshold: Int = 100

    @Inject
    @ConfigProperty(name = "summary.batch-size", defaultValue = "100")
    var summaryBatchSize: Int = 100

    private val enabledChats: Set<Long> by lazy {
        enabledChatsConfig.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLong() }
            .toSet()
    }

    fun processNewMessage(peerId: Long, conversationMessageId: Long) {
        if (!isSummarizationEnabled(peerId)) {
            return
        }

        if (persistenceClient.hasPendingSummary(peerId)) {
            Log.debugf("Skipping summarization for peerId=%d - already have pending summary", peerId)
            return
        }

        val lastSummary = persistenceClient.findLastSummary(peerId)

        if (lastSummary?.status == SummaryStatus.PENDING) {
            return
        }

        val messages = fetchMessagesForSummary(peerId, lastSummary, conversationMessageId)
        if (messages.isEmpty()) {
            Log.debugf("No messages found for summary, peerId=%d", peerId)
            return
        }

        if (messages.size < summaryThreshold && lastSummary != null) {
            Log.debugf("Not enough messages for summary, peerId=%d, count=%d", peerId, messages.size)
            return
        }

        Log.infof("Creating summary for peerId=%d, messages=%d", peerId, messages.size)

        val firstMessageId = messages.first().conversationMessageId
        val lastMessageId = messages.last().conversationMessageId

        val summaryId = generateSummaryUsecase.createPendingSummary(peerId, firstMessageId, lastMessageId)

        try {
            val result = generateSummaryUsecase.generateSummary(messages)
            generateSummaryUsecase.saveCompletedSummary(summaryId, result)

            publishSummaryReadyEvent(peerId, result.fullSummary, firstMessageId, lastMessageId)
            Log.infof("Summary completed for peerId=%d", peerId)
        } catch (e: Exception) {
            Log.errorf("Failed to generate summary for peerId=%d: %s", peerId, e.message)
            generateSummaryUsecase.markAsFailed(summaryId)
        }
    }

    private fun fetchMessagesForSummary(
        peerId: Long,
        lastSummary: Summary?,
        currentMessageId: Long
    ): List<StoredMessage> {
        return if (lastSummary == null) {
            persistenceClient.findMessagesBefore(peerId, currentMessageId + 1, summaryBatchSize)
        } else {
            persistenceClient.findMessagesBetween(
                peerId,
                lastSummary.lastMessageId ?: 0,
                currentMessageId,
                summaryBatchSize
            )
        }
    }

    private fun publishSummaryReadyEvent(
        peerId: Long,
        fullSummary: String,
        firstMessageId: Long,
        lastMessageId: Long
    ) {
        try {
            val message = "📋 Суммаризация обсуждения:\n\n$fullSummary\n\n#суммаризация"

            publishEventCommand.execute(
                PublishEventRequest(
                    event = Event.SUMMARY_READY,
                    payload = Payload(
                        SummaryReadyPayload(
                            peerId = peerId,
                            messageText = message,
                            firstConversationMessageId = firstMessageId,
                            lastConversationMessageId = lastMessageId
                        )
                    )
                )
            )
        } catch (e: Exception) {
            Log.errorf("Failed to publish summary event for peerId=%d: %s", peerId, e.message)
        }
    }

    private fun isSummarizationEnabled(peerId: Long): Boolean {
        return peerId in enabledChats
    }

    data class SummaryReadyPayload(
        val peerId: Long,
        val messageText: String,
        val firstConversationMessageId: Long,
        val lastConversationMessageId: Long,
    )
}
