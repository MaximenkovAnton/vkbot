package com.simarel.vkbot.share.domain.model.knowledge

data class UserKnowledge(
    val facts: List<UserFact>,
    val summary: String?,
    val summaryFresh: Boolean,
)
