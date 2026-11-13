package com.simarel.vkbot.persistence.domain.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import java.math.BigDecimal
import java.math.RoundingMode

object FactConfidenceCalculator {

    const val EVIDENCE_BONUS = 0.1
    const val EVIDENCE_CAP = 5
    const val MAX_CONFIDENCE = 1.0
    const val SCALE = 3

    private const val SELF_WEIGHT = 0.8
    private const val OTHER_WEIGHT = 0.6
    private const val INFERENCE_WEIGHT = 0.4

    fun clamp(sourceKind: KnowledgeSourceKind, confidence: Double): Double {
        val weight = when (sourceKind) {
            KnowledgeSourceKind.SELF_STATEMENT -> SELF_WEIGHT
            KnowledgeSourceKind.OTHER_STATEMENT -> OTHER_WEIGHT
            KnowledgeSourceKind.CONGRATULATION -> OTHER_WEIGHT
            KnowledgeSourceKind.INFERENCE -> INFERENCE_WEIGHT
        }
        return round(confidence.coerceIn(0.0, weight))
    }

    fun aggregate(maxEvidenceConfidence: Double, evidenceCount: Int): Double {
        val cappedCount = evidenceCount.coerceAtMost(EVIDENCE_CAP)
        val aggregate = maxEvidenceConfidence + EVIDENCE_BONUS * (cappedCount - 1)
        return round(aggregate.coerceAtMost(MAX_CONFIDENCE))
    }

    fun status(confidence: Double, threshold: Double): UserFactStatus {
        val confirmed = confidence >= threshold
        return if (confirmed) UserFactStatus.CONFIRMED else UserFactStatus.CANDIDATE
    }

    private fun round(value: Double): Double {
        val rounded = BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP)
        return rounded.toDouble()
    }
}
