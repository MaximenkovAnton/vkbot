package com.simarel.vkbot.processor.testfixtures.command

import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand

class FakeIsRequireAnswerCommand : IsRequireAnswerCommand {

    var shouldRequireAnswer: Boolean = true

    override fun execute(request: IsRequireAnswerCommand.IsRequireAnswerRequest): IsRequireAnswerCommand.IsRequireAnswerResponse = IsRequireAnswerCommand.IsRequireAnswerResponse(shouldRequireAnswer)
}
