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
        Кого поздравляем:
        {subjects}

        Напиши короткое поздравление по-русски, без markdown и списков. Если человек один — 1–3 предложения;
        если несколько — по одному предложению каждому, с именем.
    """,
    )
    fun generateGreeting(
        @V("date") date: String,
        @V("occasion") occasion: String,
        @V("subjects") subjects: String,
    ): String
}
