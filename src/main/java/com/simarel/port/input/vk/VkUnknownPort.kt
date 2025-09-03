package com.simarel.port.input.vk

import  io.vertx.core.json.JsonObject

interface VkUnknownPort: VkPort<VkUnknownPortRequest, VkUnknownPortResponse>

@JvmInline
value class VkUnknownPortRequest(val request: JsonObject): VkPortRequest

@JvmInline
value class VkUnknownPortResponse(val value: String): VkPortResponse