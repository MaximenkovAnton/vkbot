package com.simarel.vkbot.testfixtures.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkCityDto
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkError
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkUserDto
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkUsersResponseDto

object FakeVkDtoProvider {

    private val objectMapper = ObjectMapper()

    @Suppress("LongParameterList")
    fun createVkUserDto(
        id: Long = 1L,
        firstName: String? = "Ivan",
        lastName: String? = "Petrov",
        screenName: String? = "ivan_petrov",
        birthDate: String? = null,
        city: VkCityDto? = null,
        groupName: String? = null,
        extraFieldsJson: String = "{}",
    ): VkUserDto = VkUserDto(
        id = id,
        firstName = firstName,
        lastName = lastName,
        screenName = screenName,
        birthDate = birthDate,
        city = city,
        groupName = groupName,
    ).also { dto ->
        objectMapper.readTree(extraFieldsJson).fields().forEach { (name, value) ->
            dto.extraFields[name] = value
        }
    }

    fun createVkCityDto(id: Int = 1, title: String = "Moscow"): VkCityDto = VkCityDto(id = id, title = title)

    fun createVkUsersResponseDto(
        response: List<VkUserDto>? = emptyList(),
        error: VkError? = null,
    ): VkUsersResponseDto = VkUsersResponseDto(response = response, error = error)

    fun createVkError(errorCode: Int = 5, errorMessage: String = "error"): VkError = VkError(
        error_code = errorCode,
        error_msg = errorMessage,
    )
}
