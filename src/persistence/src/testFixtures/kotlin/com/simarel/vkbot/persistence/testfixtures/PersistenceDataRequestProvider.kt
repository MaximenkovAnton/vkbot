package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

private val FIXED_FROM: OffsetDateTime = OffsetDateTime.parse("2026-11-01T00:00:00Z")
private val FIXED_TO: OffsetDateTime = OffsetDateTime.parse("2026-11-07T23:59:59.999999999Z")

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

    fun createFindLastMessageByAuthorRequest(
        peerId: Long = 1,
        fromId: Long = 2,
        beforeConversationMessageId: Long = 3,
    ) = PersistenceDataRequest.FindLastMessageByAuthor(
        peerId = peerId,
        fromId = fromId,
        beforeConversationMessageId = beforeConversationMessageId,
    )

    fun createFindMessagesBetweenDatesRequest(
        peerId: Long = 1,
        from: OffsetDateTime = FIXED_FROM,
        to: OffsetDateTime = FIXED_TO,
        limit: Int = 4,
    ) = PersistenceDataRequest.FindMessagesBetweenDates(
        peerId = peerId,
        from = from,
        to = to,
        limit = limit,
    )

    fun createFindSummariesInPeriodRequest(
        peerId: Long = 1,
        from: OffsetDateTime = FIXED_FROM,
        to: OffsetDateTime = FIXED_TO,
    ) = PersistenceDataRequest.FindSummariesInPeriod(
        peerId = peerId,
        from = from,
        to = to,
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

    fun createFindLongPollTsRequest() = PersistenceDataRequest.FindLongPollTs

    fun createSaveLongPollTsRequest(
        ts: String = "ts-1",
    ) = PersistenceDataRequest.SaveLongPollTs(ts = ts)

    fun createFindUserProfilesByBirthDateRequest(
        day: Int = 12,
        month: Int = 9,
    ) = PersistenceDataRequest.FindUserProfilesByBirthDate(day = day, month = month)

    fun createFindUserChatPeerIdsRequest(
        userId: Long = 1_234_567,
    ) = PersistenceDataRequest.FindUserChatPeerIds(userId = userId)

    fun createFindGroupChatPeerIdsRequest() = PersistenceDataRequest.FindGroupChatPeerIds

    fun createClaimDailyGreetingRequest(
        date: LocalDate = LocalDate.parse("2026-09-13"),
        peerId: Long = 2_000_000_001,
    ) = PersistenceDataRequest.ClaimDailyGreeting(date = date, peerId = peerId)
}
