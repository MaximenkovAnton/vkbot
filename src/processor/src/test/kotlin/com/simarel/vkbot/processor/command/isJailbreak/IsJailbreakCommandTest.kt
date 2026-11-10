package com.simarel.vkbot.processor.command.isJailbreak

import com.simarel.vkbot.processor.testfixtures.command.FakeIsJailbreakProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IsJailbreakCommandTest {

    @ParameterizedTest
    @CsvSource("0.9, 0.9, true", "0.9, 0.908, true", "0.9, 0.999, true")
    fun `execute treats a probability at or above the threshold as a jailbreak`(
        threshold: Double,
        probability: Double,
        expected: Boolean,
    ) {
        val command = IsJailbreakCommandImpl(threshold)

        val result = command.execute(FakeIsJailbreakProvider.createIsJailbreakRequest(probability))

        assertEquals(expected, result.isJailbreak)
    }

    @ParameterizedTest
    @CsvSource("0.9, 0.899, false", "0.9, 0.0, false")
    fun `execute treats a probability below the threshold as not a jailbreak`(
        threshold: Double,
        probability: Double,
        expected: Boolean,
    ) {
        val command = IsJailbreakCommandImpl(threshold)

        val result = command.execute(FakeIsJailbreakProvider.createIsJailbreakRequest(probability))

        assertEquals(expected, result.isJailbreak)
    }

    @ParameterizedTest
    @CsvSource("0.5, 0.7, true", "0.5, 0.4, false")
    fun `execute honours a configured threshold`(
        threshold: Double,
        probability: Double,
        expected: Boolean,
    ) {
        val command = IsJailbreakCommandImpl(threshold)

        val result = command.execute(FakeIsJailbreakProvider.createIsJailbreakRequest(probability))

        assertEquals(expected, result.isJailbreak)
    }
}
