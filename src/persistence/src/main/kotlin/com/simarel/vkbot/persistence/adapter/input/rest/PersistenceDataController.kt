package com.simarel.vkbot.persistence.adapter.input.rest

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryStatus
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.SummaryStatus as SharedSummaryStatus
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import java.time.OffsetDateTime
import java.util.UUID

@Path("/persistence")
class PersistenceDataController(
    private val messageRepository: JooqMessageRepository,
    private val summaryRepository: JooqSummaryRepository,
    private val findUserProfilesPort: FindUserProfilesByIdsPort,
    private val findGroupProfilesPort: FindGroupProfilesByIdsPort,
) : PersistenceService {

    @GET
    @Path("/messages/before")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findMessagesBefore(
        @QueryParam("peerId") peerId: Long,
        @QueryParam("beforeConversationMessageId") beforeConversationMessageId: Long,
        @QueryParam("limit") limit: Int,
    ): List<StoredMessage> {
        return messageRepository.findMessagesBefore(peerId, beforeConversationMessageId, limit)
            .map { it.toStoredMessage() }
    }

    @GET
    @Path("/messages/between")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findMessagesBetween(
        @QueryParam("peerId") peerId: Long,
        @QueryParam("firstMessageId") firstMessageId: Long,
        @QueryParam("lastMessageId") lastMessageId: Long,
        @QueryParam("limit") limit: Int,
    ): List<StoredMessage> {
        return summaryRepository.findMessagesBetween(peerId, firstMessageId, lastMessageId, limit)
            .map { it.toStoredMessage() }
    }

    @GET
    @Path("/summaries/last")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findLastSummary(@QueryParam("peerId") peerId: Long): Summary? {
        return summaryRepository.findLastByPeerId(peerId)?.toSummary()
    }

    @GET
    @Path("/summaries/has-pending")
    @Produces(MediaType.APPLICATION_JSON)
    override fun hasPendingSummary(@QueryParam("peerId") peerId: Long): Boolean {
        return summaryRepository.hasPendingSummary(peerId)
    }

    @POST
    @Path("/summaries/pending")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    override fun createPendingSummary(request: CreatePendingSummaryRequest): UUID {
        val summaryId = UUID.randomUUID()
        val pendingSummary = SummaryEntity().apply {
            id = summaryId
            this.peerId = request.peerId
            this.firstMessageId = request.firstMessageId
            this.lastMessageId = request.lastMessageId
            this.fullSummary = ""
            this.shortSummary = ""
            this.status = SummaryStatus.PENDING
            this.createdAt = OffsetDateTime.now()
            this.updatedAt = OffsetDateTime.now()
        }
        summaryRepository.insert(pendingSummary)
        return summaryId
    }

    @POST
    @Path("/summaries/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    override fun saveCompletedSummary(
        @PathParam("id") id: UUID,
        request: CompleteSummaryRequest
    ) {
        val completedSummary = SummaryEntity().apply {
            this.id = id
            this.fullSummary = request.fullSummary
            this.shortSummary = request.shortSummary
            this.status = SummaryStatus.COMPLETED
            this.updatedAt = OffsetDateTime.now()
        }
        summaryRepository.updateStatusAndSummaries(completedSummary)
    }

    @POST
    @Path("/summaries/{id}/fail")
    @Consumes(MediaType.APPLICATION_JSON)
    override fun markSummaryAsFailed(@PathParam("id") id: UUID) {
        val failedSummary = SummaryEntity().apply {
            this.id = id
            this.status = SummaryStatus.FAILED
            this.updatedAt = OffsetDateTime.now()
        }
        summaryRepository.updateStatusAndSummaries(failedSummary)
    }

    @GET
    @Path("/profiles/users")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findUserProfilesByIds(@QueryParam("ids") ids: List<Long>): List<VkUserProfile> {
        return findUserProfilesPort.findByIds(ids.map { FromId.of(it) })
    }

    @GET
    @Path("/profiles/groups")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findGroupProfilesByIds(@QueryParam("ids") ids: List<Long>): List<VkGroupProfile> {
        return findGroupProfilesPort.findByIds(ids.map { FromId.of(it) })
    }
}

private fun MessageEntity.toStoredMessage(): StoredMessage {
    return StoredMessage(
        id = this.id,
        peerId = this.peerId!!,
        conversationMessageId = this.conversationMessageId!!,
        fromId = this.fromId!!,
        messageText = this.messageText,
        date = this.date!!,
        forwardedMessages = this.forwardedMessages,
    )
}

private fun SummaryEntity.toSummary(): Summary {
    return Summary(
        id = this.id!!,
        peerId = this.peerId!!,
        firstMessageId = this.firstMessageId,
        lastMessageId = this.lastMessageId,
        shortSummary = this.shortSummary,
        fullSummary = this.fullSummary,
        status = this.status!!.toSharedStatus(),
        createdAt = this.createdAt!!,
        updatedAt = this.updatedAt!!,
    )
}

private fun SummaryStatus.toSharedStatus(): SharedSummaryStatus {
    return when (this) {
        SummaryStatus.PENDING -> SharedSummaryStatus.PENDING
        SummaryStatus.COMPLETED -> SharedSummaryStatus.COMPLETED
        SummaryStatus.FAILED -> SharedSummaryStatus.FAILED
    }
}
