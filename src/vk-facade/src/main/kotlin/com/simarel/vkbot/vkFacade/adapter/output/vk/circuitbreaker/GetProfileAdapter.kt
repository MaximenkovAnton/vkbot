package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.vkFacade.adapter.output.vk.VkClient
import com.simarel.vkbot.vkFacade.port.output.vk.VkGetGroupProfilesBatchPort
import com.simarel.vkbot.vkFacade.port.output.vk.VkGetUserProfilesBatchPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class VkGetUserProfilesBatchAdapter(
    @RestClient private val vkClient: VkClient,
) : VkGetUserProfilesBatchPort {
    override fun execute(request: VkGetUserProfilesBatchPort.Request): VkGetUserProfilesBatchPort.Response {
        val fromIds = request.fromIds
        if (fromIds.isEmpty()) {
            return VkGetUserProfilesBatchPort.Response(emptyList())
        }

        val userIdsString = fromIds.joinToString(",") { it.value.toString() }
        val response = vkClient.getUsers(
            userIds = userIdsString,
            fields = USER_FIELDS,
        )

        if (response.error != null) {
            throw VkApiException("VK API error: ${response.error.error_msg}")
        }

        val profiles = response.response?.map(VkUserProfileMapper::map) ?: emptyList()
        return VkGetUserProfilesBatchPort.Response(profiles)
    }

    private companion object {
        const val USER_FIELDS = "verified,sex,bdate,city,country,home_town,domain,site,status,about," +
            "activities,interests,music,movies,tv,books,games,quotes,personal,education,universities," +
            "schools,career,military,relatives,relation,contacts,connections,followers_count,counters," +
            "maiden_name,nickname,timezone,occupation,screen_name"
    }
}

@ApplicationScoped
class VkGetGroupProfilesBatchAdapter(
    @RestClient private val vkClient: VkClient,
) : VkGetGroupProfilesBatchPort {
    override fun execute(request: VkGetGroupProfilesBatchPort.Request): VkGetGroupProfilesBatchPort.Response {
        val fromIds = request.fromIds
        if (fromIds.isEmpty()) {
            return VkGetGroupProfilesBatchPort.Response(emptyList())
        }

        val groupIdsString = fromIds.joinToString(",") { (-it.value).toString() }
        val response = vkClient.getGroups(groupIds = groupIdsString)

        if (response.error != null) {
            throw VkApiException("VK API error: ${response.error.error_msg}")
        }

        val profiles = response.response?.groups?.map { groupDto ->
            VkGroupProfile.of(
                id = -groupDto.id,
                name = groupDto.name,
                screenName = groupDto.screenName,
            )
        } ?: emptyList()
        return VkGetGroupProfilesBatchPort.Response(profiles)
    }
}

class VkApiException(message: String) : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
