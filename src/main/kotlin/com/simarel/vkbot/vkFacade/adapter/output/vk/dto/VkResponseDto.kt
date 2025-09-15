package com.simarel.vkbot.vkFacade.adapter.output.vk.dto

data class VkResponseDto(val error: VkError?) {
}

data class VkError(val error_code: Int, val error_msg: String)