package com.simarel.vkbot.persistence.adapter.input.inprocess

import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindStoredMessagesBeforeInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredMessagesBeforePort {
    override fun execute(request: FindStoredMessagesBeforePort.Request): FindStoredMessagesBeforePort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.FindMessagesBefore(
                request.peerId,
                request.beforeConversationMessageId,
                request.limit,
            ),
        )
        return FindStoredMessagesBeforePort.Response((response as PersistenceDataResponse.Messages).messages)
    }
}

@ApplicationScoped
class FindStoredMessagesBetweenInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredMessagesBetweenPort {
    override fun execute(request: FindStoredMessagesBetweenPort.Request): FindStoredMessagesBetweenPort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.FindMessagesBetween(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId,
                request.limit,
            ),
        )
        return FindStoredMessagesBetweenPort.Response((response as PersistenceDataResponse.Messages).messages)
    }
}

@ApplicationScoped
class FindLastStoredSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindLastStoredSummaryPort {
    override fun execute(request: FindLastStoredSummaryPort.Request): FindLastStoredSummaryPort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.FindLastSummary(request.peerId),
        )
        return FindLastStoredSummaryPort.Response((response as PersistenceDataResponse.OptionalSummary).summary)
    }
}

@ApplicationScoped
class HasStoredPendingSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : HasStoredPendingSummaryPort {
    override fun execute(request: HasStoredPendingSummaryPort.Request): HasStoredPendingSummaryPort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.HasPendingSummary(request.peerId),
        )
        return HasStoredPendingSummaryPort.Response((response as PersistenceDataResponse.PendingStatus).hasPending)
    }
}

@ApplicationScoped
class CreateStoredPendingSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : CreateStoredPendingSummaryPort {
    override fun execute(request: CreateStoredPendingSummaryPort.Request): CreateStoredPendingSummaryPort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.CreatePendingSummary(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId,
            ),
        )
        return CreateStoredPendingSummaryPort.Response(
            (response as PersistenceDataResponse.CreatedSummaryId).id,
        )
    }
}

@ApplicationScoped
class SaveStoredCompletedSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : SaveStoredCompletedSummaryPort {
    override fun execute(request: SaveStoredCompletedSummaryPort.Request): SaveStoredCompletedSummaryPort.Response {
        persistenceDataInputPort.execute(
            PersistenceDataRequest.SaveCompletedSummary(
                request.summaryId,
                request.shortSummary,
                request.fullSummary,
            ),
        )
        return SaveStoredCompletedSummaryPort.Response()
    }
}

@ApplicationScoped
class MarkStoredSummaryFailedInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : MarkStoredSummaryFailedPort {
    override fun execute(request: MarkStoredSummaryFailedPort.Request): MarkStoredSummaryFailedPort.Response {
        persistenceDataInputPort.execute(
            PersistenceDataRequest.MarkSummaryAsFailed(request.summaryId),
        )
        return MarkStoredSummaryFailedPort.Response()
    }
}

@ApplicationScoped
class FindStoredUserProfilesByIdsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredUserProfilesByIdsPort {
    override fun execute(request: FindStoredUserProfilesByIdsPort.Request): FindStoredUserProfilesByIdsPort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.FindUserProfilesByIds(request.ids.map { it.value }),
        )
        return FindStoredUserProfilesByIdsPort.Response((response as PersistenceDataResponse.UserProfiles).profiles)
    }
}

@ApplicationScoped
class FindStoredGroupProfilesByIdsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredGroupProfilesByIdsPort {
    override fun execute(
        request: FindStoredGroupProfilesByIdsPort.Request,
    ): FindStoredGroupProfilesByIdsPort.Response {
        val response = persistenceDataInputPort.execute(
            PersistenceDataRequest.FindGroupProfilesByIds(request.ids.map { it.value }),
        )
        return FindStoredGroupProfilesByIdsPort.Response((response as PersistenceDataResponse.GroupProfiles).profiles)
    }
}
