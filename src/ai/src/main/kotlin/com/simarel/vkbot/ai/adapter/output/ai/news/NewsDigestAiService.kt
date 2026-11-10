package com.simarel.vkbot.ai.adapter.output.ai.news

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiNewsDigestSystemprompt}")
interface NewsDigestAiService {

    @UserMessage(
        """
        Перескажи, что происходило в чате, по данным ниже. Верни только текст сводки.

        ---
        {data}
        ---
    """,
    )
    fun generateDigest(@V("data") data: String): String
}
