package com.simarel.vkbot.testfixtures.adapter.output.vk

import com.simarel.vkbot.vkFacade.adapter.output.vk.VkClient
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkGroupsResponseDto
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkResponseDto
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkUsersResponseDto
import java.util.concurrent.ConcurrentLinkedQueue

class FakeVkClient(
    val vkResponseDto: VkResponseDto? = null,
    val vkUsersResponseDto: VkUsersResponseDto? = null,
    val vkGroupsResponseDto: VkGroupsResponseDto? = null,
) : VkClient {
    val sendMessageParameterCalls = ConcurrentLinkedQueue<SendMessageParameter>()
    val getUsersParameterCalls = ConcurrentLinkedQueue<GetUsersParameter>()
    val getGroupsParameterCalls = ConcurrentLinkedQueue<GetGroupsParameter>()

    override fun sendMessage(
        peerId: Long,
        message: String,
        rand: Int,
        forwardMessages: String?
    ): VkResponseDto {
        sendMessageParameterCalls.add(SendMessageParameter(peerId, message, rand))
        return vkResponseDto ?: VkResponseDto(error = null)
    }

    override fun getUsers(userIds: String, fields: String): VkUsersResponseDto {
        getUsersParameterCalls.add(GetUsersParameter(userIds, fields))
        return vkUsersResponseDto ?: VkUsersResponseDto(response = emptyList(), error = null)
    }

    override fun getGroups(groupIds: String): VkGroupsResponseDto {
        getGroupsParameterCalls.add(GetGroupsParameter(groupIds))
        return vkGroupsResponseDto ?: VkGroupsResponseDto(response = emptyList(), error = null)
    }

    data class SendMessageParameter(val peerId: Long, val message: String, val rand: Int)
    data class GetUsersParameter(val userIds: String, val fields: String)
    data class GetGroupsParameter(val groupIds: String)
}
