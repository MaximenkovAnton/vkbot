package com.simarel.vkbot.objectProvider.fake.command.vkFacade

import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeSendVkMessageCommand : SendVkMessageCommand {
    val executeCalls = ConcurrentLinkedQueue<SendVkMessageCommandRequest>()
    private val response = SendVkMessageCommandResponse()

    override fun execute(request: SendVkMessageCommandRequest): SendVkMessageCommandResponse {
        executeCalls.add(request)
        return response
    }
}
