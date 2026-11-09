package com.simarel.vkbot.receiver.domain.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class VkLongPollServerException(
    errorCode: Int?,
    errorSubcode: Int?,
    errorMessage: String?,
) : VkBotAppException(
    buildString {
        append("groups.getLongPollServer failed: code ${errorCode ?: "?"}")
        append(" subcode ${errorSubcode ?: "?"}")
        append(" message ${errorMessage ?: "?"}")
    },
) {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
