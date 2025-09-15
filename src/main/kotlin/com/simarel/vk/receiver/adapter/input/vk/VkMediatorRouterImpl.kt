package com.simarel.vk.receiver.adapter.input.vk

import com.simarel.vk.receiver.domain.vo.VkEvent
import com.simarel.vk.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vk.receiver.port.input.VkConfirmationInputPortRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.JsonObject
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("vk")
@ApplicationScoped
open class VkMediatorRouterImpl(val receiveMessageInputPort: ReceiveMessageInputPort): VkMediatorRouter {

    @POST()
    @Path("callback")
    @Produces(MediaType.TEXT_PLAIN)
    override fun callback(event: JsonObject): String {
        val responseHolder = receiveMessageInputPort.execute(
            VkConfirmationInputPortRequest(
                VkEvent(event)
            )
        )
        return responseHolder.response.value
    }
}