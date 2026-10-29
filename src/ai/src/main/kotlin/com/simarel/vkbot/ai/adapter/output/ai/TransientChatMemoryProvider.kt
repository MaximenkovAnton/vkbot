package com.simarel.vkbot.ai.adapter.output.ai

import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.util.function.Supplier

private const val MAX_MESSAGES = 10

@ApplicationScoped
class TransientChatMemoryProvider : Supplier<ChatMemoryProvider> {
    @Produces
    override fun get(): ChatMemoryProvider {
        // In-memory chat memory only for current request context
        // Does not persist anything between requests
        return ChatMemoryProvider { MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES) }
    }
}
