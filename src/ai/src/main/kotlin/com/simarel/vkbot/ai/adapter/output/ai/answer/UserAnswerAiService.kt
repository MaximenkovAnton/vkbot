package com.simarel.vkbot.ai.adapter.output.ai.answer

import com.simarel.vkbot.ai.adapter.output.ai.TransientChatMemoryProvider
import com.simarel.vkbot.ai.adapter.output.ai.tool.VaneSearchToolAdapter
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService
import jakarta.enterprise.context.ApplicationScoped
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@RegisterAiService(
    chatMemoryProviderSupplier = TransientChatMemoryProvider::class,
    tools = [VaneSearchToolAdapter::class]
)
@SystemMessage("{config:aiUseranswerSystemprompt}")
@ApplicationScoped
interface UserAnswerAiService {

    @UserMessage(
        """
        <профили_участников>
        {userProfiles}
        {groupProfiles}
        </профили_участников>

        <история_чата>
        {chatHistory}
        </история_чата>

        Текущая дата: {dateTime}
        <текущее_сообщение>
        {currentMessage}
        </текущее_сообщение>
    """,
    )
    fun generateAnswer(
        @V("userProfiles") userProfiles: String,
        @V("groupProfiles") groupProfiles: String,
        @V("chatHistory") chatHistory: String,
        @V("currentMessage") currentMessage: String,
        @V("dateTime") dateTime: String = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
    ): String
}
