package com.simarel.vkbot.vkFacade.adapter.input.inprocess

import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkProfileInputPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GetUserProfilesBatchInProcessAdapter(
    private val vkProfileInputPort: VkProfileInputPort,
) : GetUserProfilesBatchPort {
    override fun execute(request: GetUserProfilesBatchPort.Request): GetUserProfilesBatchPort.Response {
        val response = vkProfileInputPort.execute(
            VkProfileInputPort.VkProfileRequest(
                userIds = request.fromIds.map { it.value },
                groupIds = emptyList(),
            ),
        )
        return GetUserProfilesBatchPort.Response(response.users)
    }
}

@ApplicationScoped
class GetGroupProfilesBatchInProcessAdapter(
    private val vkProfileInputPort: VkProfileInputPort,
) : GetGroupProfilesBatchPort {
    override fun execute(request: GetGroupProfilesBatchPort.Request): GetGroupProfilesBatchPort.Response {
        val response = vkProfileInputPort.execute(
            VkProfileInputPort.VkProfileRequest(
                userIds = emptyList(),
                groupIds = request.fromIds.map { it.value },
            ),
        )
        return GetGroupProfilesBatchPort.Response(response.groups)
    }
}
