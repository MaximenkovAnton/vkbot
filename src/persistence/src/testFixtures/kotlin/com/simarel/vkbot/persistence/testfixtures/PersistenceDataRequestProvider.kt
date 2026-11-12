package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import java.time.Duration
import java.time.LocalDate
import java.time.MonthDay
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

    fun createFindChatAuthorsRequest(
        peerId: Long = 2_000_000_001,
    ) = PersistenceDataRequest.FindChatAuthors(peerId = peerId)

    fun createFindUserStatisticsRequest(
        userId: Long = 1,
    ) = PersistenceDataRequest.FindUserStatistics(userId = userId)

    fun createFindUserProfileHistoryRequest(
        profileId: Long = 1,
        limit: Int = 10,
        offset: Int = 0,
    ) = PersistenceDataRequest.FindUserProfileHistory(
        profileId = profileId,
        limit = limit,
        offset = offset,
    )

    fun createHasPendingKnowledgeBatchRequest(
        peerId: Long = 10,
        minCount: Int = 25,
        minInterval: Duration = Duration.ofMinutes(30),
    ) = PersistenceDataRequest.HasPendingKnowledgeBatch(
        peerId = peerId,
        minCount = minCount,
        minInterval = minInterval,
    )

    fun createFindPendingKnowledgeBatchRequest(
        peerId: Long = 10,
        upToConversationMessageId: Long = 30,
        limit: Int = 25,
    ) = PersistenceDataRequest.FindPendingKnowledgeBatch(
        peerId = peerId,
        upToConversationMessageId = upToConversationMessageId,
        limit = limit,
    )

    fun createSaveUserFactsRequest(
        candidates: List<UserFactCandidate> = emptyList(),
    ) = PersistenceDataRequest.SaveUserFacts(candidates = candidates)

    fun createAdvanceKnowledgeExtractionCursorRequest(
        peerId: Long = 10,
        lastConversationMessageId: Long = 30,
        completedAt: OffsetDateTime = OffsetDateTime.parse("2026-11-01T00:00:00Z"),
    ) = PersistenceDataRequest.AdvanceKnowledgeExtractionCursor(
        peerId = peerId,
        lastConversationMessageId = lastConversationMessageId,
        completedAt = completedAt,
    )

    fun createSaveUserKnowledgeSummaryRequest(
        userId: Long = 10,
        summaryText: String = "summary",
    ) = PersistenceDataRequest.SaveUserKnowledgeSummary(userId = userId, summaryText = summaryText)

    fun createSaveChatKnowledgeSummaryRequest(
        peerId: Long = 10,
        summaryText: String = "chat summary",
        firstConversationMessageId: Long = 10,
        lastConversationMessageId: Long = 70,
        messagesCovered: Int = 7,
    ) = PersistenceDataRequest.SaveChatKnowledgeSummary(
        peerId = peerId,
        summaryText = summaryText,
        firstConversationMessageId = firstConversationMessageId,
        lastConversationMessageId = lastConversationMessageId,
        messagesCovered = messagesCovered,
    )

    fun createFindChatKnowledgeSummaryRequest(
        peerId: Long = 10,
    ) = PersistenceDataRequest.FindChatKnowledgeSummary(peerId = peerId)

    fun createFindChatSummaryMessagesRequest(
        peerId: Long = 10,
        limit: Int = 100,
    ) = PersistenceDataRequest.FindChatSummaryMessages(peerId = peerId, limit = limit)

    fun createFindUserKnowledgeRequest(
        userIds: List<Long> = emptyList(),
    ) = PersistenceDataRequest.FindUserKnowledge(userIds = userIds)

    fun createFindUserKnowledgeSummaryStateRequest(
        userId: Long = 10,
    ) = PersistenceDataRequest.FindUserKnowledgeSummaryState(userId = userId)

    fun createFindUserBirthdayFactsRequest(
        monthDay: MonthDay = MonthDay.of(5, 14),
    ) = PersistenceDataRequest.FindUserBirthdayFacts(monthDay = monthDay)
}
