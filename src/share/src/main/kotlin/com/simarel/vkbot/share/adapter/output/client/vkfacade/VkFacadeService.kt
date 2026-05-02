package com.simarel.vkbot.share.adapter.output.client.vkfacade

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "vk-facade")
interface VkFacadeService {

    @GET
    @Path("/profiles/users/batch")
    @Produces(MediaType.APPLICATION_JSON)
    fun getUserProfilesBatch(@QueryParam("ids") ids: List<Long>): List<VkUserProfile>

    @GET
    @Path("/profiles/groups/batch")
    @Produces(MediaType.APPLICATION_JSON)
    fun getGroupProfilesBatch(@QueryParam("ids") ids: List<Long>): List<VkGroupProfile>
}
