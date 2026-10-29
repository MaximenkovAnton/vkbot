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
}
