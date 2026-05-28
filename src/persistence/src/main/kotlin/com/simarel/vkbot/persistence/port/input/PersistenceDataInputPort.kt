package com.simarel.vkbot.persistence.port.input

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse
import java.util.UUID

interface PersistenceDataInputPort : InputPort<PersistenceDataRequest, PersistenceDataResponse>

sealed interface PersistenceDataRequest : InputPortRequest {
    data class FindMessagesBefore(
        val peerId: Long,
        val beforeConversationMessageId: Long,
        val limit: Int,
    ) : PersistenceDataRequest

    data class FindMessagesBetween(
        val peerId: Long,
        val firstMessageId: Long,
        val lastMessageId: Long,
        val limit: Int,
    ) : PersistenceDataRequest

    data class FindLastSummary(val peerId: Long) : PersistenceDataRequest
    data class HasPendingSummary(val peerId: Long) : PersistenceDataRequest
    data class CreatePendingSummary(
        val peerId: Long,
        val firstMessageId: Long,
        val lastMessageId: Long,
    ) : PersistenceDataRequest

    data class SaveCompletedSummary(
        val id: UUID,
        val shortSummary: String,
        val fullSummary: String,
    ) : PersistenceDataRequest

    data class MarkSummaryAsFailed(val id: UUID) : PersistenceDataRequest
    data class FindUserProfilesByIds(val ids: List<Long>) : PersistenceDataRequest
    data class FindGroupProfilesByIds(val ids: List<Long>) : PersistenceDataRequest
}

sealed interface PersistenceDataResponse : InputPortResponse {
    data class Messages(val messages: List<StoredMessage>) : PersistenceDataResponse
    data class OptionalSummary(val summary: Summary?) : PersistenceDataResponse
    data class PendingStatus(val hasPending: Boolean) : PersistenceDataResponse
    data class CreatedSummaryId(val id: UUID) : PersistenceDataResponse
    data object Empty : PersistenceDataResponse
    data class UserProfiles(val profiles: List<VkUserProfile>) : PersistenceDataResponse
    data class GroupProfiles(val profiles: List<VkGroupProfile>) : PersistenceDataResponse
}
