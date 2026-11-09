package com.simarel.vkbot.processor.testfixtures.command

import com.simarel.vkbot.processor.command.isSpam.IsSpamCommand

object FakeIsSpamProvider {
    fun createIsSpamRequest(spamProbability: Double = 0.0): IsSpamCommand.IsSpamRequest = IsSpamCommand.IsSpamRequest(spamProbability = spamProbability)
}
