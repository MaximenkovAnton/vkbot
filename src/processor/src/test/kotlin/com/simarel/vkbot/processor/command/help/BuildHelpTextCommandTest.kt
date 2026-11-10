package com.simarel.vkbot.processor.command.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildHelpTextCommandTest {

    @Test
    fun `execute returns the documented help text with the configured bot mention`() {
        val command = BuildHelpTextCommandImpl("@testbot")

        val response = command.execute(BuildHelpTextCommand.BuildHelpTextRequest)

        assertEquals(
            """
            Я — бот сообщества. Вот что я умею:
            • «Что нового» — расскажу, что происходило в чате за выбранный срок. Примеры: «что нового за вчера», «что нового с 1 по 3 ноября». Если срок не указать, возьму время с вашего последнего сообщения, но не больше недели.
            • «Что ты умеешь», /help или /помощь — эта справка: что я умею и как меня вызвать.
            В групповом чате упомяните бота, например: @testbot что нового за вчера. Остальные вопросы получат обычный AI-ответ.
            """.trimIndent(),
            response.messageText.value,
        )
    }
}
