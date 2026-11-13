package com.simarel.vkbot.persistence.adapter.input.rest

import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@ApplicationScoped
@Path("/persistence")
@Suppress("TooManyFunctions")
class PersistenceDataResource(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : PersistenceResource {

    @GET
    @Path("/messages/before")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findMessagesBefore(
        @QueryParam("peerId") peerId: Long,
        @QueryParam("beforeConversationMessageId") beforeConversationMessageId: Long,
        @QueryParam("limit") limit: Int,
    ): List<StoredMessage> {
        val response = persistenceDataInputPort.findMessagesBefore(
            PersistenceDataRequest.FindMessagesBefore(peerId, beforeConversationMessageId, limit),
        )
        return response.messages
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
        val response = persistenceDataInputPort.findMessagesBetween(
            PersistenceDataRequest.FindMessagesBetween(peerId, firstMessageId, lastMessageId, limit),
        )
        return response.messages
    }

    @GET
    @Path("/summaries/last")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findLastSummary(@QueryParam("peerId") peerId: Long): Summary? {
        val response = persistenceDataInputPort.findLastSummary(
            PersistenceDataRequest.FindLastSummary(peerId),
        )
        return response.summary
    }

    @GET
    @Path("/summaries/has-pending")
    @Produces(MediaType.APPLICATION_JSON)
    override fun hasPendingSummary(@QueryParam("peerId") peerId: Long): Boolean {
        val response = persistenceDataInputPort.hasPendingSummary(
            PersistenceDataRequest.HasPendingSummary(peerId),
        )
        return response.hasPending
    }

    @POST
    @Path("/summaries/pending")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    override fun createPendingSummary(request: CreatePendingSummaryRequest): UUID {
        val response = persistenceDataInputPort.createPendingSummary(
            PersistenceDataRequest.CreatePendingSummary(request.peerId, request.firstMessageId, request.lastMessageId),
        )
        return response.id
    }

    @POST
    @Path("/summaries/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    override fun saveCompletedSummary(
        @PathParam("id") id: UUID,
        request: CompleteSummaryRequest,
    ) {
        persistenceDataInputPort.saveCompletedSummary(
            PersistenceDataRequest.SaveCompletedSummary(id, request.shortSummary, request.fullSummary),
        )
    }

    @POST
    @Path("/summaries/{id}/fail")
    @Consumes(MediaType.APPLICATION_JSON)
    override fun markSummaryAsFailed(@PathParam("id") id: UUID) {
        persistenceDataInputPort.markSummaryAsFailed(
            PersistenceDataRequest.MarkSummaryAsFailed(id),
        )
    }

    @GET
    @Path("/profiles/users")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findUserProfilesByIds(@QueryParam("ids") ids: List<Long>): List<VkUserProfile> {
        val response = persistenceDataInputPort.findUserProfilesByIds(
            PersistenceDataRequest.FindUserProfilesByIds(ids),
        )
        return response.profiles
    }

    @GET
    @Path("/profiles/groups")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findGroupProfilesByIds(@QueryParam("ids") ids: List<Long>): List<VkGroupProfile> {
        val response = persistenceDataInputPort.findGroupProfilesByIds(
            PersistenceDataRequest.FindGroupProfilesByIds(ids),
        )
        return response.profiles
    }

    @GET
    @Path("/profiles/users/{id}/history")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findUserProfileHistory(
        @PathParam("id") id: Long,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
        @QueryParam("offset") @DefaultValue("0") offset: Int,
    ): List<ProfileHistoryEntry> {
        val response = persistenceDataInputPort.findUserProfileHistory(
            PersistenceDataRequest.FindUserProfileHistory(id, limit, offset),
        )
        return response.entries
    }

    @GET
    @Path("/statistics/users/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    override fun findUserStatistics(@PathParam("id") id: Long): UserStatistics {
        val response = persistenceDataInputPort.findUserStatistics(
            PersistenceDataRequest.FindUserStatistics(id),
        )
        return response.statistics
    }
}
