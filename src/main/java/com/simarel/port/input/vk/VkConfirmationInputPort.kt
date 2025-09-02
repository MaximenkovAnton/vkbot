package com.simarel.port.input.vk

import com.simarel.domain.vo.ConfirmationCode

interface VkConfirmationInputPort: VkPort<VkConfirmationInputRequest, VkConfirmationInputResponse>

class VkConfirmationInputRequest: VkRequest

@JvmInline
value class VkConfirmationInputResponse(val confirmationCode: ConfirmationCode): VkResponse