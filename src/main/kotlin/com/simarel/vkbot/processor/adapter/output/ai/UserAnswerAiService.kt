package com.simarel.vkbot.processor.adapter.output.ai

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped

@RegisterAiService
@SystemMessage(
    """
    You are a helpful ai chatbot in a chat. Your task is to answer user questions in Russian.
    Use the context of forwarded messages if provided.
    The format of forwarded messages will be: [sender]: [message]
""",
)
@ApplicationScoped
interface UserAnswerAiService {
    @UserMessage(
        """
        {context}
        User message: {message}
    """,
    )
    fun answerUser(@V("message") message: String, @V("context") context: String): String
}
