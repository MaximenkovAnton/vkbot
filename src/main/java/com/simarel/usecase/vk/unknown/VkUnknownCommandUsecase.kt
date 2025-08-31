package com.simarel.usecase.vk.unknown

import com.simarel.port.input.vk.callback.VkUnknownPort
import com.simarel.port.input.vk.callback.VkUnknownRequest
import com.simarel.port.input.vk.callback.VkUnknownResponse
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkUnknownCommandUsecase: VkUnknownPort {
    companion object {
        val STATIC_RESPONSE = VkUnknownResponse("ok")
    }
    override fun execute(request: VkUnknownRequest): VkUnknownResponse {
        Log.error("Unknown VK command: ${request.request}")
        return STATIC_RESPONSE
    }

}