package com.simarel.vkbot.ai.adapter.output.ai.news

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiNewsDigestSystemprompt}")
interface NewsDigestGenerator {

    @UserMessage(
        """
        Перескажи, что происходило в чате, по данным ниже. Верни только текст сводки.
{interests}{chatTopics}
        ---
        {data}
        ---
    """,
    )
    fun generateDigest(
        @V("data") data: String,
        @V("interests") interests: String,
        @V("chatTopics") chatTopics: String,
    ): String
}
