package com.simarel.vkbot.persistence.usecase

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryStatus
import com.simarel.vkbot.persistence.domain.exception.SummaryNotFoundException
import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.persistence.port.output.persistence.ClaimDailyGreetingPort
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindLastMessageByAuthorPort
import com.simarel.vkbot.persistence.port.output.persistence.FindLastSummaryByPeerIdPort
import com.simarel.vkbot.persistence.port.output.persistence.FindLongPollTsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBeforePort
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenDatesPort
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenPort
import com.simarel.vkbot.persistence.port.output.persistence.FindSummariesInPeriodPort
import com.simarel.vkbot.persistence.port.output.persistence.FindSummaryByIdPort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByBirthDatePort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.HasPendingSummaryPort
import com.simarel.vkbot.persistence.port.output.persistence.InsertSummaryPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveLongPollTsPort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusAndSummariesPort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusPort
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.SummaryReadyPayload
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.domain.vo.PeerId
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID
import com.simarel.vkbot.share.domain.model.SummaryStatus as SharedSummaryStatus

@Suppress("LongParameterList")
@ApplicationScoped
open class PersistenceDataUsecase(
    private val findMessagesBeforePort: FindMessagesBeforePort,
    private val findMessagesBetweenPort: FindMessagesBetweenPort,
    private val findLastMessageByAuthorPort: FindLastMessageByAuthorPort,
    private val findMessagesBetweenDatesPort: FindMessagesBetweenDatesPort,
    private val findSummariesInPeriodPort: FindSummariesInPeriodPort,
    private val findLastSummaryByPeerIdPort: FindLastSummaryByPeerIdPort,
    private val hasPendingSummaryPort: HasPendingSummaryPort,
    private val insertSummaryPort: InsertSummaryPort,
    private val updateSummaryStatusAndSummariesPort: UpdateSummaryStatusAndSummariesPort,
    private val updateSummaryStatusPort: UpdateSummaryStatusPort,
    private val findSummaryByIdPort: FindSummaryByIdPort,
    private val findUserProfilesByIdsPort: FindUserProfilesByIdsPort,
    private val findGroupProfilesByIdsPort: FindGroupProfilesByIdsPort,
    private val findLongPollTsPort: FindLongPollTsPort,
    private val saveLongPollTsPort: SaveLongPollTsPort,
    private val findUserProfilesByBirthDatePort: FindUserProfilesByBirthDatePort,
    private val findUserChatPeerIdsPort: FindUserChatPeerIdsPort,
    private val findGroupChatPeerIdsPort: FindGroupChatPeerIdsPort,
    private val claimDailyGreetingPort: ClaimDailyGreetingPort,
    private val publishEventCommand: PublishEventCommand,
) : PersistenceDataInputPort {

    override fun findMessagesBefore(
        request: PersistenceDataRequest.FindMessagesBefore,
    ): PersistenceDataResponse.Messages = handleFindMessagesBefore(request)

    override fun findMessagesBetween(
        request: PersistenceDataRequest.FindMessagesBetween,
    ): PersistenceDataResponse.Messages = handleFindMessagesBetween(request)

    override fun findLastMessageByAuthor(
        request: PersistenceDataRequest.FindLastMessageByAuthor,
    ): PersistenceDataResponse.OptionalMessage = handleFindLastMessageByAuthor(request)

    override fun findMessagesBetweenDates(
        request: PersistenceDataRequest.FindMessagesBetweenDates,
    ): PersistenceDataResponse.Messages = handleFindMessagesBetweenDates(request)

    override fun findSummariesInPeriod(
        request: PersistenceDataRequest.FindSummariesInPeriod,
    ): PersistenceDataResponse.Summaries = handleFindSummariesInPeriod(request)

    override fun findLastSummary(
        request: PersistenceDataRequest.FindLastSummary,
    ): PersistenceDataResponse.OptionalSummary = handleFindLastSummary(request)

    override fun hasPendingSummary(
        request: PersistenceDataRequest.HasPendingSummary,
    ): PersistenceDataResponse.PendingStatus = handleHasPendingSummary(request)

    override fun createPendingSummary(
        request: PersistenceDataRequest.CreatePendingSummary,
    ): PersistenceDataResponse.CreatedSummaryId = handleCreatePendingSummary(request)

    override fun saveCompletedSummary(
        request: PersistenceDataRequest.SaveCompletedSummary,
    ): PersistenceDataResponse.Empty = handleSaveCompletedSummary(request)

    override fun markSummaryAsFailed(
        request: PersistenceDataRequest.MarkSummaryAsFailed,
    ): PersistenceDataResponse.Empty = handleMarkSummaryAsFailed(request)

    override fun findUserProfilesByIds(
        request: PersistenceDataRequest.FindUserProfilesByIds,
    ): PersistenceDataResponse.UserProfiles = handleFindUserProfilesByIds(request)

    override fun findGroupProfilesByIds(
        request: PersistenceDataRequest.FindGroupProfilesByIds,
    ): PersistenceDataResponse.GroupProfiles = handleFindGroupProfilesByIds(request)

    override fun findLongPollTs(
        request: PersistenceDataRequest.FindLongPollTs,
    ): PersistenceDataResponse.LongPollTs = PersistenceDataResponse.LongPollTs(
        findLongPollTsPort.execute(FindLongPollTsPort.FindLongPollTsRequest).ts,
    )

    override fun saveLongPollTs(request: PersistenceDataRequest.SaveLongPollTs): PersistenceDataResponse.Empty {
        saveLongPollTsPort.execute(SaveLongPollTsPort.SaveLongPollTsRequest(request.ts))
        return PersistenceDataResponse.Empty
    }

    override fun findUserProfilesByBirthDate(
        request: PersistenceDataRequest.FindUserProfilesByBirthDate,
    ): PersistenceDataResponse.UserProfiles = handleFindUserProfilesByBirthDate(request)

    override fun findUserChatPeerIds(
        request: PersistenceDataRequest.FindUserChatPeerIds,
    ): PersistenceDataResponse.PeerIds = handleFindUserChatPeerIds(request)

    override fun findGroupChatPeerIds(
        request: PersistenceDataRequest.FindGroupChatPeerIds,
    ): PersistenceDataResponse.PeerIds = handleFindGroupChatPeerIds()

    override fun claimDailyGreeting(
        request: PersistenceDataRequest.ClaimDailyGreeting,
    ): PersistenceDataResponse.Claimed = handleClaimDailyGreeting(request)

    private fun handleFindMessagesBefore(request: PersistenceDataRequest.FindMessagesBefore): PersistenceDataResponse.Messages {
        val messages = findMessagesBeforePort.execute(
            FindMessagesBeforePort.FindMessagesBeforeRequest(
                PeerId.of(request.peerId),
                ConversationMessageId.of(request.beforeConversationMessageId),
                request.limit,
            ),
        ).messages
        return PersistenceDataResponse.Messages(messages.map { it.toStoredMessage() })
    }

    private fun handleFindMessagesBetween(request: PersistenceDataRequest.FindMessagesBetween): PersistenceDataResponse.Messages {
        val messages = findMessagesBetweenPort.execute(
            FindMessagesBetweenPort.FindMessagesBetweenRequest(
                PeerId.of(request.peerId),
                ConversationMessageId.of(request.firstMessageId),
                ConversationMessageId.of(request.lastMessageId),
                request.limit,
            ),
        ).messages
        return PersistenceDataResponse.Messages(messages.map { it.toStoredMessage() })
    }

    private fun handleFindLastMessageByAuthor(
        request: PersistenceDataRequest.FindLastMessageByAuthor,
    ): PersistenceDataResponse.OptionalMessage {
        val message = findLastMessageByAuthorPort.execute(
            FindLastMessageByAuthorPort.Request(
                PeerId.of(request.peerId),
                FromId.of(request.fromId),
                ConversationMessageId.of(request.beforeConversationMessageId),
            ),
        ).message?.toStoredMessage()
        return PersistenceDataResponse.OptionalMessage(message)
    }

    private fun handleFindMessagesBetweenDates(
        request: PersistenceDataRequest.FindMessagesBetweenDates,
    ): PersistenceDataResponse.Messages {
        val messages = findMessagesBetweenDatesPort.execute(
            FindMessagesBetweenDatesPort.Request(
                PeerId.of(request.peerId),
                request.from,
                request.to,
                request.limit,
            ),
        ).messages
        return PersistenceDataResponse.Messages(messages.map { it.toStoredMessage() })
    }

    private fun handleFindSummariesInPeriod(
        request: PersistenceDataRequest.FindSummariesInPeriod,
    ): PersistenceDataResponse.Summaries {
        val summaries = findSummariesInPeriodPort.execute(
            FindSummariesInPeriodPort.Request(
                PeerId.of(request.peerId),
                request.from,
                request.to,
            ),
        ).summaries
        return PersistenceDataResponse.Summaries(summaries.map { it.toSummary() })
    }

    private fun handleFindLastSummary(request: PersistenceDataRequest.FindLastSummary): PersistenceDataResponse.OptionalSummary {
        val summary = findLastSummaryByPeerIdPort.execute(
            FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdRequest(request.peerId),
        ).summary?.toSummary()
        return PersistenceDataResponse.OptionalSummary(summary)
    }

    private fun handleHasPendingSummary(request: PersistenceDataRequest.HasPendingSummary): PersistenceDataResponse.PendingStatus {
        val hasPending = hasPendingSummaryPort.execute(
            HasPendingSummaryPort.HasPendingSummaryRequest(request.peerId),
        ).hasPending
        return PersistenceDataResponse.PendingStatus(hasPending)
    }

    private fun handleCreatePendingSummary(request: PersistenceDataRequest.CreatePendingSummary): PersistenceDataResponse.CreatedSummaryId {
        val summaryId = UUID.randomUUID()
        val pendingSummary = SummaryEntity().apply {
            id = summaryId
            peerId = request.peerId
            firstMessageId = request.firstMessageId
            lastMessageId = request.lastMessageId
            fullSummary = ""
            shortSummary = ""
            status = SummaryStatus.PENDING
            createdAt = OffsetDateTime.now()
            updatedAt = OffsetDateTime.now()
        }
        insertSummaryPort.execute(InsertSummaryPort.InsertSummaryRequest(pendingSummary))
        return PersistenceDataResponse.CreatedSummaryId(summaryId)
    }

    private fun handleSaveCompletedSummary(request: PersistenceDataRequest.SaveCompletedSummary): PersistenceDataResponse.Empty {
        val existingSummary = findSummaryByIdPort.execute(
            FindSummaryByIdPort.FindSummaryByIdRequest(request.id),
        ).summary
            ?: throw SummaryNotFoundException(request.id)

        val completedSummary = SummaryEntity().apply {
            id = request.id
            fullSummary = request.fullSummary
            shortSummary = request.shortSummary
            status = SummaryStatus.COMPLETED
            updatedAt = OffsetDateTime.now()
        }
        updateSummaryStatusAndSummariesPort.execute(
            UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesRequest(completedSummary),
        )

        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.SUMMARY_READY,
                payload = Payload(
                    SummaryReadyPayload(
                        peerId = existingSummary.peerId!!,
                        messageText = "📋 Суммаризация обсуждения:\n\n${request.fullSummary}\n\n#суммаризация",
                        firstConversationMessageId = existingSummary.firstMessageId!!,
                        lastConversationMessageId = existingSummary.lastMessageId!!,
                    ),
                ),
            ),
        )
        return PersistenceDataResponse.Empty
    }

    private fun handleMarkSummaryAsFailed(request: PersistenceDataRequest.MarkSummaryAsFailed): PersistenceDataResponse.Empty {
        val failedSummary = SummaryEntity().apply {
            id = request.id
            status = SummaryStatus.FAILED
            updatedAt = OffsetDateTime.now()
        }
        updateSummaryStatusPort.execute(
            UpdateSummaryStatusPort.UpdateSummaryStatusRequest(failedSummary),
        )
        return PersistenceDataResponse.Empty
    }

    private fun handleFindUserProfilesByIds(
        request: PersistenceDataRequest.FindUserProfilesByIds,
    ): PersistenceDataResponse.UserProfiles {
        val profiles = findUserProfilesByIdsPort.execute(
            FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest(request.ids.map { FromId.of(it) }),
        ).profiles
        return PersistenceDataResponse.UserProfiles(profiles)
    }

    private fun handleFindGroupProfilesByIds(
        request: PersistenceDataRequest.FindGroupProfilesByIds,
    ): PersistenceDataResponse.GroupProfiles {
        val profiles = findGroupProfilesByIdsPort.execute(
            FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest(request.ids.map { FromId.of(it) }),
        ).profiles
        return PersistenceDataResponse.GroupProfiles(profiles)
    }

    private fun handleFindUserProfilesByBirthDate(
        request: PersistenceDataRequest.FindUserProfilesByBirthDate,
    ): PersistenceDataResponse.UserProfiles {
        val profiles = findUserProfilesByBirthDatePort.execute(
            FindUserProfilesByBirthDatePort.FindUserProfilesByBirthDateRequest(request.day, request.month),
        ).profiles
        return PersistenceDataResponse.UserProfiles(profiles)
    }

    private fun handleFindUserChatPeerIds(
        request: PersistenceDataRequest.FindUserChatPeerIds,
    ): PersistenceDataResponse.PeerIds {
        val peerIds = findUserChatPeerIdsPort.execute(
            FindUserChatPeerIdsPort.FindUserChatPeerIdsRequest(FromId.of(request.userId)),
        ).peerIds
        return PersistenceDataResponse.PeerIds(peerIds.map { it.value }.toSet())
    }

    private fun handleFindGroupChatPeerIds(): PersistenceDataResponse.PeerIds {
        val peerIds = findGroupChatPeerIdsPort.execute(FindGroupChatPeerIdsPort.FindGroupChatPeerIdsRequest).peerIds
        return PersistenceDataResponse.PeerIds(peerIds.map { it.value }.toSet())
    }

    private fun handleClaimDailyGreeting(
        request: PersistenceDataRequest.ClaimDailyGreeting,
    ): PersistenceDataResponse.Claimed {
        val claimed = claimDailyGreetingPort.execute(
            ClaimDailyGreetingPort.ClaimDailyGreetingRequest(request.date, PeerId.of(request.peerId)),
        ).claimed
        return PersistenceDataResponse.Claimed(claimed)
    }

    companion object {
        private fun MessageEntity.toStoredMessage(): StoredMessage = StoredMessage(
            id = id,
            peerId = peerId!!,
            conversationMessageId = conversationMessageId!!,
            fromId = fromId!!,
            messageText = messageText,
            date = date!!,
            forwardedMessages = forwardedMessages,
        )

        private fun SummaryEntity.toSummary(): Summary = Summary(
            id = id!!,
            peerId = peerId!!,
            firstMessageId = firstMessageId,
            lastMessageId = lastMessageId,
            shortSummary = shortSummary,
            fullSummary = fullSummary,
            status = status!!.toSharedStatus(),
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )

        private fun SummaryStatus.toSharedStatus(): SharedSummaryStatus = when (this) {
            SummaryStatus.PENDING -> SharedSummaryStatus.PENDING
            SummaryStatus.COMPLETED -> SharedSummaryStatus.COMPLETED
            SummaryStatus.FAILED -> SharedSummaryStatus.FAILED
        }
    }
}
