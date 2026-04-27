package com.simarel.vkbot.processor.testfixtures.command

import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand

class FakeIsRequireAnswerCommand(
    botId: Long = 12345L,
    botMention: String = "@testbot"
) : IsRequireAnswerCommand(botId, botMention) {

    var shouldRequireAnswer: Boolean = true

    override fun execute(message: com.simarel.vkbot.share.domain.model.Message): Boolean {
        return shouldRequireAnswer
    }
}
