package com.simarel.vkbot.persistence.domain.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import java.util.UUID

class SummaryNotFoundException(summaryId: UUID) : VkBotAppException("Summary not found: $summaryId") {
    override fun status(): ExceptionStatus = ExceptionStatus.NOT_FOUND
}
