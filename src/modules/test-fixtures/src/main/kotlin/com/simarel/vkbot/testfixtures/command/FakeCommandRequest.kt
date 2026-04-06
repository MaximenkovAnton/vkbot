package com.simarel.vkbot.testfixtures.command

import com.simarel.vkbot.share.command.CommandRequest

data class FakeCommandRequest(
    val value: String = "test",
) : CommandRequest