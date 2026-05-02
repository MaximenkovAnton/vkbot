package com.simarel.vkbot.ai.adapter.output.persistence

import com.simarel.vkbot.ai.adapter.output.client.persistence.CompleteSummaryRequest
import com.simarel.vkbot.ai.adapter.output.client.persistence.CreatePendingSummaryRequest
import com.simarel.vkbot.ai.adapter.output.client.persistence.PersistenceService
import com.simarel.vkbot.ai.port.output.persistence.SummaryPersistenceOutputPort
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient
import java.util.UUID

@ApplicationScoped
class SummaryPersistenceRestAdapter(
    @RestClient private val persistenceClient: PersistenceService,
) : SummaryPersistenceOutputPort {

    override fun createPendingSummary(
        peerId: Long,
        firstMessageId: Long,
        lastMessageId: Long,
    ): UUID {
        return persistenceClient.createPendingSummary(
            CreatePendingSummaryRequest(peerId, firstMessageId, lastMessageId)
        )
    }

    override fun saveCompletedSummary(
        summaryId: UUID,
        shortSummary: String,
        fullSummary: String,
    ) {
        persistenceClient.saveCompletedSummary(
            summaryId,
            CompleteSummaryRequest(shortSummary, fullSummary)
        )
    }

    override fun markSummaryAsFailed(summaryId: UUID) {
        persistenceClient.markSummaryAsFailed(summaryId)
    }

    override fun findMessagesBetween(
        peerId: Long,
        firstMessageId: Long,
        lastMessageId: Long,
        limit: Int,
    ): List<StoredMessage> {
        return persistenceClient.findMessagesBetween(peerId, firstMessageId, lastMessageId, limit)
    }

    override fun findMessagesBefore(
        peerId: Long,
        beforeConversationMessageId: Long,
        limit: Int,
    ): List<StoredMessage> {
        return persistenceClient.findMessagesBefore(peerId, beforeConversationMessageId, limit)
    }

    override fun findUserProfilesByIds(ids: List<FromId>): List<VkUserProfile> {
        return persistenceClient.findUserProfilesByIds(ids.map { it.value })
    }

    override fun findGroupProfilesByIds(ids: List<FromId>): List<VkGroupProfile> {
        return persistenceClient.findGroupProfilesByIds(ids.map { it.value })
    }

    override fun findLastSummary(peerId: Long): Summary? {
        return persistenceClient.findLastSummary(peerId)
    }

    override fun hasPendingSummary(peerId: Long): Boolean {
        return persistenceClient.hasPendingSummary(peerId)
    }
}
