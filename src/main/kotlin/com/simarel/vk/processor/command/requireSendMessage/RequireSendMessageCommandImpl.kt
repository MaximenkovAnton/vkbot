package com.simarel.vk.processor.command.requireSendMessage

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vk.processor.port.output.vk.VkSendMessageOutputPort
import com.simarel.vk.processor.port.output.vk.VkSendMessageOutputRequest
import com.simarel.vk.share.adapter.output.mq.PublishEventOutputAdapter
import com.simarel.vk.share.domain.Event
import com.simarel.vk.share.domain.vo.Payload
import com.simarel.vk.share.port.output.PublishEventOutputPortRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class RequireSendMessageCommandImpl(
    val publishEventOutputAdapter: PublishEventOutputAdapter,
    val objectMapper: ObjectMapper
) : RequireSendMessageCommand {
    val response = RequireSendMessageCommandResponse()

    override fun execute(request: RequireSendMessageCommandRequest): RequireSendMessageCommandResponse {
        publishEventOutputAdapter.execute(
            PublishEventOutputPortRequest(
                event = Event.MESSAGE_REQUIRE_ANSWER,
                payload = Payload(objectMapper.writeValueAsString(request.message)),
            )
        )
        return response
    }
}
