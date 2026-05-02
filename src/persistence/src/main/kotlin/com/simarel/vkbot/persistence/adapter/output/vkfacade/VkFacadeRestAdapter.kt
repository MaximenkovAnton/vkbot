package com.simarel.vkbot.persistence.adapter.output.vkfacade

import com.simarel.vkbot.share.adapter.output.client.vkfacade.VkFacadeService
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.vk.GetProfileOutputPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class VkFacadeRestAdapter(
    @RestClient private val vkFacadeClient: VkFacadeService,
) : GetProfileOutputPort {

    override fun getUserProfilesBatch(fromIds: List<FromId>): List<VkUserProfile> {
        return vkFacadeClient.getUserProfilesBatch(fromIds.map { it.value })
    }

    override fun getGroupProfilesBatch(fromIds: List<FromId>): List<VkGroupProfile> {
        return vkFacadeClient.getGroupProfilesBatch(fromIds.map { it.value })
    }
}
