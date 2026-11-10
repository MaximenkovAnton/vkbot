package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.ai.command.common.MessageAuthorFormatter
import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateSummaryCommandImpl(
    private val summarizationPort: SummarizationOutputPort,
    private val createPendingSummaryPort: CreateStoredPendingSummaryPort,
    private val findMessagesBetweenPort: FindStoredMessagesBetweenPort,
    private val saveCompletedSummaryPort: SaveStoredCompletedSummaryPort,
    private val markSummaryAsFailedPort: MarkStoredSummaryFailedPort,
    private val messageAuthorFormatter: MessageAuthorFormatter,
) : GenerateSummaryCommand {

    @Suppress("TooGenericExceptionCaught")
    override fun execute(request: GenerateSummaryCommand.GenerateSummaryRequest): GenerateSummaryCommand.GenerateSummaryResponse {
        val summaryId = createPendingSummaryPort.execute(
            CreateStoredPendingSummaryPort.Request(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId,
            ),
        ).summaryId

        try {
            val messages = findMessagesBetweenPort.execute(
                FindStoredMessagesBetweenPort.Request(
                    peerId = request.peerId,
                    firstMessageId = request.firstMessageId,
                    lastMessageId = request.lastMessageId,
                    limit = 1000,
                ),
            ).messages

            if (messages.isEmpty()) {
                saveCompletedSummaryPort.execute(
                    SaveStoredCompletedSummaryPort.Request(summaryId, "Нет сообщений", "Нет сообщений для суммаризации"),
                )
                return GenerateSummaryCommand.GenerateSummaryResponse(summaryId)
            }

            val formattedMessages = messageAuthorFormatter.format(messages).joinToString("\n\n")
            val summaryResult = summarizationPort.execute(SummarizationRequest(formattedMessages))

            saveCompletedSummaryPort.execute(
                SaveStoredCompletedSummaryPort.Request(summaryId, summaryResult.shortSummary, summaryResult.fullSummary),
            )
            return GenerateSummaryCommand.GenerateSummaryResponse(summaryId)
        } catch (e: Exception) {
            markSummaryAsFailedPort.execute(MarkStoredSummaryFailedPort.Request(summaryId))
            throw e
        }
    }
}
