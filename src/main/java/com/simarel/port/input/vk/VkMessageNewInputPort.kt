package com.simarel.port.input.vk

import com.simarel.domain.model.Message

interface VkMessageNewInputPort: VkPort<VkMessageNewInputPortRequest, VkMessageNewInputPortResponse>

@JvmInline
value class VkMessageNewInputPortRequest(val message: Message): VkPortRequest

@JvmInline
value class VkMessageNewInputPortResponse(val value: String): VkPortResponse