package com.simarel.vkbot.vkFacade.adapter.input.rest

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.vkFacade.port.input.vk.VkProfileInputPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

@ApplicationScoped
@Path("/api")
class VkFacadeController(
    private val vkProfileInputPort: VkProfileInputPort,
) {

    @GET
    @Path("/profiles/users/batch")
    @Produces(MediaType.APPLICATION_JSON)
    fun getUserProfilesBatch(@QueryParam("ids") ids: List<Long>): List<VkUserProfile> {
        return vkProfileInputPort.execute(
            VkProfileInputPort.VkProfileRequest(userIds = ids, groupIds = emptyList())
        ).users
    }

    @GET
    @Path("/profiles/groups/batch")
    @Produces(MediaType.APPLICATION_JSON)
    fun getGroupProfilesBatch(@QueryParam("ids") ids: List<Long>): List<VkGroupProfile> {
        return vkProfileInputPort.execute(
            VkProfileInputPort.VkProfileRequest(userIds = emptyList(), groupIds = ids)
        ).groups
    }
}
