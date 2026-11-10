package com.simarel.vkbot.persistence.domain.profile

import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileFieldsMergerTest {

    @Test
    fun `merge keeps keys vk did not return and clears with empty collections`() {
        val stored = mapOf<String, Any?>(
            "about" to "old about",
            "status" to "old status",
            "career" to listOf(mapOf("company" to "Acme")),
        )
        val fetched = mapOf<String, Any?>(
            "status" to null,
            "about" to "new about",
            "career" to emptyList<Any?>(),
        )

        val merged = ProfileFieldsMerger.merge(stored, fetched)

        assertEquals("old status", merged["status"])
        assertEquals("new about", merged["about"])
        assertEquals(emptyList<Any?>(), merged["career"])
    }

    @Test
    fun `diff records typed and details changes by column names`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            firstName = "Ivan",
            birthDate = "01.01.1990",
            details = mapOf("about" to "старое", "status" to "same"),
        )
        val current = previous.copy(
            firstName = "Petr",
            birthDate = "02.02.1991",
            details = mapOf("about" to "новое", "status" to "same"),
        )

        val changes = ProfileFieldsMerger.diff(previous, current)

        assertEquals(setOf("first_name", "birth_date", "about"), changes.keys)
        assertEquals(mapOf("old" to "Ivan", "new" to "Petr"), changes["first_name"])
        assertEquals(mapOf("old" to "01.01.1990", "new" to "02.02.1991"), changes["birth_date"])
        assertEquals(mapOf("old" to "старое", "new" to "новое"), changes["about"])
        assertFalse(changes.containsKey("bdate"))
    }

    @Test
    fun `diff of identical profiles is empty`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            details = mapOf("about" to "same", "counters" to mapOf("friends" to 1)),
        )

        assertTrue(ProfileFieldsMerger.diff(previous, previous).isEmpty())
    }

    @Test
    fun `diff falls back to typed fields when previous details are empty`() {
        val previous = FakeModelProvider.createVkUserProfile(
            id = 1,
            firstName = "Ivan",
            city = "Moscow",
            details = emptyMap(),
        )
        val current = previous.copy(firstName = "Petr", details = mapOf("about" to "new"))

        val changes = ProfileFieldsMerger.diff(previous, current)

        assertEquals(setOf("first_name"), changes.keys)
        assertEquals("Ivan", changes.getValue("first_name")["old"])
        assertEquals("Petr", changes.getValue("first_name")["new"])
    }
}
