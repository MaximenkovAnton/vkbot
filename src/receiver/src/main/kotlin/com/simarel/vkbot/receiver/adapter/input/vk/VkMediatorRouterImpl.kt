package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.adapter.input.vk.mapper.MessageMapper
import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.domain.vo.VkEvent
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.vkbot.share.domain.model.Message
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.JsonObject
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("vk")
@ApplicationScoped
open class VkMediatorRouterImpl(
    private val receiveMessageInputPort: ReceiveMessageInputPort,
    private val messageMapper: MessageMapper,
) : VkMediatorRouter {

    @POST()
    @Path("callback")
    @Produces(MediaType.TEXT_PLAIN)
    override fun callback(event: JsonObject): String {
        val type = event.getString("type")
            ?.let { VkCallbackEvent.mapOrUnknown(it) }
            ?: VkCallbackEvent.UNKNOWN

        val message: Message? = when (type) {
            VkCallbackEvent.MESSAGE_NEW, VkCallbackEvent.MESSAGE_REPLY -> messageMapper.toDomain(event)
            else -> null
        }

        val responseHolder = receiveMessageInputPort.execute(
            VkConfirmationInputPortRequest(VkEvent(type), message),
        )
        return responseHolder.response.value
    }
}
