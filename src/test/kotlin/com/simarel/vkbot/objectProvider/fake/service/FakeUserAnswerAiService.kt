package com.simarel.vkbot.objectProvider.fake.service

import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import com.simarel.vkbot.processor.adapter.output.ai.UserAnswerAiService
import java.util.concurrent.ConcurrentLinkedQueue

class FakeUserAnswerAiService : UserAnswerAiService {
    val answerUserCalls = ConcurrentLinkedQueue<String>()

    override fun answerUser(message: String): String {
        answerUserCalls.add(message)
        return FakeVoProvider.createMessageText().value
    }
}
