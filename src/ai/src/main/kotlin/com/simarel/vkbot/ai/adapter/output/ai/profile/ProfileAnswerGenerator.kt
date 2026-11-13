package com.simarel.vkbot.ai.adapter.output.ai.profile

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiProfileSystemprompt}")
interface ProfileAnswerGenerator {

    @UserMessage(
        """
        Ответь на вопрос о человеке по данным ниже. Верни только текст ответа.

        Вопрос: {question}

        ---
        {data}
        ---
    """,
    )
    fun answer(@V("question") question: String, @V("data") data: String): String
}
