package com.simarel.vkbot.share.adapter.output.client.persistence

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import java.util.UUID

@RegisterRestClient(configKey = "persistence")
@Path("/persistence")
interface PersistenceService {

    @GET
    @Path("/messages/before")
    @Produces(MediaType.APPLICATION_JSON)
    fun findMessagesBefore(
        @QueryParam("peerId") peerId: Long,
        @QueryParam("beforeConversationMessageId") beforeConversationMessageId: Long,
        @QueryParam("limit") limit: Int,
    ): List<StoredMessage>

    @GET
    @Path("/messages/between")
    @Produces(MediaType.APPLICATION_JSON)
    fun findMessagesBetween(
        @QueryParam("peerId") peerId: Long,
        @QueryParam("firstMessageId") firstMessageId: Long,
        @QueryParam("lastMessageId") lastMessageId: Long,
        @QueryParam("limit") limit: Int,
    ): List<StoredMessage>

    @GET
    @Path("/summaries/last")
    @Produces(MediaType.APPLICATION_JSON)
    fun findLastSummary(@QueryParam("peerId") peerId: Long): Summary?

    @GET
    @Path("/summaries/has-pending")
    @Produces(MediaType.APPLICATION_JSON)
    fun hasPendingSummary(@QueryParam("peerId") peerId: Long): Boolean

    @POST
    @Path("/summaries/pending")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createPendingSummary(request: CreatePendingSummaryRequest): UUID

    @POST
    @Path("/summaries/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    fun saveCompletedSummary(
        @PathParam("id") id: UUID,
        request: CompleteSummaryRequest
    )

    @POST
    @Path("/summaries/{id}/fail")
    @Consumes(MediaType.APPLICATION_JSON)
    fun markSummaryAsFailed(@PathParam("id") id: UUID)

    @GET
    @Path("/profiles/users")
    @Produces(MediaType.APPLICATION_JSON)
    fun findUserProfilesByIds(@QueryParam("ids") ids: List<Long>): List<VkUserProfile>

    @GET
    @Path("/profiles/groups")
    @Produces(MediaType.APPLICATION_JSON)
    fun findGroupProfilesByIds(@QueryParam("ids") ids: List<Long>): List<VkGroupProfile>
}

data class CreatePendingSummaryRequest(
    val peerId: Long,
    val firstMessageId: Long,
    val lastMessageId: Long,
)

data class CompleteSummaryRequest(
    val shortSummary: String,
    val fullSummary: String,
    val status: SummaryStatus = SummaryStatus.COMPLETED,
)
