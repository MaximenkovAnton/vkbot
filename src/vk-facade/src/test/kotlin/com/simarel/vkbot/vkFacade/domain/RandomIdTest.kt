package com.simarel.vkbot.vkFacade.domain

import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RandomIdTest {

    private val peerId = FakeVoProvider.createPeerId(value = 2_000_000_001)
    private val messageText = FakeVoProvider.createMessageText(value = "С Новым годом!")

    @Test
    fun `same peer and text give the same random id`() {
        assertEquals(RandomId.of(peerId, messageText), RandomId.of(peerId, messageText))
    }

    @Test
    fun `random id depends on peer and text`() {
        val otherPeerId = FakeVoProvider.createPeerId(value = 2_000_000_002)
        val otherMessageText = FakeVoProvider.createMessageText(value = "С Днём программиста!")

        assertNotEquals(RandomId.of(peerId, messageText), RandomId.of(otherPeerId, messageText))
        assertNotEquals(RandomId.of(peerId, messageText), RandomId.of(peerId, otherMessageText))
    }

    @ParameterizedTest
    @CsvSource("0, 1", "7, 7", "-7, -7")
    fun `zero random id is replaced with one`(value: Int, expected: Int) {
        assertEquals(expected, RandomId.of(value).value)
    }
}
