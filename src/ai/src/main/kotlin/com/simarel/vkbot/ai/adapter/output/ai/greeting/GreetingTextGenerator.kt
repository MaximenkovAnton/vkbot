package com.simarel.vkbot.ai.adapter.output.ai.greeting

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiGreetingSystemprompt}")
interface GreetingTextGenerator {

    @UserMessage(
        """
        Дата: {date}
        Повод: {occasion}
        Кого поздравляем: {people}

        Напиши одно короткое поздравление по-русски: 1–3 предложения, без markdown и списков.
    """,
    )
    fun generateGreeting(
        @V("date") date: String,
        @V("occasion") occasion: String,
        @V("people") people: String,
    ): String
}
