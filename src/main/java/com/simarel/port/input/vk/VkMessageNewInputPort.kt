package com.simarel.port.input.vk

import com.simarel.domain.model.Message

interface VkMessageNewInputPort: VkPort<VkMessageNewInputRequest, VkMessageNewInputResponse>

@JvmInline
value class VkMessageNewInputRequest(val message: Message): VkRequest

@JvmInline
value class VkMessageNewInputResponse(val value: String): VkResponse