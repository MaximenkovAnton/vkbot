package com.simarel.vkbot.vkFacade.adapter.output.vk.dto

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

data class VkResponseDto(val error: VkError?)

@Suppress("ConstructorParameterNaming")
data class VkError(val error_code: Int, val error_msg: String)

@Suppress("ConstructorParameterNaming")
data class VkUsersResponseDto(
    val response: List<VkUserDto>?,
    val error: VkError?,
)

@Suppress("ConstructorParameterNaming")
data class VkUserDto(
    val id: Long,
    @JsonProperty("first_name")
    val firstName: String?,
    @JsonProperty("last_name")
    val lastName: String?,
    @JsonProperty("screen_name")
    val screenName: String?,
    @JsonProperty("bdate")
    val birthDate: String?,
    val city: VkCityDto?,
    @JsonProperty("name")
    val groupName: String?,
) {
    val extraFields: MutableMap<String, JsonNode> = mutableMapOf()

    @JsonAnySetter
    fun putExtraField(name: String, value: JsonNode) {
        extraFields[name] = value
    }
}

@Suppress("ConstructorParameterNaming")
data class VkCityDto(
    val id: Int,
    val title: String,
)

@Suppress("ConstructorParameterNaming")
data class VkGroupsResponseDto(
    val response: VkGroupsResponseData?,
    val error: VkError?,
)

@Suppress("ConstructorParameterNaming")
data class VkGroupsResponseData(
    val groups: List<VkGroupDto>?,
    val profiles: List<VkUserDto>?,
)

@Suppress("ConstructorParameterNaming")
data class VkGroupDto(
    val id: Long,
    val name: String,
    @JsonProperty("screen_name")
    val screenName: String?,
)
