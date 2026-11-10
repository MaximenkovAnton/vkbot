package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.testfixtures.adapter.output.vk.FakeVkClient
import com.simarel.vkbot.testfixtures.domain.FakeVkDtoProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.vkFacade.FakeVkFacadeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VkGetUserProfilesBatchAdapterTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `requests every profile field vk can return except avatars`() {
        val vkClient = FakeVkClient(
            vkUsersResponseDto = FakeVkDtoProvider.createVkUsersResponseDto(
                response = listOf(FakeVkDtoProvider.createVkUserDto(id = 1)),
            ),
        )
        val adapter = VkGetUserProfilesBatchAdapter(vkClient, objectMapper)

        adapter.execute(
            FakeVkFacadeProvider.createVkGetUserProfilesBatchRequest(
                fromIds = listOf(FakeVoProvider.createFromId(1)),
            ),
        )

        val fields = vkClient.getUsersParameterCalls.single().fields.split(",")
        assertTrue(fields.contains("about"))
        assertTrue(fields.contains("counters"))
        assertTrue(fields.contains("verified"))
        assertTrue(fields.contains("friend_status"))
        assertFalse(fields.any { it.startsWith("photo") || it == "crop_photo" })
        assertFalse(fields.contains("has_photo"))
    }

    @Test
    fun `maps the whole vk user payload into profile data without avatar fields`() {
        val extraFieldsJson = "{\"about\":\"about me\",\"counters\":{\"friends\":10}," +
            "\"photo_200\":\"avatar\",\"crop_photo\":{\"x\":1},\"has_photo\":1}}"
        val userDto = FakeVkDtoProvider.createVkUserDto(
            id = 1,
            firstName = "Ivan",
            lastName = "Petrov",
            screenName = "ivan_petrov",
            birthDate = "01.01.1990",
            city = FakeVkDtoProvider.createVkCityDto(id = 2, title = "Moscow"),
            extraFieldsJson = extraFieldsJson,
        )
        val vkClient = FakeVkClient(
            vkUsersResponseDto = FakeVkDtoProvider.createVkUsersResponseDto(response = listOf(userDto)),
        )
        val adapter = VkGetUserProfilesBatchAdapter(vkClient, objectMapper)

        val response = adapter.execute(
            FakeVkFacadeProvider.createVkGetUserProfilesBatchRequest(
                fromIds = listOf(FakeVoProvider.createFromId(1)),
            ),
        )

        val profile = response.profiles.single()
        assertEquals("Ivan", profile.firstName)
        assertEquals("Moscow", profile.city)
        assertEquals("about me", profile.profileData["about"])
        assertEquals(mapOf("friends" to 10), profile.profileData["counters"])
        assertEquals(mapOf("id" to 2, "title" to "Moscow"), profile.profileData["city"])
        assertFalse(profile.profileData.containsKey("photo_200"))
        assertFalse(profile.profileData.containsKey("crop_photo"))
        assertFalse(profile.profileData.containsKey("has_photo"))
    }

    @Test
    fun `propagates vk api error`() {
        val vkClient = FakeVkClient(
            vkUsersResponseDto = FakeVkDtoProvider.createVkUsersResponseDto(
                response = null,
                error = FakeVkDtoProvider.createVkError(errorCode = 5, errorMessage = "auth failed"),
            ),
        )
        val adapter = VkGetUserProfilesBatchAdapter(vkClient, objectMapper)

        val failure = runCatching {
            adapter.execute(
                FakeVkFacadeProvider.createVkGetUserProfilesBatchRequest(
                    fromIds = listOf(FakeVoProvider.createFromId(1)),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is VkApiException)
        assertTrue(failure?.message?.contains("auth failed") == true)
    }
}
