package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import java.time.OffsetDateTime
import java.util.UUID

object PersistenceDataResponseProvider {

    fun createMessagesResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = PersistenceDataResponse.Messages(messages = messages)

    fun createOptionalMessageResponse(
        message: StoredMessage? = null,
    ) = PersistenceDataResponse.OptionalMessage(message = message)

    fun createSummariesResponse(
        summaries: List<Summary> = emptyList(),
    ) = PersistenceDataResponse.Summaries(summaries = summaries)

    fun createOptionalSummaryResponse(
        summary: Summary? = null,
    ) = PersistenceDataResponse.OptionalSummary(summary = summary)

    fun createPendingStatusResponse(
        hasPending: Boolean = false,
    ) = PersistenceDataResponse.PendingStatus(hasPending = hasPending)

    fun createCreatedSummaryIdResponse(
        id: UUID = UUID.randomUUID(),
    ) = PersistenceDataResponse.CreatedSummaryId(id = id)

    fun createEmptyResponse() = PersistenceDataResponse.Empty

    fun createUserProfilesResponse(
        profiles: List<VkUserProfile> = emptyList(),
    ) = PersistenceDataResponse.UserProfiles(profiles = profiles)

    fun createGroupProfilesResponse(
        profiles: List<VkGroupProfile> = emptyList(),
    ) = PersistenceDataResponse.GroupProfiles(profiles = profiles)

    fun createLongPollTsResponse(
        ts: String? = null,
    ) = PersistenceDataResponse.LongPollTs(ts = ts)

    fun createPeerIdsResponse(
        peerIds: Set<Long> = setOf(2_000_000_001),
    ) = PersistenceDataResponse.PeerIds(peerIds = peerIds)

    fun createClaimedResponse(
        claimed: Boolean = true,
    ) = PersistenceDataResponse.Claimed(claimed = claimed)

    fun createSavedUserFactsResponse(
        saved: Int = 1,
        skipped: Int = 0,
    ) = PersistenceDataResponse.SavedUserFacts(saved = saved, skipped = skipped)

    fun createChatAuthorsResponse(
        authors: List<ChatAuthor> = emptyList(),
    ) = PersistenceDataResponse.ChatAuthors(authors = authors)

    fun createStatisticsResponse(
        statistics: UserStatistics = FakeModelProvider.createUserStatistics(),
    ) = PersistenceDataResponse.Statistics(statistics = statistics)

    fun createProfileHistoryResponse(
        entries: List<ProfileHistoryEntry> = emptyList(),
    ) = PersistenceDataResponse.ProfileHistory(entries = entries)

    fun createChatKnowledgeSummaryResponse(
        knowledge: ChatKnowledge? = null,
    ) = PersistenceDataResponse.ChatKnowledgeSummary(knowledge = knowledge)

    fun createUserKnowledgeByUserIdsResponse(
        knowledge: Map<Long, UserKnowledge> = emptyMap(),
    ) = PersistenceDataResponse.UserKnowledgeByUserIds(knowledge = knowledge)

    fun createUserKnowledgeSummaryStateResponse(
        summarizedAt: OffsetDateTime? = null,
        factsSinceSummary: Int = 0,
    ) = PersistenceDataResponse.UserKnowledgeSummaryState(
        summarizedAt = summarizedAt,
        factsSinceSummary = factsSinceSummary,
    )

    fun createUserFactsResponse(
        facts: List<UserFact> = emptyList(),
    ) = PersistenceDataResponse.UserFacts(facts = facts)
}
