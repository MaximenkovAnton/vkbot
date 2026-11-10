package com.simarel.vkbot.persistence.domain.profile

import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileDataMergerTest {

    @Test
    fun `merge keeps stored fields that vk does not return and overrides the ones it does`() {
        val stored = mapOf<String, Any?>(
            "about" to "old about",
            "city" to mapOf("id" to 1, "title" to "Moscow"),
            "status" to "old status",
        )
        val fetched = mapOf<String, Any?>(
            "city" to mapOf("id" to 2, "title" to "Minsk"),
            "status" to null,
        )

        val merged = ProfileDataMerger.merge(stored, fetched)

        assertEquals("old about", merged["about"])
        assertEquals("old status", merged["status"])
        assertEquals(mapOf("id" to 2, "title" to "Minsk"), merged["city"])
    }

    @Test
    fun `diff records old and new values of changed fields only`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            profileData = mapOf(
                "city" to mapOf("id" to 1, "title" to "Moscow"),
                "about" to "same",
            ),
        )
        val current = previous.copy(
            profileData = mapOf(
                "city" to mapOf("id" to 2, "title" to "Minsk"),
                "about" to "same",
            ),
        )

        val changes = ProfileDataMerger.diff(previous, current)

        assertEquals(setOf("city"), changes.keys)
        assertEquals(mapOf("id" to 1, "title" to "Moscow"), changes.getValue("city")["old"])
        assertEquals(mapOf("id" to 2, "title" to "Minsk"), changes.getValue("city")["new"])
    }

    @Test
    fun `diff ignores equal values regardless of number type and map order`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            profileData = mapOf(
                "counters" to mapOf("friends" to 10, "followers" to 20L),
            ),
        )
        val current = previous.copy(
            profileData = mapOf(
                "counters" to linkedMapOf<String, Any?>("followers" to 20, "friends" to 10L),
            ),
        )

        assertTrue(ProfileDataMerger.diff(previous, current).isEmpty())
    }

    @Test
    fun `diff falls back to typed fields for profiles stored before profile data existed`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            firstName = "Ivan",
            city = "Moscow",
            profileData = emptyMap(),
        )
        val current = previous.copy(firstName = "Petr", city = "Minsk")

        val changes = ProfileDataMerger.diff(previous, current)

        assertEquals(setOf("first_name", "city"), changes.keys)
        assertEquals("Ivan", changes.getValue("first_name")["old"])
        assertEquals("Petr", changes.getValue("first_name")["new"])
        assertEquals("Moscow", changes.getValue("city")["old"])
        assertEquals("Minsk", changes.getValue("city")["new"])
    }

    @Test
    fun `diff of identical profiles is empty`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            profileData = mapOf("about" to "same", "counters" to mapOf("friends" to 1)),
        )

        assertTrue(ProfileDataMerger.diff(previous, previous).isEmpty())
    }
}
