package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.vkFacade.adapter.output.vk.VkClient
import com.simarel.vkbot.vkFacade.port.output.vk.GetProfileOutputPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class GetProfileAdapter(
    @RestClient private val vkClient: VkClient,
) : GetProfileOutputPort {

    override fun getUserProfilesBatch(fromIds: List<FromId>): List<VkUserProfile> {
        if (fromIds.isEmpty()) {
            return emptyList()
        }

        val userIdsString = fromIds.joinToString(",") { it.value.toString() }
        val response = vkClient.getUsers(
            userIds = userIdsString,
            fields = "bdate,city,screen_name",
        )

        if (response.error != null) {
            throw VkApiException("VK API error: ${response.error.error_msg}")
        }

        return response.response?.map { userDto ->
            VkUserProfile.of(
                id = userDto.id,
                firstName = userDto.firstName,
                lastName = userDto.lastName,
                screenName = userDto.screenName,
                birthDate = userDto.birthDate,
                city = userDto.city?.title,
            )
        } ?: emptyList()
    }

    override fun getGroupProfilesBatch(fromIds: List<FromId>): List<VkGroupProfile> {
        if (fromIds.isEmpty()) {
            return emptyList()
        }

        val groupIdsString = fromIds.joinToString(",") { (-it.value).toString() }
        val response = vkClient.getGroups(groupIds = groupIdsString)

        if (response.error != null) {
            throw VkApiException("VK API error: ${response.error.error_msg}")
        }

        return response.response?.map { groupDto ->
            VkGroupProfile.of(
                id = -groupDto.id,
                name = groupDto.name,
                screenName = groupDto.screenName,
            )
        } ?: emptyList()
    }
}

class VkApiException(message: String) : RuntimeException(message)
