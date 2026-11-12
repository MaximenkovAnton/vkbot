package com.simarel.vkbot.processor.testfixtures.command

import com.simarel.vkbot.processor.command.knowledge.RequestKnowledgeExtractionCommand
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider

object FakeKnowledgeProvider {

    fun createRequest(
        message: Message = FakeMessageProvider.createMessage(),
    ): RequestKnowledgeExtractionCommand.Request = RequestKnowledgeExtractionCommand.Request(message = message)
}
