package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.quarkiverse.langchain4j.RegisterAiService

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier::class,
)
@SystemMessage("{config:aiKnowledgeSystemprompt}")
interface KnowledgeFactExtractor {

    @UserMessage(
        """
        Извлеки факты о людях из сообщений ниже.

        Каждая строка имеет вид [msg=<идентификатор сообщения>|from=<id автора>|<имя автора>]: текст.
        Верни только JSON-объект (без пояснений и без markdown):
        {|{"facts":[{"subjectId":<from автора, если факт о нём, иначе null>,"subjectName":<имя|null>,"category":"<INTEREST|HABIT|KNOWLEDGE|PROFILE_ATTRIBUTE|SOCIAL|EVENT>","key":"<^[a-z0-9._-]{1,96}$>","value":"<до 500 символов>","valuePayload":{<строка>:<значение>},"confidence":<0.0..1.0>,"sourceKind":"<SELF_STATEMENT|OTHER_STATEMENT|CONGRATULATION|INFERENCE>","sourceConversationMessageId":<msg той строки, из которой взят факт>}]}|}

        Сообщения:
        {messages}
        """,
    )
    fun extract(@V("messages") messages: String): String
}
