package com.simarel.vkbot.persistence.domain.knowledge

data class NormalizedFact(
    val factKey: String,
    val value: String,
    val valuePayload: Map<String, Any?>,
)
