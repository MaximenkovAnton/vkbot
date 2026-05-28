package com.simarel.vkbot.persistence.usecase

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryStatus
import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindLastSummaryByPeerIdPort
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBeforePort
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenPort
import com.simarel.vkbot.persistence.port.output.persistence.FindSummaryByIdPort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.HasPendingSummaryPort
import com.simarel.vkbot.persistence.port.output.persistence.InsertSummaryPort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusAndSummariesPort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusPort
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.domain.model.SummaryStatus as SharedSummaryStatus
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
open class PersistenceDataUsecase(
    private val findMessagesBeforePort: FindMessagesBeforePort,
    private val findMessagesBetweenPort: FindMessagesBetweenPort,
    private val findLastSummaryByPeerIdPort: FindLastSummaryByPeerIdPort,
    private val hasPendingSummaryPort: HasPendingSummaryPort,
    private val insertSummaryPort: InsertSummaryPort,
    private val updateSummaryStatusAndSummariesPort: UpdateSummaryStatusAndSummariesPort,
    private val updateSummaryStatusPort: UpdateSummaryStatusPort,
    private val findSummaryByIdPort: FindSummaryByIdPort,
    private val findUserProfilesByIdsPort: FindUserProfilesByIdsPort,
    private val findGroupProfilesByIdsPort: FindGroupProfilesByIdsPort,
    private val publishEventCommand: PublishEventCommand,
) : PersistenceDataInputPort {

    override fun execute(request: PersistenceDataRequest): PersistenceDataResponse {
        return when (request) {
            is PersistenceDataRequest.FindMessagesBefore -> handleFindMessagesBefore(request)
            is PersistenceDataRequest.FindMessagesBetween -> handleFindMessagesBetween(request)
            is PersistenceDataRequest.FindLastSummary -> handleFindLastSummary(request)
            is PersistenceDataRequest.HasPendingSummary -> handleHasPendingSummary(request)
            is PersistenceDataRequest.CreatePendingSummary -> handleCreatePendingSummary(request)
            is PersistenceDataRequest.SaveCompletedSummary -> handleSaveCompletedSummary(request)
            is PersistenceDataRequest.MarkSummaryAsFailed -> handleMarkSummaryAsFailed(request)
            is PersistenceDataRequest.FindUserProfilesByIds -> handleFindUserProfilesByIds(request)
            is PersistenceDataRequest.FindGroupProfilesByIds -> handleFindGroupProfilesByIds(request)
        }
    }

    private fun handleFindMessagesBefore(request: PersistenceDataRequest.FindMessagesBefore): PersistenceDataResponse.Messages {
        val messages = findMessagesBeforePort.execute(
            FindMessagesBeforePort.FindMessagesBeforeRequest(
                PeerId.of(request.peerId),
                ConversationMessageId.of(request.beforeConversationMessageId),
                request.limit,
            )
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
            )
        ).messages
        return PersistenceDataResponse.Messages(messages.map { it.toStoredMessage() })
    }

    private fun handleFindLastSummary(request: PersistenceDataRequest.FindLastSummary): PersistenceDataResponse.OptionalSummary {
        val summary = findLastSummaryByPeerIdPort.execute(
            FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdRequest(request.peerId)
        ).summary?.toSummary()
        return PersistenceDataResponse.OptionalSummary(summary)
    }

    private fun handleHasPendingSummary(request: PersistenceDataRequest.HasPendingSummary): PersistenceDataResponse.PendingStatus {
        val hasPending = hasPendingSummaryPort.execute(
            HasPendingSummaryPort.HasPendingSummaryRequest(request.peerId)
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
            FindSummaryByIdPort.FindSummaryByIdRequest(request.id)
        ).summary
            ?: throw IllegalArgumentException("Summary not found: ${request.id}")

        val completedSummary = SummaryEntity().apply {
            id = request.id
            fullSummary = request.fullSummary
            shortSummary = request.shortSummary
            status = SummaryStatus.COMPLETED
            updatedAt = OffsetDateTime.now()
        }
        updateSummaryStatusAndSummariesPort.execute(
            UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesRequest(completedSummary)
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
            UpdateSummaryStatusPort.UpdateSummaryStatusRequest(failedSummary)
        )
        return PersistenceDataResponse.Empty
    }

    private fun handleFindUserProfilesByIds(
        request: PersistenceDataRequest.FindUserProfilesByIds,
    ): PersistenceDataResponse.UserProfiles {
        val profiles = findUserProfilesByIdsPort.execute(
            FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest(request.ids.map { FromId.of(it) })
        ).profiles
        return PersistenceDataResponse.UserProfiles(profiles)
    }

    private fun handleFindGroupProfilesByIds(
        request: PersistenceDataRequest.FindGroupProfilesByIds,
    ): PersistenceDataResponse.GroupProfiles {
        val profiles = findGroupProfilesByIdsPort.execute(
            FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest(request.ids.map { FromId.of(it) })
        ).profiles
        return PersistenceDataResponse.GroupProfiles(profiles)
    }

    data class SummaryReadyPayload(
        val peerId: Long,
        val messageText: String,
        val firstConversationMessageId: Long,
        val lastConversationMessageId: Long,
    )

    companion object {
        private fun MessageEntity.toStoredMessage(): StoredMessage {
            return StoredMessage(
                id = id,
                peerId = peerId!!,
                conversationMessageId = conversationMessageId!!,
                fromId = fromId!!,
                messageText = messageText,
                date = date!!,
                forwardedMessages = forwardedMessages,
            )
        }

        private fun SummaryEntity.toSummary(): Summary {
            return Summary(
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
        }

        private fun SummaryStatus.toSharedStatus(): SharedSummaryStatus {
            return when (this) {
                SummaryStatus.PENDING -> SharedSummaryStatus.PENDING
                SummaryStatus.COMPLETED -> SharedSummaryStatus.COMPLETED
                SummaryStatus.FAILED -> SharedSummaryStatus.FAILED
            }
        }
    }
}
