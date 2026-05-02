package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortResponse
import com.simarel.vkbot.ai.port.output.persistence.SummaryPersistenceOutputPort
import com.simarel.vkbot.share.domain.model.SummaryStatus
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ProcessSummaryUsecase(
    private val generateSummaryInputPort: GenerateSummaryInputPort,
    private val persistencePort: SummaryPersistenceOutputPort,
    @ConfigProperty(name = "summary.enabled-chats", defaultValue = "")
    private val enabledChatsConfig: String,
    @ConfigProperty(name = "summary.threshold", defaultValue = "100")
    private val summaryThreshold: Int,
    @ConfigProperty(name = "summary.batch-size", defaultValue = "100")
    private val summaryBatchSize: Int,
) : ProcessSummaryInputPort {

    private val enabledChats: Set<Long> by lazy {
        enabledChatsConfig.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLong() }
            .toSet()
    }

    override fun execute(request: ProcessSummaryInputPortRequest): ProcessSummaryInputPortResponse {
        if (!isSummarizationEnabled(request.peerId)) {
            return ProcessSummaryInputPortResponse
        }

        if (persistencePort.hasPendingSummary(request.peerId)) {
            Log.debugf("Skipping summarization for peerId=%d - already have pending summary", request.peerId)
            return ProcessSummaryInputPortResponse
        }

        val lastSummary = persistencePort.findLastSummary(request.peerId)

        if (lastSummary?.status == SummaryStatus.PENDING) {
            return ProcessSummaryInputPortResponse
        }

        val messages = fetchMessagesForSummary(request.peerId, lastSummary, request.conversationMessageId)
        if (messages.isEmpty()) {
            Log.debugf("No messages found for summary, peerId=%d", request.peerId)
            return ProcessSummaryInputPortResponse
        }

        if (messages.size < summaryThreshold && lastSummary != null) {
            Log.debugf("Not enough messages for summary, peerId=%d, count=%d", request.peerId, messages.size)
            return ProcessSummaryInputPortResponse
        }

        Log.infof("Creating summary for peerId=%d, messages=%d", request.peerId, messages.size)

        val firstMessageId = messages.first().conversationMessageId
        val lastMessageId = messages.last().conversationMessageId

        try {
            val result = generateSummaryInputPort.execute(
                GenerateSummaryInputPortRequest(request.peerId, firstMessageId, lastMessageId)
            )

            Log.infof("Summary completed for peerId=%d, summaryId=%s", request.peerId, result.summaryId)
        } catch (e: Exception) {
            Log.errorf("Failed to generate summary for peerId=%d: %s", request.peerId, e.message)
        }

        return ProcessSummaryInputPortResponse
    }

    private fun fetchMessagesForSummary(
        peerId: Long,
        lastSummary: com.simarel.vkbot.share.domain.model.Summary?,
        currentMessageId: Long
    ): List<com.simarel.vkbot.share.domain.model.StoredMessage> {
        return if (lastSummary == null) {
            persistencePort.findMessagesBefore(peerId, currentMessageId + 1, summaryBatchSize)
        } else {
            persistencePort.findMessagesBetween(
                peerId,
                lastSummary.lastMessageId ?: 0,
                currentMessageId,
                summaryBatchSize
            )
        }
    }

    private fun isSummarizationEnabled(peerId: Long): Boolean {
        return peerId in enabledChats
    }
}
