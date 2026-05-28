package com.simarel.vkbot.vkFacade.command.getProfile

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId

interface GetProfileCommand : Command<GetProfileCommand.GetProfileRequest, GetProfileCommand.GetProfileResponse> {
    data class GetProfileRequest(
        val userIds: List<FromId>,
        val groupIds: List<FromId>,
    ) : CommandRequest

    data class GetProfileResponse(
        val users: List<VkUserProfile>,
        val groups: List<VkGroupProfile>,
    ) : CommandResponse
}
