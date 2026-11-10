package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
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
}
