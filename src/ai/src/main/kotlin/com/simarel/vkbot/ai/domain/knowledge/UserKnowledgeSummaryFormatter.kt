package com.simarel.vkbot.ai.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus

object UserKnowledgeSummaryFormatter {

    fun format(facts: List<UserFact>): String = CATEGORY_ORDER
        .mapNotNull { category ->
            val lines = facts
                .asSequence()
                .filter { fact -> fact.category == category && !fact.stale }
                .map { fact -> line(fact) }
                .toList()
            if (lines.isEmpty()) null else (listOf(HEADERS.getValue(category)) + lines).joinToString("\n")
        }
        .joinToString("\n")

    private fun line(fact: UserFact): String = when {
        fact.conflict -> CONFLICT_PREFIX + fact.value
        fact.status == UserFactStatus.HISTORICAL -> HISTORICAL_PREFIX + fact.value
        fact.status == UserFactStatus.CANDIDATE -> CANDIDATE_PREFIX + fact.value
        else -> PLAIN_PREFIX + fact.value
    }

    private val CATEGORY_ORDER: List<UserFactCategory> = listOf(
        UserFactCategory.INTEREST,
        UserFactCategory.HABIT,
        UserFactCategory.KNOWLEDGE,
        UserFactCategory.PROFILE_ATTRIBUTE,
        UserFactCategory.SOCIAL,
        UserFactCategory.EVENT,
    )

    private val HEADERS: Map<UserFactCategory, String> = mapOf(
        UserFactCategory.INTEREST to "Интересы и увлечения:",
        UserFactCategory.HABIT to "Привычки и рутина:",
        UserFactCategory.KNOWLEDGE to "Знания и уровень:",
        UserFactCategory.PROFILE_ATTRIBUTE to "Атрибуты профиля:",
        UserFactCategory.SOCIAL to "Социальные связи:",
        UserFactCategory.EVENT to "События и планы:",
    )

    private const val PLAIN_PREFIX = "- "
    private const val CONFLICT_PREFIX = "- возможно, противоречивые данные: "
    private const val HISTORICAL_PREFIX = "- ранее: "
    private const val CANDIDATE_PREFIX = "- возможно: "
}
