package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import java.util.UUID

object FakePersistencePortProvider {

    fun createFindMessagesBeforeRequest(
        peerId: Long = 1,
        beforeConversationMessageId: Long = 2,
        limit: Int = 3,
    ) = FindStoredMessagesBeforePort.Request(
        peerId = peerId,
        beforeConversationMessageId = beforeConversationMessageId,
        limit = limit,
    )

    fun createFindMessagesBetweenRequest(
        peerId: Long = 1,
        firstMessageId: Long = 2,
        lastMessageId: Long = 3,
        limit: Int = 4,
    ) = FindStoredMessagesBetweenPort.Request(
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
        limit = limit,
    )

    fun createFindLastSummaryRequest(peerId: Long = 1) = FindLastStoredSummaryPort.Request(peerId = peerId)

    fun createHasPendingSummaryRequest(peerId: Long = 1) = HasStoredPendingSummaryPort.Request(peerId = peerId)

    fun createCreatePendingSummaryRequest(
        peerId: Long = 1,
        firstMessageId: Long = 2,
        lastMessageId: Long = 3,
    ) = CreateStoredPendingSummaryPort.Request(
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
    )

    fun createSaveCompletedSummaryRequest(
        summaryId: UUID = UUID.randomUUID(),
        shortSummary: String = "short summary",
        fullSummary: String = "full summary",
    ) = SaveStoredCompletedSummaryPort.Request(
        summaryId = summaryId,
        shortSummary = shortSummary,
        fullSummary = fullSummary,
    )

    fun createMarkSummaryFailedRequest(
        summaryId: UUID = UUID.randomUUID(),
    ) = MarkStoredSummaryFailedPort.Request(summaryId = summaryId)

    fun createFindUserProfilesByIdsRequest(
        ids: List<FromId> = emptyList(),
    ) = FindStoredUserProfilesByIdsPort.Request(ids = ids)

    fun createFindGroupProfilesByIdsRequest(
        ids: List<FromId> = emptyList(),
    ) = FindStoredGroupProfilesByIdsPort.Request(ids = ids)
}
