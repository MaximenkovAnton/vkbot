package com.simarel.vkbot.integration

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkUserProfileTable
import com.simarel.vkbot.persistence.domain.profile.ProfileFieldsMerger
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class ProfileHistoryAndStatisticsIntegrationTest {

    @Inject
    lateinit var userProfileTable: VkUserProfileTable

    @Test
    fun `profile details are stored as jsonb and read back`() {
        val profileId = 7_610_001L
        val saved = FakeModelProvider.createVkUserProfile(
            id = profileId,
            firstName = "Иван",
            lastName = "Петров",
            screenName = "ivan",
            birthDate = "01.01.1990",
            city = "Минск",
            details = mapOf("about" to "about me", "followers_count" to 42),
        )

        userProfileTable.persist(saved, null)
        val actualized = userProfileTable.findByIds(listOf(profileId)).single()

        assertEquals("about me", actualized.details["about"])
        assertEquals(42, actualized.details["followers_count"])
        assertEquals("Иван", actualized.firstName)
        assertEquals("Петров", actualized.lastName)
        assertEquals("ivan", actualized.screenName)
        assertEquals("01.01.1990", actualized.birthDate)
        assertEquals("Минск", actualized.city)
        assertEquals(saved.lastUpdated.toInstant().toEpochMilli(), actualized.lastUpdated.toInstant().toEpochMilli())
    }

    @Test
    fun `profile actualization keeps only human changed fields in history`() {
        val profileId = 7_610_003L
        val stored = FakeModelProvider.createVkUserProfile(
            id = profileId,
            city = "Moscow",
            details = mapOf("about" to "about me"),
        )
        val current = FakeModelProvider.createVkUserProfile(
            id = profileId,
            city = "Minsk",
            details = mapOf("about" to "about me"),
        )

        userProfileTable.persist(stored, null)
        userProfileTable.persist(current, ProfileFieldsMerger.diff(stored, current))

        val history = userProfileTable.findHistory(profileId, limit = 10, offset = 0)

        assertEquals(1, history.size)
        val entry = history.single()
        assertEquals(profileId, entry.profileId)
        assertNotNull(entry.changedAt)
        assertEquals(setOf("city"), entry.changes.keys)
        assertEquals(mapOf("old" to "Moscow", "new" to "Minsk"), entry.changes["city"])
        assertNull(entry.changes["about"])
    }
}
