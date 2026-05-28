package com.simarel.vkbot.vkFacade.usecase.profile

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.vkFacade.command.getProfile.GetProfileCommand
import com.simarel.vkbot.vkFacade.port.input.vk.VkProfileInputPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class VkProfileUsecase(
    private val getProfileCommand: GetProfileCommand,
) : VkProfileInputPort {
    override fun execute(request: VkProfileInputPort.VkProfileRequest): VkProfileInputPort.VkProfileResponse {
        val result = getProfileCommand.execute(
            GetProfileCommand.GetProfileRequest(
                userIds = request.userIds.map { FromId.of(it) },
                groupIds = request.groupIds.map { FromId.of(it) },
            )
        )
        return VkProfileInputPort.VkProfileResponse(result.users, result.groups)
    }
}
