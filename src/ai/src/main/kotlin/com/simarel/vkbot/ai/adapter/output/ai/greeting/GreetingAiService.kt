package com.simarel.vkbot.ai.adapter.output.ai.greeting

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiGreetingSystemprompt}")
interface GreetingAiService {

    @UserMessage(
        """
        Дата: {date}
        Праздники: {holidays}
        Именинники: {people}

        Напиши одно короткое поздравление по-русски: 1–3 предложения, без markdown и списков.
    """,
    )
    fun generateGreeting(
        @V("date") date: String,
        @V("holidays") holidays: String,
        @V("people") people: String,
    ): String
}
