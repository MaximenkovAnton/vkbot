package com.simarel.vkbot.persistence.adapter.output.vkfacade

import com.simarel.vkbot.persistence.adapter.output.client.vkfacade.VkFacadeService
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class GetUserProfilesBatchAdapter(
    @RestClient private val vkFacadeClient: VkFacadeService,
) : GetUserProfilesBatchPort {
    override fun execute(request: GetUserProfilesBatchPort.Request): GetUserProfilesBatchPort.Response {
        return GetUserProfilesBatchPort.Response(
            vkFacadeClient.getUserProfilesBatch(request.fromIds.map { it.value })
        )
    }
}

@ApplicationScoped
class GetGroupProfilesBatchAdapter(
    @RestClient private val vkFacadeClient: VkFacadeService,
) : GetGroupProfilesBatchPort {
    override fun execute(request: GetGroupProfilesBatchPort.Request): GetGroupProfilesBatchPort.Response {
        return GetGroupProfilesBatchPort.Response(
            vkFacadeClient.getGroupProfilesBatch(request.fromIds.map { it.value })
        )
    }
}
