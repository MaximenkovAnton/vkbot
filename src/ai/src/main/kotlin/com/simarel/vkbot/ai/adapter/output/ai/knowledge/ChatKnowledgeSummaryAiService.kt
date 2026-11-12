package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiChatKnowledgeSummarySystemprompt}")
interface ChatKnowledgeSummaryAiService {

    @UserMessage(
        """
        Составь сводку чата по сообщениям ниже.
        Верни только связный текст: темы и направления чата, манера общения, повторяющиеся групповые факты, мемы и шутки. Не пересказывай сообщения по одному, не выдумывай, не списком сообщений. Не более ~2000 символов. Пиши по-русски.

        Сообщения ([имя]: текст):
        {messages}
        """,
    )
    fun summarize(@V("messages") messages: String): String
}
