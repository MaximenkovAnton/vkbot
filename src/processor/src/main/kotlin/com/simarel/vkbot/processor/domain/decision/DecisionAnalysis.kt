package com.simarel.vkbot.processor.domain.decision

import com.simarel.vkbot.share.domain.exception.ValidationException

private val PROBABILITY_RANGE = 0.0..1.0

data class DecisionAnalysis(
    val messageClass: MessageClass,
    val classConfidence: Double?,
    val classProbabilities: Map<MessageClass, Double>,
    val spamProbability: Double,
) {
    init {
        if (spamProbability !in PROBABILITY_RANGE) {
            throw DecisionAnalysisSpamProbabilityValidationException(spamProbability)
        }
        if (classConfidence != null && classConfidence !in PROBABILITY_RANGE) {
            throw DecisionAnalysisClassConfidenceValidationException(classConfidence)
        }
        if (!classProbabilities.values.all { it in PROBABILITY_RANGE }) {
            throw DecisionAnalysisClassProbabilitiesValidationException(classProbabilities)
        }
    }
}

open class DecisionAnalysisValidationException(message: String) : ValidationException(message)

class DecisionAnalysisSpamProbabilityValidationException(
    spamProbability: Double,
) : DecisionAnalysisValidationException(
    "spamProbability must be in [0, 1], but was $spamProbability",
)

class DecisionAnalysisClassConfidenceValidationException(
    classConfidence: Double,
) : DecisionAnalysisValidationException(
    "classConfidence must be in [0, 1], but was $classConfidence",
)

class DecisionAnalysisClassProbabilitiesValidationException(
    classProbabilities: Map<MessageClass, Double>,
) : DecisionAnalysisValidationException(
    "classProbabilities must be in [0, 1], but were $classProbabilities",
)
