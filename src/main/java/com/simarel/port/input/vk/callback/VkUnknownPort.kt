package com.simarel.port.input.vk.callback

import  io.vertx.core.json.JsonObject

interface VkUnknownPort: VkPort<VkUnknownRequest, VkUnknownResponse>

class VkUnknownRequest(val request: JsonObject): VkRequest

@JvmInline
value class VkUnknownResponse(val value: String): VkResponse