package com.simarel.vkbot.objectProvider.fake.command.processor

import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommand
import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommandResponse
import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import java.util.concurrent.ConcurrentLinkedQueue

class FakeMessageAnswerTextGenerateCommand : MessageAnswerTextGenerateCommand {
    val executeCalls = ConcurrentLinkedQueue<MessageAnswerTextGenerateCommandRequest>()

    override fun execute(request: MessageAnswerTextGenerateCommandRequest): MessageAnswerTextGenerateCommandResponse {
        executeCalls.add(request)
        return MessageAnswerTextGenerateCommandResponse(FakeVoProvider.createMessageText())
    }
}
