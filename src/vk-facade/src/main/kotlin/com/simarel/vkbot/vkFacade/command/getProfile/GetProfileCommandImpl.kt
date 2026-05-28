package com.simarel.vkbot.vkFacade.command.getProfile

import com.simarel.vkbot.vkFacade.port.output.vk.VkGetGroupProfilesBatchPort
import com.simarel.vkbot.vkFacade.port.output.vk.VkGetUserProfilesBatchPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GetProfileCommandImpl(
    private val vkGetUserProfilesBatchPort: VkGetUserProfilesBatchPort,
    private val vkGetGroupProfilesBatchPort: VkGetGroupProfilesBatchPort,
) : GetProfileCommand {
    override fun execute(request: GetProfileCommand.GetProfileRequest): GetProfileCommand.GetProfileResponse {
        val users = if (request.userIds.isNotEmpty()) {
            vkGetUserProfilesBatchPort.execute(
                VkGetUserProfilesBatchPort.Request(request.userIds)
            ).profiles
        } else emptyList()

        val groups = if (request.groupIds.isNotEmpty()) {
            vkGetGroupProfilesBatchPort.execute(
                VkGetGroupProfilesBatchPort.Request(request.groupIds)
            ).profiles
        } else emptyList()

        return GetProfileCommand.GetProfileResponse(users, groups)
    }
}
