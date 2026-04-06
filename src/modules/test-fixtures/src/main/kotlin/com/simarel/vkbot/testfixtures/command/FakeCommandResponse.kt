package com.simarel.vkbot.testfixtures.command

import com.simarel.vkbot.share.command.CommandResponse

data class FakeCommandResponse(
    val result: String = "result",
) : CommandResponse