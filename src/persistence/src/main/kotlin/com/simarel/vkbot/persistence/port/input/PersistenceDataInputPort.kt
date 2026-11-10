package com.simarel.vkbot.persistence.port.input

import com.simarel.vkbot.persistence.domain.profile.ProfileHistoryEntry
import com.simarel.vkbot.persistence.domain.statistics.UserStatistics
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

interface PersistenceDataInputPort {
    fun findMessagesBefore(request: PersistenceDataRequest.FindMessagesBefore): PersistenceDataResponse.Messages

    fun findMessagesBetween(request: PersistenceDataRequest.FindMessagesBetween): PersistenceDataResponse.Messages

    fun findLastMessageByAuthor(
        request: PersistenceDataRequest.FindLastMessageByAuthor,
    ): PersistenceDataResponse.OptionalMessage

    fun findMessagesBetweenDates(
        request: PersistenceDataRequest.FindMessagesBetweenDates,
    ): PersistenceDataResponse.Messages

    fun findSummariesInPeriod(
        request: PersistenceDataRequest.FindSummariesInPeriod,
    ): PersistenceDataResponse.Summaries

    fun findLastSummary(request: PersistenceDataRequest.FindLastSummary): PersistenceDataResponse.OptionalSummary

    fun hasPendingSummary(request: PersistenceDataRequest.HasPendingSummary): PersistenceDataResponse.PendingStatus

    fun createPendingSummary(
        request: PersistenceDataRequest.CreatePendingSummary,
    ): PersistenceDataResponse.CreatedSummaryId

    fun saveCompletedSummary(request: PersistenceDataRequest.SaveCompletedSummary): PersistenceDataResponse.Empty

    fun markSummaryAsFailed(request: PersistenceDataRequest.MarkSummaryAsFailed): PersistenceDataResponse.Empty

    fun findUserProfilesByIds(
        request: PersistenceDataRequest.FindUserProfilesByIds,
    ): PersistenceDataResponse.UserProfiles

    fun findGroupProfilesByIds(
        request: PersistenceDataRequest.FindGroupProfilesByIds,
    ): PersistenceDataResponse.GroupProfiles

    fun findLongPollTs(request: PersistenceDataRequest.FindLongPollTs): PersistenceDataResponse.LongPollTs

    fun saveLongPollTs(request: PersistenceDataRequest.SaveLongPollTs): PersistenceDataResponse.Empty

    fun findUserProfilesByBirthDate(
        request: PersistenceDataRequest.FindUserProfilesByBirthDate,
    ): PersistenceDataResponse.UserProfiles

    fun findUserChatPeerIds(request: PersistenceDataRequest.FindUserChatPeerIds): PersistenceDataResponse.PeerIds

    fun findGroupChatPeerIds(request: PersistenceDataRequest.FindGroupChatPeerIds): PersistenceDataResponse.PeerIds

    fun claimDailyGreeting(request: PersistenceDataRequest.ClaimDailyGreeting): PersistenceDataResponse.Claimed

    fun findUserProfileHistory(
        request: PersistenceDataRequest.FindUserProfileHistory,
    ): PersistenceDataResponse.ProfileHistory

    fun findUserStatistics(
        request: PersistenceDataRequest.FindUserStatistics,
    ): PersistenceDataResponse.Statistics
}

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
    data class FindLastMessageByAuthor(
        val peerId: Long,
        val fromId: Long,
        val beforeConversationMessageId: Long,
    ) : PersistenceDataRequest

    data class FindMessagesBetweenDates(
        val peerId: Long,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
        val limit: Int,
    ) : PersistenceDataRequest

    data class FindSummariesInPeriod(
        val peerId: Long,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
    ) : PersistenceDataRequest
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
    data object FindLongPollTs : PersistenceDataRequest
    data class SaveLongPollTs(val ts: String) : PersistenceDataRequest
    data class FindUserProfilesByBirthDate(val day: Int, val month: Int) : PersistenceDataRequest
    data class FindUserChatPeerIds(val userId: Long) : PersistenceDataRequest
    data object FindGroupChatPeerIds : PersistenceDataRequest
    data class ClaimDailyGreeting(val date: LocalDate, val peerId: Long) : PersistenceDataRequest
    data class FindUserProfileHistory(val profileId: Long, val limit: Int, val offset: Int) : PersistenceDataRequest
    data class FindUserStatistics(val userId: Long) : PersistenceDataRequest
}

sealed interface PersistenceDataResponse : InputPortResponse {
    data class Messages(val messages: List<StoredMessage>) : PersistenceDataResponse
    data class OptionalMessage(val message: StoredMessage?) : PersistenceDataResponse
    data class Summaries(val summaries: List<Summary>) : PersistenceDataResponse
    data class OptionalSummary(val summary: Summary?) : PersistenceDataResponse
    data class PendingStatus(val hasPending: Boolean) : PersistenceDataResponse
    data class CreatedSummaryId(val id: UUID) : PersistenceDataResponse
    data object Empty : PersistenceDataResponse
    data class UserProfiles(val profiles: List<VkUserProfile>) : PersistenceDataResponse
    data class GroupProfiles(val profiles: List<VkGroupProfile>) : PersistenceDataResponse
    data class LongPollTs(val ts: String?) : PersistenceDataResponse
    data class PeerIds(val peerIds: Set<Long>) : PersistenceDataResponse
    data class Claimed(val claimed: Boolean) : PersistenceDataResponse
    data class ProfileHistory(val entries: List<ProfileHistoryEntry>) : PersistenceDataResponse
    data class Statistics(val statistics: UserStatistics) : PersistenceDataResponse
}
