package com.simarel.vkbot.processor.testfixtures.service

import com.simarel.vkbot.processor.adapter.output.ai.UserAnswerAiService
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.util.concurrent.ConcurrentLinkedQueue

class FakeUserAnswerAiService : UserAnswerAiService {
    val answerUserCalls = ConcurrentLinkedQueue<Pair<String, String>>()
    val answerUserMessageCalls = ConcurrentLinkedQueue<Message>()

    override fun answerUser(message: String, context: String): String {
        answerUserCalls.add(Pair(message, context))
        return FakeVoProvider.createMessageText().value
    }

    override fun answerUser(message: Message): String {
        answerUserMessageCalls.add(message)
        return FakeVoProvider.createMessageText().value
    }
}
