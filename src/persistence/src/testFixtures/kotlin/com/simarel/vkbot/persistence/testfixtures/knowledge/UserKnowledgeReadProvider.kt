package com.simarel.vkbot.persistence.testfixtures.knowledge

import com.simarel.vkbot.persistence.domain.knowledge.StoredUserKnowledgeSummary
import java.time.OffsetDateTime

private val FIXED_SUMMARY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-01T00:00:00Z")

object UserKnowledgeReadProvider {

    fun createStoredUserKnowledgeSummary(
        userId: Long = 10,
        summaryText: String = "сводка",
        lastFactAt: OffsetDateTime = FIXED_SUMMARY_AT,
        summarizedAt: OffsetDateTime = FIXED_SUMMARY_AT,
    ) = StoredUserKnowledgeSummary(
        userId = userId,
        summaryText = summaryText,
        lastFactAt = lastFactAt,
        summarizedAt = summarizedAt,
    )
}
