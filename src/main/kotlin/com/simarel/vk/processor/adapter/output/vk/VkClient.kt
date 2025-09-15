package com.simarel.vk.processor.adapter.output.vk

import io.quarkus.rest.client.reactive.ClientQueryParam
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey="vk")
@ClientQueryParam(name = "v", value = ["\${vk.api.version}"])
@ClientQueryParam(name = "disable_mentions", value = ["1"])
interface VkClient {

    @POST
    @Path("/messages.send")
    fun sendMessage(@FormParam("peer_id") peerId: Long, @FormParam("message") message: String, @FormParam("random_id") rand: Int)
}