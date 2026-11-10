package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.vkFacade.adapter.output.vk.VkClient
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkUserDto
import com.simarel.vkbot.vkFacade.port.output.vk.VkGetGroupProfilesBatchPort
import com.simarel.vkbot.vkFacade.port.output.vk.VkGetUserProfilesBatchPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

@ApplicationScoped
class VkGetUserProfilesBatchAdapter(
    @RestClient private val vkClient: VkClient,
    private val objectMapper: ObjectMapper,
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

        val profiles = response.response?.map { userDto ->
            VkUserProfile.of(
                id = userDto.id,
                firstName = userDto.firstName,
                lastName = userDto.lastName,
                screenName = userDto.screenName,
                birthDate = userDto.birthDate,
                city = userDto.city?.title,
                profileData = userDto.toProfileData(),
            )
        } ?: emptyList()
        return VkGetUserProfilesBatchPort.Response(profiles)
    }

    private fun VkUserDto.toProfileData(): Map<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        data["id"] = id
        firstName?.let { data["first_name"] = it }
        lastName?.let { data["last_name"] = it }
        screenName?.let { data["screen_name"] = it }
        birthDate?.let { data["bdate"] = it }
        city?.let { data["city"] = mapOf("id" to it.id, "title" to it.title) }
        extraFields.forEach { (name, value) ->
            if (value.isNull || name.isAvatarField()) {
                return@forEach
            }
            data[name] = objectMapper.convertValue(value, Any::class.java)
        }
        return data
    }

    private fun String.isAvatarField(): Boolean = startsWith("photo") || this == "has_photo" || this == "crop_photo"

    private companion object {
        const val USER_FIELDS = "verified,sex,bdate,city,country,home_town,online,lists,domain," +
            "has_mobile,contacts,site,education,universities,schools,status,last_seen,followers_count," +
            "common_count,occupation,nickname,relatives,relation,personal,connections,exports,wall_comments," +
            "wall_default,activities,interests,music,movies,tv,books,games,about,quotes,can_post," +
            "can_see_all_posts,can_see_audio,can_write_private_message,can_send_friend_request,is_favorite," +
            "is_hidden_from_feed,timezone,screen_name,maiden_name,is_friend,friend_status,career,military," +
            "blacklisted,blacklisted_by_me,counters"
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
