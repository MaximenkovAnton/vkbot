package com.simarel.vkbot.persistence.domain.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class VkUserProfileJsonTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create().registerModule(JavaTimeModule())

    @Test
    fun `serializes human details instead of profile data`() {
        val profile = FakeModelProvider.createVkUserProfile(
            id = 7_620_001,
            firstName = "Иван",
            lastName = "Петров",
            screenName = "ivan",
            birthDate = "01.01.1990",
            city = "Минск",
            details = mapOf("about" to "about me", "followers_count" to 42),
        )

        val json = objectMapper.readTree(objectMapper.writeValueAsString(profile))

        assertEquals(
            mapOf("about" to "about me", "followers_count" to 42),
            objectMapper.convertValue(json["details"], Map::class.java),
        )
        assertFalse(json.has("profileData"))
        assertFalse(json.has("profile_data"))
    }
}
