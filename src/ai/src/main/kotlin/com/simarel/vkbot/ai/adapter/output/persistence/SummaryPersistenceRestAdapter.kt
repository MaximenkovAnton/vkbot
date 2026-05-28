package com.simarel.vkbot.ai.adapter.output.persistence

import com.simarel.vkbot.ai.port.output.persistence.CreateAiStoredPendingSummaryPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredGroupProfilesByIdsPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredMessagesBeforePort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredMessagesBetweenPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredUserProfilesByIdsPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiLastStoredSummaryPort
import com.simarel.vkbot.ai.port.output.persistence.HasAiStoredPendingSummaryPort
import com.simarel.vkbot.ai.port.output.persistence.MarkAiStoredSummaryFailedPort
import com.simarel.vkbot.ai.port.output.persistence.SaveAiStoredCompletedSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class CreateAiStoredPendingSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : CreateAiStoredPendingSummaryPort {
    override fun execute(request: CreateAiStoredPendingSummaryPort.Request): CreateAiStoredPendingSummaryPort.Response {
        return CreateAiStoredPendingSummaryPort.Response(
            persistenceClient.createPendingSummary(
                CreatePendingSummaryRequest(request.peerId, request.firstMessageId, request.lastMessageId)
            )
        )
    }
}

@ApplicationScoped
class SaveAiStoredCompletedSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : SaveAiStoredCompletedSummaryPort {
    override fun execute(request: SaveAiStoredCompletedSummaryPort.Request): SaveAiStoredCompletedSummaryPort.Response {
        persistenceClient.saveCompletedSummary(
            request.summaryId,
            CompleteSummaryRequest(request.shortSummary, request.fullSummary)
        )
        return SaveAiStoredCompletedSummaryPort.Response()
    }
}

@ApplicationScoped
class MarkAiStoredSummaryFailedAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : MarkAiStoredSummaryFailedPort {
    override fun execute(request: MarkAiStoredSummaryFailedPort.Request): MarkAiStoredSummaryFailedPort.Response {
        persistenceClient.markSummaryAsFailed(request.summaryId)
        return MarkAiStoredSummaryFailedPort.Response()
    }
}

@ApplicationScoped
class FindAiStoredMessagesBetweenAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindAiStoredMessagesBetweenPort {
    override fun execute(request: FindAiStoredMessagesBetweenPort.Request): FindAiStoredMessagesBetweenPort.Response {
        return FindAiStoredMessagesBetweenPort.Response(
            persistenceClient.findMessagesBetween(request.peerId, request.firstMessageId, request.lastMessageId, request.limit)
        )
    }
}

@ApplicationScoped
class FindAiStoredMessagesBeforeAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindAiStoredMessagesBeforePort {
    override fun execute(request: FindAiStoredMessagesBeforePort.Request): FindAiStoredMessagesBeforePort.Response {
        return FindAiStoredMessagesBeforePort.Response(
            persistenceClient.findMessagesBefore(request.peerId, request.beforeConversationMessageId, request.limit)
        )
    }
}

@ApplicationScoped
class FindAiStoredUserProfilesByIdsAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindAiStoredUserProfilesByIdsPort {
    override fun execute(request: FindAiStoredUserProfilesByIdsPort.Request): FindAiStoredUserProfilesByIdsPort.Response {
        return FindAiStoredUserProfilesByIdsPort.Response(
            persistenceClient.findUserProfilesByIds(request.ids.map { it.value })
        )
    }
}

@ApplicationScoped
class FindAiStoredGroupProfilesByIdsAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindAiStoredGroupProfilesByIdsPort {
    override fun execute(request: FindAiStoredGroupProfilesByIdsPort.Request): FindAiStoredGroupProfilesByIdsPort.Response {
        return FindAiStoredGroupProfilesByIdsPort.Response(
            persistenceClient.findGroupProfilesByIds(request.ids.map { it.value })
        )
    }
}

@ApplicationScoped
class FindAiLastStoredSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : FindAiLastStoredSummaryPort {
    override fun execute(request: FindAiLastStoredSummaryPort.Request): FindAiLastStoredSummaryPort.Response {
        return FindAiLastStoredSummaryPort.Response(
            persistenceClient.findLastSummary(request.peerId)
        )
    }
}

@ApplicationScoped
class HasAiStoredPendingSummaryAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : HasAiStoredPendingSummaryPort {
    override fun execute(request: HasAiStoredPendingSummaryPort.Request): HasAiStoredPendingSummaryPort.Response {
        return HasAiStoredPendingSummaryPort.Response(
            persistenceClient.hasPendingSummary(request.peerId)
        )
    }
}
