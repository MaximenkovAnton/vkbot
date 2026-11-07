package com.simarel.vkbot.greetings.domain

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class NameFormatterTest {

    @ParameterizedTest
    @MethodSource("cases")
    fun `format falls back from the full name to the screen name and the id`(case: NameCase) {
        assertEquals(case.expected, NameFormatter.format(case.profile))
    }

    data class NameCase(val profile: VkUserProfile, val expected: String)

    companion object {
        @JvmStatic
        fun cases(): Stream<NameCase> = Stream.of(
            NameCase(
                FakeModelProvider.createVkUserProfile(id = 1, firstName = "Иван", lastName = "Петров"),
                "Иван Петров",
            ),
            NameCase(
                FakeModelProvider.createVkUserProfile(id = 2, firstName = null, lastName = "Петров"),
                "Петров",
            ),
            NameCase(
                FakeModelProvider.createVkUserProfile(id = 3, firstName = "  ", lastName = null, screenName = "ivan"),
                "ivan",
            ),
            NameCase(
                FakeModelProvider.createVkUserProfile(id = 4, firstName = null, lastName = null, screenName = " "),
                "4",
            ),
            NameCase(
                FakeModelProvider.createVkUserProfile(id = 5, firstName = " Иван ", lastName = " Петров "),
                "Иван Петров",
            ),
        )
    }
}
