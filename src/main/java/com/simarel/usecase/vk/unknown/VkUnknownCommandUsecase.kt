package com.simarel.usecase.vk.unknown

import com.simarel.port.input.vk.callback.VkUnknownPort
import com.simarel.port.input.vk.callback.VkUnknownRequest
import com.simarel.port.input.vk.callback.VkUnknownResponse
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkUnknownCommandUsecase: VkUnknownPort {
    val okResponse = VkUnknownResponse("ok") // Отвечаем ок, чтоб вк не ддосил неподдерживаемыми ивентами

    override fun execute(request: VkUnknownRequest): VkUnknownResponse {
        Log.error("Unknown VK command: ${request.request}")
        return okResponse
    }

}