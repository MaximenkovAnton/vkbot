package com.simarel.vkbot.processor.testfixtures.command

import com.simarel.vkbot.processor.command.isJailbreak.IsJailbreakCommand

object FakeIsJailbreakProvider {
    fun createIsJailbreakRequest(jailbreakProbability: Double = 0.0): IsJailbreakCommand.IsJailbreakRequest = IsJailbreakCommand.IsJailbreakRequest(jailbreakProbability = jailbreakProbability)
}
