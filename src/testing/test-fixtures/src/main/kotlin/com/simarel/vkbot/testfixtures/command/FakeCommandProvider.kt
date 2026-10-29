package com.simarel.vkbot.testfixtures.command

object FakeCommandProvider {

    fun createRequest(value: String = "test") = FakeCommandRequest(value = value)

    fun createResponse(result: String = "result") = FakeCommandResponse(result = result)
}
