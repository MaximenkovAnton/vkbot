package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ProcessSummaryCommandImpl(
    private val hasPendingSummaryPort: HasStoredPendingSummaryPort,
    private val findLastSummaryPort: FindLastStoredSummaryPort,
    private val findMessagesBeforePort: FindStoredMessagesBeforePort,
    private val findMessagesBetweenPort: FindStoredMessagesBetweenPort,
    private val generateSummaryCommand: GenerateSummaryCommand,
    @ConfigProperty(name = "summary.enabled-chats", defaultValue = "")
    private val enabledChatsConfig: String,
    @ConfigProperty(name = "summary.threshold", defaultValue = "100")
    private val summaryThreshold: Int,
    @ConfigProperty(name = "summary.batch-size", defaultValue = "100")
    private val summaryBatchSize: Int,
) : ProcessSummaryCommand {

    private val logger = System.getLogger(ProcessSummaryCommandImpl::class.java.name)

    private val enabledChats: Set<Long> by lazy {
        enabledChatsConfig.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLong() }
            .toSet()
    }

    override fun execute(request: ProcessSummaryCommand.ProcessSummaryRequest): ProcessSummaryCommand.ProcessSummaryResponse {
        if (!isSummarizationEnabled(request.peerId)) {
            logger.log(System.Logger.Level.DEBUG, "Summarization disabled for peerId=${request.peerId}")
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        if (hasPendingSummaryPort.execute(
                HasStoredPendingSummaryPort.Request(request.peerId),
            ).hasPending
        ) {
            logger.log(
                System.Logger.Level.DEBUG,
                "Skipping summarization for peerId=${request.peerId} - already have pending summary",
            )
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        val lastSummaryResponse = findLastSummaryPort.execute(
            FindLastStoredSummaryPort.Request(request.peerId),
        )
        val lastSummary = lastSummaryResponse.summary

        if (lastSummary?.status == SummaryStatus.PENDING) {
            logger.log(
                System.Logger.Level.WARNING,
                "Last summary for peerId=${request.peerId} is stuck in PENDING status, skipping",
            )
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        val messages = fetchMessagesForSummary(request.peerId, lastSummary, request.conversationMessageId)
        if (messages.isEmpty()) {
            logger.log(System.Logger.Level.DEBUG, "No messages found for summary, peerId=${request.peerId}")
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        if (messages.size < summaryThreshold && lastSummary != null) {
            logger.log(
                System.Logger.Level.DEBUG,
                "Not enough messages for summary, peerId=${request.peerId}, " +
                    "count=${messages.size}, threshold=$summaryThreshold",
            )
            return ProcessSummaryCommand.ProcessSummaryResponse
        }

        logger.log(
            System.Logger.Level.INFO,
            "Creating summary for peerId=${request.peerId}, messages=${messages.size}",
        )

        val firstMessageId = messages.first().conversationMessageId
        val lastMessageId = messages.last().conversationMessageId

        try {
            val result = generateSummaryCommand.execute(
                GenerateSummaryCommand.GenerateSummaryRequest(request.peerId, firstMessageId, lastMessageId),
            )
            logger.log(
                System.Logger.Level.INFO,
                "Summary completed for peerId=${request.peerId}, summaryId=${result.summaryId}",
            )
        } catch (e: Exception) {
            logger.log(
                System.Logger.Level.ERROR,
                "Failed to generate summary for peerId=${request.peerId}: ${e.message}",
                e,
            )
        }

        return ProcessSummaryCommand.ProcessSummaryResponse
    }

    private fun fetchMessagesForSummary(
        peerId: Long,
        lastSummary: com.simarel.vkbot.share.domain.model.Summary?,
        currentMessageId: Long,
    ): List<com.simarel.vkbot.share.domain.model.StoredMessage> = if (lastSummary == null) {
        findMessagesBeforePort.execute(
            FindStoredMessagesBeforePort.Request(peerId, currentMessageId + 1, summaryBatchSize),
        ).messages
    } else {
        findMessagesBetweenPort.execute(
            FindStoredMessagesBetweenPort.Request(
                peerId,
                lastSummary.lastMessageId ?: 0,
                currentMessageId,
                summaryBatchSize,
            ),
        ).messages
    }

    private fun isSummarizationEnabled(peerId: Long): Boolean = peerId in enabledChats
}
