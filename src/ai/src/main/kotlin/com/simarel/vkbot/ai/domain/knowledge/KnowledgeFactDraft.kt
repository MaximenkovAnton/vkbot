package com.simarel.vkbot.ai.domain.knowledge

data class KnowledgeFactDraft(
    val subjectId: Long? = null,
    val subjectName: String? = null,
    val category: String? = null,
    val key: String? = null,
    val value: String? = null,
    val valuePayload: Map<String, Any?>? = null,
    val confidence: Double? = null,
    val sourceKind: String? = null,
    val sourceConversationMessageId: Long? = null,
)
