package com.simarel.usecase.vk.unknown

import com.simarel.port.input.vk.VkUnknownPort
import com.simarel.port.input.vk.VkUnknownPortRequest
import com.simarel.port.input.vk.VkUnknownPortResponse
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkUnknownCommandUsecase: VkUnknownPort {
    val okResponse = VkUnknownPortResponse("ok") // Отвечаем ок, чтоб вк не ддосил неподдерживаемыми ивентами

    override fun execute(request: VkUnknownPortRequest): VkUnknownPortResponse {
        Log.error("Unknown VK command: ${request.request}")
        return okResponse
    }

}