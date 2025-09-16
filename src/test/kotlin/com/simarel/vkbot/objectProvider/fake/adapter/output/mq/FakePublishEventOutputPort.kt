package com.simarel.vkbot.objectProvider.fake.adapter.output.mq

import com.simarel.vkbot.share.port.output.PublishEventOutputPort
import com.simarel.vkbot.share.port.output.PublishEventOutputPortRequest
import com.simarel.vkbot.share.port.output.PublishEventOutputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakePublishEventOutputPort : PublishEventOutputPort {
    val executeCalls = ConcurrentLinkedQueue<PublishEventOutputPortRequest>()
    private val response = PublishEventOutputPortResponse()

    override fun execute(request: PublishEventOutputPortRequest): PublishEventOutputPortResponse {
        executeCalls.add(request)
        return response
    }
}
