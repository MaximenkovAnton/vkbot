package com.simarel.vkbot.receiver.domain.exception

class VkLongPollServerException(
    errorCode: Int?,
    errorSubcode: Int?,
    errorMessage: String?,
) : RuntimeException(
    buildString {
        append("groups.getLongPollServer failed: code ${errorCode ?: "?"}")
        append(" subcode ${errorSubcode ?: "?"}")
        append(" message ${errorMessage ?: "?"}")
    },
)
