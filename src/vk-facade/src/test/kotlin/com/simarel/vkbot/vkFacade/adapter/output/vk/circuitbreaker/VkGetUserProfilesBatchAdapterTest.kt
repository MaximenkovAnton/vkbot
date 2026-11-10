package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.simarel.vkbot.testfixtures.adapter.output.vk.FakeVkClient
import com.simarel.vkbot.testfixtures.domain.FakeVkDtoProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.vkFacade.FakeVkFacadeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VkGetUserProfilesBatchAdapterTest {

    @Test
    fun `requests only the stored profile fields`() {
        val vkClient = FakeVkClient(
            vkUsersResponseDto = FakeVkDtoProvider.createVkUsersResponseDto(
                response = listOf(FakeVkDtoProvider.createVkUserDto(id = 1)),
            ),
        )
        val adapter = VkGetUserProfilesBatchAdapter(vkClient)

        adapter.execute(
            FakeVkFacadeProvider.createVkGetUserProfilesBatchRequest(
                fromIds = listOf(FakeVoProvider.createFromId(1)),
            ),
        )

        val fields = vkClient.getUsersParameterCalls.single().fields.split(",")
        assertEquals(
            setOf(
                "verified", "sex", "bdate", "city", "country", "home_town", "domain", "site", "status",
                "about", "activities", "interests", "music", "movies", "tv", "books", "games", "quotes",
                "personal", "education", "universities", "schools", "career", "military", "relatives",
                "relation", "contacts", "connections", "followers_count", "counters", "maiden_name",
                "nickname", "timezone", "occupation", "screen_name",
            ),
            fields.toSet(),
        )
        assertEquals(35, fields.size)
        assertFalse(fields.contains("relation_partner"))
        assertFalse(fields.contains("last_seen"))
        assertFalse(fields.contains("online"))
        assertFalse(fields.contains("lists"))
        assertFalse(fields.contains("common_count"))
        assertFalse(fields.contains("friend_status"))
        assertFalse(fields.contains("wall_comments"))
        assertFalse(fields.any { it.startsWith("photo") || it == "crop_photo" || it == "has_photo" })
    }

    @Test
    fun `maps the human profile details through the mapper`() {
        val extraFieldsJson = "{\"about\":\"about me\",\"sex\":2,\"country\":{\"id\":1,\"title\":\"Россия\"}," +
            "\"counters\":{\"friends\":10},\"photo_200\":\"avatar\",\"crop_photo\":{\"x\":1},\"has_photo\":1}}"
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
        val adapter = VkGetUserProfilesBatchAdapter(vkClient)

        val response = adapter.execute(
            FakeVkFacadeProvider.createVkGetUserProfilesBatchRequest(
                fromIds = listOf(FakeVoProvider.createFromId(1)),
            ),
        )

        val profile = response.profiles.single()
        assertEquals("Ivan", profile.firstName)
        assertEquals("Moscow", profile.city)
        assertEquals("Мужской", profile.details["sex"])
        assertEquals("about me", profile.details["about"])
        assertEquals("Россия", profile.details["country"])
        assertEquals(mapOf("friends" to 10), profile.details["counters"])
        assertFalse(profile.details.containsKey("city"))
        assertFalse(profile.details.containsKey("id"))
        assertFalse(profile.details.containsKey("bdate"))
        assertFalse(profile.details.containsKey("photo_200"))
        assertFalse(profile.details.containsKey("crop_photo"))
        assertFalse(profile.details.containsKey("has_photo"))
    }

    @Test
    fun `propagates vk api error`() {
        val vkClient = FakeVkClient(
            vkUsersResponseDto = FakeVkDtoProvider.createVkUsersResponseDto(
                response = null,
                error = FakeVkDtoProvider.createVkError(errorCode = 5, errorMessage = "auth failed"),
            ),
        )
        val adapter = VkGetUserProfilesBatchAdapter(vkClient)

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
