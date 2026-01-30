package com.simarel.vkbot.objectProvider.fake.command

import com.simarel.vkbot.share.command.Command
import java.util.concurrent.ConcurrentLinkedQueue

class FakeCommand : Command<FakeCommandRequest, FakeCommandResponse> {
    
    val executeCalls = ConcurrentLinkedQueue<FakeCommandRequest>()
    var response: FakeCommandResponse = FakeCommandResponse()
    
    override fun execute(request: FakeCommandRequest): FakeCommandResponse {
        executeCalls.add(request)
        return response
    }
}