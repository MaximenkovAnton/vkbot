package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.adapter.output.ai.summarization.SummaryResponse
import com.simarel.vkbot.ai.command.summary.GenerateSummaryCommand
import com.simarel.vkbot.ai.command.summary.ProcessSummaryCommand
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortResponse
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationResponse
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import java.util.UUID

object FakeSummaryProvider {

    fun createSummarizationRequest(
        messages: String = "Ivan Petrov: hello",
    ) = SummarizationRequest(messages)

    fun createSummarizationResponse(
        shortSummary: String = "short summary",
        fullSummary: String = "full summary",
    ) = SummarizationResponse(
        shortSummary = shortSummary,
        fullSummary = fullSummary,
    )

    fun createSummarizationServiceResponse(
        shortSummary: String = "short summary",
        fullSummary: String = "full summary",
    ) = SummaryResponse(
        shortSummary = shortSummary,
        fullSummary = fullSummary,
    )

    fun createProcessSummaryCommandRequest(
        peerId: Long = 100,
        conversationMessageId: Long = 20,
    ) = ProcessSummaryCommand.ProcessSummaryRequest(
        peerId = peerId,
        conversationMessageId = conversationMessageId,
    )

    fun createGenerateSummaryCommandRequest(
        peerId: Long = 100,
        firstMessageId: Long = 10,
        lastMessageId: Long = 20,
    ) = GenerateSummaryCommand.GenerateSummaryRequest(
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
    )

    fun createGenerateSummaryCommandResponse(
        summaryId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    ) = GenerateSummaryCommand.GenerateSummaryResponse(summaryId)

    fun createProcessSummaryInputPortRequest(
        peerId: Long = 100,
        conversationMessageId: Long = 20,
    ) = ProcessSummaryInputPortRequest(
        peerId = peerId,
        conversationMessageId = conversationMessageId,
    )

    fun createGenerateSummaryInputPortRequest(
        peerId: Long = 100,
        firstMessageId: Long = 10,
        lastMessageId: Long = 20,
    ) = GenerateSummaryInputPortRequest(
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
    )

    fun createGenerateSummaryInputPortResponse(
        summaryId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    ) = GenerateSummaryInputPortResponse(summaryId)

    fun createHasPendingSummaryResponse(
        hasPending: Boolean = false,
    ) = HasStoredPendingSummaryPort.Response(hasPending)

    fun createFindLastSummaryResponse(
        summary: Summary? = null,
    ) = FindLastStoredSummaryPort.Response(summary)

    fun createFindMessagesBetweenResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = FindStoredMessagesBetweenPort.Response(messages)

    fun createCreatePendingSummaryResponse(
        summaryId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003"),
    ) = CreateStoredPendingSummaryPort.Response(summaryId)

    fun createSaveCompletedSummaryResponse() = SaveStoredCompletedSummaryPort.Response()

    fun createMarkSummaryFailedResponse() = MarkStoredSummaryFailedPort.Response()
}
