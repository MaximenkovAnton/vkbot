package com.simarel.vkbot.share.domain.model.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KnowledgeEnumLiteralsTest {

    @Test
    fun `keepsAgreedEnumLiterals`() {
        assertEquals(
            listOf("INTEREST", "HABIT", "KNOWLEDGE", "PROFILE_ATTRIBUTE", "SOCIAL", "EVENT"),
            UserFactCategory.values().map { it.name },
        )
        assertEquals(
            listOf("CANDIDATE", "CONFIRMED", "HISTORICAL"),
            UserFactStatus.values().map { it.name },
        )
        assertEquals(
            listOf("SELF_STATEMENT", "OTHER_STATEMENT", "CONGRATULATION", "INFERENCE"),
            KnowledgeSourceKind.values().map { it.name },
        )
    }
}
