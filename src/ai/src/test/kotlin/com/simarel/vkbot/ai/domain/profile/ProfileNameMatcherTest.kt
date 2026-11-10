package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

class ProfileNameMatcherTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("nameCases")
    fun `matches a full name written in another case form`(
        phrase: String,
        profiles: List<VkUserProfile>,
        expectedIds: List<Long>,
    ) {
        val matchedIds = ProfileNameMatcher().matches(phrase, profiles).map { it.id.value }

        assertEquals(expectedIds, matchedIds)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `returns an empty list for an empty phrase`(phrase: String) {
        val profiles = listOf(
            FakeModelProvider.createVkUserProfile(id = 7_600_001L, firstName = "Паша", lastName = "Селезнев"),
        )

        assertTrue(ProfileNameMatcher().matches(phrase, profiles).isEmpty())
    }

    @Test
    fun `matches the screen name without at sign`() {
        val profile = FakeModelProvider.createVkUserProfile(
            id = 7_600_003L,
            firstName = null,
            lastName = null,
            screenName = "pasha",
        )

        val matchedIds = ProfileNameMatcher().matches("pasha", listOf(profile)).map { it.id.value }

        assertEquals(listOf(7_600_003L), matchedIds)
    }

    @Test
    fun `does not match a diminutive outside the prefix`() {
        val pavel = FakeModelProvider.createVkUserProfile(
            id = 7_600_004L,
            firstName = "Павел",
            lastName = null,
            screenName = null,
        )
        val pasha = FakeModelProvider.createVkUserProfile(
            id = 7_600_005L,
            firstName = "Паша",
            lastName = "Селезнев",
            screenName = null,
        )
        val matcher = ProfileNameMatcher()

        assertTrue(matcher.matches("Паша", listOf(pavel)).isEmpty())
        assertEquals(listOf(7_600_005L), matcher.matches("Паша", listOf(pasha)).map { it.id.value })
    }

    @Test
    fun `returns every matching participant when several share the name`() {
        val first = FakeModelProvider.createVkUserProfile(
            id = 7_600_001L,
            firstName = "Паша",
            lastName = "Селезнев",
        )
        val second = FakeModelProvider.createVkUserProfile(
            id = 7_600_002L,
            firstName = "Паша",
            lastName = "Петров",
        )
        val other = FakeModelProvider.createVkUserProfile(
            id = 7_600_003L,
            firstName = "Маша",
            lastName = "Иванова",
        )

        val matchedIds = ProfileNameMatcher()
            .matches("Паша", listOf(first, other, second))
            .map { it.id.value }

        assertEquals(listOf(7_600_001L, 7_600_002L), matchedIds)
    }

    @Test
    fun `returns an empty list when no participant matches`() {
        val profile = FakeModelProvider.createVkUserProfile(
            id = 7_600_001L,
            firstName = "Паша",
            lastName = "Селезнев",
        )

        assertTrue(ProfileNameMatcher().matches("Вася Пупкин", listOf(profile)).isEmpty())
    }

    companion object {

        @JvmStatic
        fun nameCases(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "Паше Селезневе",
                listOf(
                    FakeModelProvider.createVkUserProfile(
                        id = 7_600_001L,
                        firstName = "Паша",
                        lastName = "Селезнев",
                        screenName = null,
                    ),
                ),
                listOf(7_600_001L),
            ),
            Arguments.of(
                "артему",
                listOf(
                    FakeModelProvider.createVkUserProfile(
                        id = 7_600_002L,
                        firstName = "Артём",
                        lastName = null,
                        screenName = null,
                    ),
                ),
                listOf(7_600_002L),
            ),
        )
    }
}
