package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import java.util.UUID

object PersistenceDataRequestProvider {

    fun createFindMessagesBeforeRequest(
        peerId: Long = 1,
        beforeConversationMessageId: Long = 2,
        limit: Int = 3,
    ) = PersistenceDataRequest.FindMessagesBefore(
        peerId = peerId,
        beforeConversationMessageId = beforeConversationMessageId,
        limit = limit,
    )

    fun createFindMessagesBetweenRequest(
        peerId: Long = 1,
        firstMessageId: Long = 2,
        lastMessageId: Long = 3,
        limit: Int = 4,
    ) = PersistenceDataRequest.FindMessagesBetween(
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
        limit = limit,
    )

    fun createFindLastSummaryRequest(peerId: Long = 1) = PersistenceDataRequest.FindLastSummary(peerId = peerId)

    fun createHasPendingSummaryRequest(peerId: Long = 1) = PersistenceDataRequest.HasPendingSummary(peerId = peerId)

    fun createCreatePendingSummaryRequest(
        peerId: Long = 1,
        firstMessageId: Long = 2,
        lastMessageId: Long = 3,
    ) = PersistenceDataRequest.CreatePendingSummary(
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
    )

    fun createSaveCompletedSummaryRequest(
        summaryId: UUID = UUID.randomUUID(),
        shortSummary: String = "short summary",
        fullSummary: String = "full summary",
    ) = PersistenceDataRequest.SaveCompletedSummary(
        id = summaryId,
        shortSummary = shortSummary,
        fullSummary = fullSummary,
    )

    fun createMarkSummaryFailedRequest(
        summaryId: UUID = UUID.randomUUID(),
    ) = PersistenceDataRequest.MarkSummaryAsFailed(id = summaryId)

    fun createFindUserProfilesByIdsRequest(
        ids: List<Long> = emptyList(),
    ) = PersistenceDataRequest.FindUserProfilesByIds(ids = ids)

    fun createFindGroupProfilesByIdsRequest(
        ids: List<Long> = emptyList(),
    ) = PersistenceDataRequest.FindGroupProfilesByIds(ids = ids)
}
