package com.simarel.vkbot.ai.adapter.output.ai.tool.vane.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class VaneProviderNotConfiguredException : VkBotAppException("No providers configured in Vane") {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
