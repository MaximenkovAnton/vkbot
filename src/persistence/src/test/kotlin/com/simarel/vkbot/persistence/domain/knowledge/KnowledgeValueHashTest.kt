package com.simarel.vkbot.persistence.domain.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KnowledgeValueHashTest {

    @Test
    fun `frozenCanonicalVectors`() {
        assertEquals(
            "a7be8e1fe282a37cd666e0632b17d933fa13f21addf4798fc0455bc166e2488c",
            KnowledgeValueHash.of("  1990 "),
        )
        assertEquals(
            "3d3499d25843e98491f14e4d472cd19365d2d7260d60dddf076afdcbafce79ea",
            KnowledgeValueHash.of("  ДР:   14.05 "),
        )
        assertEquals(
            "481d4d34b7ef9cecabf9767fe9d49d18ca8f4a81b886f384ba512c2dfdc06d2a",
            KnowledgeValueHash.of("  Футбол   и  хобби "),
        )
        assertEquals(64, KnowledgeValueHash.of("футбол").length)
    }
}
