package com.simarel.vkbot.vkFacade.adapter.input.rest

import com.simarel.vkbot.share.adapter.output.client.vkfacade.VkFacadeService
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.vkFacade.port.output.vk.VkProfileOutputPort
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

@Path("/vk")
class VkFacadeController(
    private val getProfilePort: VkProfileOutputPort,
) : VkFacadeService {

    @GET
    @Path("/profiles/users/batch")
    @Produces(MediaType.APPLICATION_JSON)
    override fun getUserProfilesBatch(@QueryParam("ids") ids: List<Long>): List<VkUserProfile> {
        return getProfilePort.getUserProfilesBatch(ids.map { FromId.of(it) })
    }

    @GET
    @Path("/profiles/groups/batch")
    @Produces(MediaType.APPLICATION_JSON)
    override fun getGroupProfilesBatch(@QueryParam("ids") ids: List<Long>): List<VkGroupProfile> {
        return getProfilePort.getGroupProfilesBatch(ids.map { FromId.of(it) })
    }
}
