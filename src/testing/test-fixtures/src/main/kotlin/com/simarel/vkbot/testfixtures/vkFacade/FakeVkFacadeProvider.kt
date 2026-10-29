package com.simarel.vkbot.testfixtures.vkFacade

import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkError
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkResponseDto
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages
import com.simarel.vkbot.vkFacade.port.input.vk.VkProfileInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest

object FakeVkFacadeProvider {

    fun createVkError(
        errorCode: Int = 1,
        errorMessage: String = "error happened",
    ) = VkError(error_code = errorCode, error_msg = errorMessage)

    fun createVkResponseDto(error: VkError? = null) = VkResponseDto(error = error)

    fun createVkProfileRequest(
        userIds: List<Long> = emptyList(),
        groupIds: List<Long> = emptyList(),
    ) = VkProfileInputPort.VkProfileRequest(
        userIds = userIds,
        groupIds = groupIds,
    )

    fun createVkProfileResponse(
        users: List<VkUserProfile> = emptyList(),
        groups: List<VkGroupProfile> = emptyList(),
    ) = VkProfileInputPort.VkProfileResponse(
        users = users,
        groups = groups,
    )

    fun createVkSendMessageInputRequest(
        responseMessage: ResponseMessage? = null,
        forwardedMessages: ForwardedMessages? = null,
    ) = VkSendMessageInputRequest(
        responseMessage = responseMessage ?: FakeModelProvider.createResponseMessage(),
        forwardedMessages = forwardedMessages,
    )
}
