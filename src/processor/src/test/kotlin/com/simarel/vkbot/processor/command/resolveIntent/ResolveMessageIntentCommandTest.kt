package com.simarel.vkbot.processor.command.resolveIntent

import com.simarel.vkbot.processor.domain.decision.MessageIntent
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ResolveMessageIntentCommandTest {

    @ParameterizedTest
    @CsvSource("/help", "' /help'", "'/help '", "/HELP", "/Помощь", "/помощь")
    fun `execute returns HELP for an exact slash command`(messageText: String) {
        assertEquals(MessageIntent.HELP, resolve(messageText))
    }

    @ParameterizedTest
    @CsvSource("/help что нового", "/help@simarel", "/HELP extra")
    fun `execute returns GENERAL for a help command with arguments`(messageText: String) {
        assertEquals(MessageIntent.GENERAL, resolve(messageText))
    }

    @ParameterizedTest
    @CsvSource("!help", "/start", "help", "что ты умеешь", "''")
    fun `execute returns null for anything that is not a help command`(messageText: String) {
        assertNull(resolve(messageText))
    }

    private fun resolve(messageText: String): MessageIntent? = ResolveMessageIntentCommandImpl().execute(
        ResolveMessageIntentCommand.ResolveMessageIntentRequest(FakeVoProvider.createMessageText(messageText)),
    ).intent
}
