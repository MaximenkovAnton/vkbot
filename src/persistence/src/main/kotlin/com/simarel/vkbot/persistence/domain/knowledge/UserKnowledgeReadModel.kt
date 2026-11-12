package com.simarel.vkbot.persistence.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import java.time.OffsetDateTime

object UserKnowledgeReadModel {

    fun assemble(
        userIds: List<Long>,
        facts: List<UserFact>,
        summaries: List<StoredUserKnowledgeSummary>,
    ): Map<Long, UserKnowledge> {
        val factsByUser = facts.groupBy { it.userId }
        val summariesByUser = summaries.associateBy { it.userId }
        return userIds.distinct().associateWith { userId ->
            val userFacts = factsByUser[userId].orEmpty()
            val summary = summariesByUser[userId]
            UserKnowledge(
                facts = userFacts,
                summary = summary?.summaryText,
                summaryFresh = summary != null && !summary.summarizedAt.isBefore(lastFactAt(summary, userFacts)),
            )
        }
    }

    private fun lastFactAt(summary: StoredUserKnowledgeSummary, facts: List<UserFact>): OffsetDateTime {
        val factsLastConfirmedAt = facts.maxOfOrNull { it.lastConfirmedAt } ?: return summary.lastFactAt
        return maxOf(summary.lastFactAt, factsLastConfirmedAt)
    }
}
