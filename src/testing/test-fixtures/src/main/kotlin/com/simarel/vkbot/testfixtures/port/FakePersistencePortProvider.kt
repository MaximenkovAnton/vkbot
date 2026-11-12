package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatSummaryMessagesPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredMessageByAuthorPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenDatesPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredSummariesInPeriodPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgeSummaryStatePort
import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

private val FIXED_FROM: OffsetDateTime = OffsetDateTime.parse("2026-11-01T00:00:00Z")
private const val KNOWLEDGE_BATCH_INTERVAL_MINUTES = 30L
private const val DEFAULT_USER_ID = 10L
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

    fun createHasPendingKnowledgeBatchRequest(
        peerId: Long = 20,
        minCount: Int = 25,
        minInterval: Duration = Duration.ofMinutes(KNOWLEDGE_BATCH_INTERVAL_MINUTES),
    ) = HasPendingKnowledgeBatchPort.Request(
        peerId = peerId,
        minCount = minCount,
        minInterval = minInterval,
    )

    fun createFindPendingKnowledgeBatchRequest(
        peerId: Long = 20,
        upToConversationMessageId: Long = 30,
        limit: Int = 25,
    ) = FindPendingKnowledgeBatchPort.Request(
        peerId = peerId,
        upToConversationMessageId = upToConversationMessageId,
        limit = limit,
    )

    fun createSaveUserFactsRequest(
        candidates: List<UserFactCandidate> = listOf(FakeModelProvider.createUserFactCandidate()),
    ) = SaveUserFactsPort.Request(candidates = candidates)

    fun createAdvanceExtractionCursorRequest(
        peerId: Long = 20,
        lastConversationMessageId: Long = 30,
        completedAt: OffsetDateTime = FIXED_FROM,
    ) = AdvanceExtractionCursorPort.Request(
        peerId = peerId,
        lastConversationMessageId = lastConversationMessageId,
        completedAt = completedAt,
    )

    fun createSaveUserKnowledgeSummaryRequest(
        userId: Long = 10,
        summaryText: String = "user summary",
    ) = SaveUserKnowledgeSummaryPort.Request(userId = userId, summaryText = summaryText)

    fun createSaveChatKnowledgeSummaryRequest(
        peerId: Long = 20,
        summaryText: String = "chat summary",
        firstConversationMessageId: Long = 1,
        lastConversationMessageId: Long = 7,
        messagesCovered: Int = 7,
    ) = SaveChatKnowledgeSummaryPort.Request(
        peerId = peerId,
        summaryText = summaryText,
        firstConversationMessageId = firstConversationMessageId,
        lastConversationMessageId = lastConversationMessageId,
        messagesCovered = messagesCovered,
    )

    fun createHasPendingKnowledgeBatchResponse(
        hasPending: Boolean = false,
    ) = HasPendingKnowledgeBatchPort.Response(hasPending = hasPending)

    fun createFindPendingKnowledgeBatchResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = FindPendingKnowledgeBatchPort.Response(messages = messages)

    fun createSaveUserFactsResponse(
        saved: Int = 1,
        skipped: Int = 0,
    ) = SaveUserFactsPort.Response(saved = saved, skipped = skipped)

    fun createAdvanceExtractionCursorResponse() = AdvanceExtractionCursorPort.Response()

    fun createSaveUserKnowledgeSummaryResponse() = SaveUserKnowledgeSummaryPort.Response()

    fun createSaveChatKnowledgeSummaryResponse() = SaveChatKnowledgeSummaryPort.Response()

    fun createFindChatKnowledgeSummaryRequest(
        peerId: Long = 20,
    ) = FindChatKnowledgeSummaryPort.Request(peerId = peerId)

    fun createFindChatKnowledgeSummaryResponse(
        knowledge: ChatKnowledge? = null,
    ) = FindChatKnowledgeSummaryPort.Response(knowledge = knowledge)

    fun createFindChatSummaryMessagesRequest(
        peerId: Long = 20,
        limit: Int = 100,
    ) = FindChatSummaryMessagesPort.Request(peerId = peerId, limit = limit)

    fun createFindChatSummaryMessagesResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = FindChatSummaryMessagesPort.Response(messages = messages)

    fun createFindUserKnowledgeRequest(
        userIds: List<FromId> = listOf(FakeVoProvider.createFromId(DEFAULT_USER_ID)),
    ) = FindUserKnowledgePort.Request(userIds = userIds)

    fun createFindUserKnowledgeResponse(
        knowledge: Map<FromId, UserKnowledge> = emptyMap(),
    ) = FindUserKnowledgePort.Response(knowledge = knowledge)

    fun createFindUserKnowledgeSummaryStateRequest(
        userId: Long = DEFAULT_USER_ID,
    ) = FindUserKnowledgeSummaryStatePort.Request(userId = userId)

    fun createFindUserKnowledgeSummaryStateResponse(
        summarizedAt: OffsetDateTime? = null,
        factsSinceSummary: Int = 0,
    ) = FindUserKnowledgeSummaryStatePort.Response(
        summarizedAt = summarizedAt,
        factsSinceSummary = factsSinceSummary,
    )
}
