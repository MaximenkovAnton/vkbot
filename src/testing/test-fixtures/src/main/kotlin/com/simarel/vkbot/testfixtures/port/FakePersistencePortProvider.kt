package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredMessageByAuthorPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenDatesPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredSummariesInPeriodPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.time.OffsetDateTime
import java.util.UUID

private val FIXED_FROM: OffsetDateTime = OffsetDateTime.parse("2026-11-01T00:00:00Z")
private val FIXED_TO: OffsetDateTime = OffsetDateTime.parse("2026-11-07T23:59:59.999999999Z")

@Suppress("TooManyFunctions")
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

    fun createFindLastStoredMessageByAuthorRequest(
        peerId: Long = 1,
        fromId: Long = 2,
        beforeConversationMessageId: Long = 3,
    ) = FindLastStoredMessageByAuthorPort.Request(
        peerId = peerId,
        fromId = fromId,
        beforeConversationMessageId = beforeConversationMessageId,
    )

    fun createFindStoredMessagesBetweenDatesRequest(
        peerId: Long = 1,
        from: OffsetDateTime = FIXED_FROM,
        to: OffsetDateTime = FIXED_TO,
        limit: Int = 4,
    ) = FindStoredMessagesBetweenDatesPort.Request(
        peerId = peerId,
        from = from,
        to = to,
        limit = limit,
    )

    fun createFindStoredSummariesInPeriodRequest(
        peerId: Long = 1,
        from: OffsetDateTime = FIXED_FROM,
        to: OffsetDateTime = FIXED_TO,
    ) = FindStoredSummariesInPeriodPort.Request(
        peerId = peerId,
        from = from,
        to = to,
    )

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

    fun createFindChatAuthorsRequest(
        peerId: Long = 2_000_000_001,
    ) = FindChatAuthorsPort.Request(peerId = FakeVoProvider.createPeerId(peerId))

    fun createFindStoredUserStatisticsRequest(
        userId: Long = 1,
    ) = FindStoredUserStatisticsPort.Request(userId = FakeVoProvider.createFromId(userId))

    fun createFindStoredUserProfileHistoryRequest(
        profileId: Long = 1,
        limit: Int = 10,
    ) = FindStoredUserProfileHistoryPort.Request(
        profileId = FakeVoProvider.createFromId(profileId),
        limit = limit,
    )
}
