package com.simarel.processor.port.input.vk

import com.simarel.processor.domain.model.Message

interface VkMessageNewInputPort: VkPort<VkMessageNewInputPortRequest, VkMessageNewInputPortResponse>

@JvmInline
value class VkMessageNewInputPortRequest(val message: Message): VkPortRequest

@JvmInline
value class VkMessageNewInputPortResponse(val value: String): VkPortResponse