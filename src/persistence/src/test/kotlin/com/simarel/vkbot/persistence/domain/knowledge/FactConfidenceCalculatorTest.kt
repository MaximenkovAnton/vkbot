package com.simarel.vkbot.persistence.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FactConfidenceCalculatorTest {

    @Test
    fun `clampsBySourceKindAndConfirmsAtThreshold`() {
        val clamps = listOf(
            Triple(KnowledgeSourceKind.SELF_STATEMENT, 0.9, 0.800),
            Triple(KnowledgeSourceKind.OTHER_STATEMENT, 1.0, 0.600),
            Triple(KnowledgeSourceKind.CONGRATULATION, 1.0, 0.600),
            Triple(KnowledgeSourceKind.INFERENCE, 1.0, 0.400),
            Triple(KnowledgeSourceKind.INFERENCE, 0.9, 0.400),
        )
        clamps.forEach { (sourceKind, confidence, expected) ->
            assertEquals(expected, FactConfidenceCalculator.clamp(sourceKind, confidence), DELTA)
        }

        val aggregates = listOf(
            Triple(0.9, 1, 0.900),
            Triple(0.85, 3, 1.000),
            Triple(0.7, 6, 1.000),
            Triple(0.5, 1, 0.500),
        )
        aggregates.forEach { (maxEvidenceConfidence, evidenceCount, expected) ->
            assertEquals(expected, FactConfidenceCalculator.aggregate(maxEvidenceConfidence, evidenceCount), DELTA)
        }

        assertEquals(
            UserFactStatus.CONFIRMED,
            FactConfidenceCalculator.status(confidence = 0.8, threshold = 0.8),
        )
        assertEquals(
            UserFactStatus.CANDIDATE,
            FactConfidenceCalculator.status(confidence = 0.79, threshold = 0.8),
        )
        assertEquals(
            UserFactStatus.CANDIDATE,
            FactConfidenceCalculator.status(confidence = 0.6, threshold = 0.8),
        )
    }

    private companion object {
        private const val DELTA = 0.0000001
    }
}
