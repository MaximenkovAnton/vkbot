package com.simarel.vkbot.ai.adapter.output.persistence

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
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class FindStoredMessagesBeforeAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindStoredMessagesBeforePort {
    override fun execute(request: FindStoredMessagesBeforePort.Request): FindStoredMessagesBeforePort.Response {
        return FindStoredMessagesBeforePort.Response(
            persistenceClient.findMessagesBefore(request.peerId, request.beforeConversationMessageId, request.limit)
        )
    }
}

@ApplicationScoped
class FindStoredMessagesBetweenAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindStoredMessagesBetweenPort {
    override fun execute(request: FindStoredMessagesBetweenPort.Request): FindStoredMessagesBetweenPort.Response {
        return FindStoredMessagesBetweenPort.Response(
            persistenceClient.findMessagesBetween(request.peerId, request.firstMessageId, request.lastMessageId, request.limit)
        )
    }
}

@ApplicationScoped
class FindLastStoredSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindLastStoredSummaryPort {
    override fun execute(request: FindLastStoredSummaryPort.Request): FindLastStoredSummaryPort.Response {
        return FindLastStoredSummaryPort.Response(
            persistenceClient.findLastSummary(request.peerId)
        )
    }
}

@ApplicationScoped
class HasStoredPendingSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : HasStoredPendingSummaryPort {
    override fun execute(request: HasStoredPendingSummaryPort.Request): HasStoredPendingSummaryPort.Response {
        return HasStoredPendingSummaryPort.Response(
            persistenceClient.hasPendingSummary(request.peerId)
        )
    }
}

@ApplicationScoped
class CreateStoredPendingSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : CreateStoredPendingSummaryPort {
    override fun execute(request: CreateStoredPendingSummaryPort.Request): CreateStoredPendingSummaryPort.Response {
        return CreateStoredPendingSummaryPort.Response(
            persistenceClient.createPendingSummary(
                CreatePendingSummaryRequest(request.peerId, request.firstMessageId, request.lastMessageId)
            )
        )
    }
}

@ApplicationScoped
class SaveStoredCompletedSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : SaveStoredCompletedSummaryPort {
    override fun execute(request: SaveStoredCompletedSummaryPort.Request): SaveStoredCompletedSummaryPort.Response {
        persistenceClient.saveCompletedSummary(
            request.summaryId,
            CompleteSummaryRequest(request.shortSummary, request.fullSummary)
        )
        return SaveStoredCompletedSummaryPort.Response()
    }
}

@ApplicationScoped
class MarkStoredSummaryFailedAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : MarkStoredSummaryFailedPort {
    override fun execute(request: MarkStoredSummaryFailedPort.Request): MarkStoredSummaryFailedPort.Response {
        persistenceClient.markSummaryAsFailed(request.summaryId)
        return MarkStoredSummaryFailedPort.Response()
    }
}

@ApplicationScoped
class FindStoredUserProfilesByIdsAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindStoredUserProfilesByIdsPort {
    override fun execute(request: FindStoredUserProfilesByIdsPort.Request): FindStoredUserProfilesByIdsPort.Response {
        return FindStoredUserProfilesByIdsPort.Response(
            persistenceClient.findUserProfilesByIds(request.ids.map { it.value })
        )
    }
}

@ApplicationScoped
class FindStoredGroupProfilesByIdsAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindStoredGroupProfilesByIdsPort {
    override fun execute(request: FindStoredGroupProfilesByIdsPort.Request): FindStoredGroupProfilesByIdsPort.Response {
        return FindStoredGroupProfilesByIdsPort.Response(
            persistenceClient.findGroupProfilesByIds(request.ids.map { it.value })
        )
    }
}
