package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.ai.port.output.persistence.FindAiLastStoredSummaryPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredMessagesBeforePort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredMessagesBetweenPort
import com.simarel.vkbot.ai.port.output.persistence.HasAiStoredPendingSummaryPort
import com.simarel.vkbot.share.domain.model.SummaryStatus
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ProcessSummaryCommandImpl(
    private val hasPendingSummaryPort: HasAiStoredPendingSummaryPort,
    private val findLastSummaryPort: FindAiLastStoredSummaryPort,
    private val findMessagesBeforePort: FindAiStoredMessagesBeforePort,
    private val findMessagesBetweenPort: FindAiStoredMessagesBetweenPort,
    private val generateSummaryCommand: GenerateSummaryCommand,
    @ConfigProperty(name = "summary.enabled-chats", defaultValue = "")
    private val enabledChatsConfig: String,
    @ConfigProperty(name = "summary.threshold", defaultValue = "100")
    private val summaryThreshold: Int,
    @ConfigProperty(name = "summary.batch-size", defaultValue = "100")
    private val summaryBatchSize: Int,
) : ProcessSummaryCommand {

    private val enabledChats: Set<Long> by lazy {
        enabledChatsConfig.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLong() }
            .toSet()
    }

    override fun execute(request: ProcessSummaryCommand.ProcessSummaryRequest): ProcessSummaryCommand.ProcessSummaryResponse {
        if (!isSummarizationEnabled(request.peerId)) {
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        if (hasPendingSummaryPort.execute(
                HasAiStoredPendingSummaryPort.Request(request.peerId)
            ).hasPending
        ) {
            Log.debugf("Skipping summarization for peerId=%d - already have pending summary", request.peerId)
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        val lastSummaryResponse = findLastSummaryPort.execute(
            FindAiLastStoredSummaryPort.Request(request.peerId)
        )
        val lastSummary = lastSummaryResponse.summary

        if (lastSummary?.status == SummaryStatus.PENDING) {
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        val messages = fetchMessagesForSummary(request.peerId, lastSummary, request.conversationMessageId)
        if (messages.isEmpty()) {
            Log.debugf("No messages found for summary, peerId=%d", request.peerId)
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        if (messages.size < summaryThreshold && lastSummary != null) {
            Log.debugf("Not enough messages for summary, peerId=%d, count=%d", request.peerId, messages.size)
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        Log.infof("Creating summary for peerId=%d, messages=%d", request.peerId, messages.size)

        val firstMessageId = messages.first().conversationMessageId
        val lastMessageId = messages.last().conversationMessageId

        try {
            val result = generateSummaryCommand.execute(
                GenerateSummaryCommand.GenerateSummaryRequest(request.peerId, firstMessageId, lastMessageId)
            )
            Log.infof("Summary completed for peerId=%d, summaryId=%s", request.peerId, result.summaryId)
        } catch (e: Exception) {
            Log.errorf("Failed to generate summary for peerId=%d: %s", request.peerId, e.message)
        }

        return ProcessSummaryCommand.ProcessSummaryResponse
    }

    private fun fetchMessagesForSummary(
        peerId: Long,
        lastSummary: com.simarel.vkbot.share.domain.model.Summary?,
        currentMessageId: Long
    ): List<com.simarel.vkbot.share.domain.model.StoredMessage> {
        return if (lastSummary == null) {
            findMessagesBeforePort.execute(
                FindAiStoredMessagesBeforePort.Request(peerId, currentMessageId + 1, summaryBatchSize)
            ).messages
        } else {
            findMessagesBetweenPort.execute(
                FindAiStoredMessagesBetweenPort.Request(
                    peerId,
                    lastSummary.lastMessageId ?: 0,
                    currentMessageId,
                    summaryBatchSize
                )
            ).messages
        }
    }

    private fun isSummarizationEnabled(peerId: Long): Boolean {
        return peerId in enabledChats
    }
}
