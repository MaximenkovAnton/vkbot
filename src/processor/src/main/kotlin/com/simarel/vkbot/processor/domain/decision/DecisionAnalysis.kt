package com.simarel.vkbot.processor.domain.decision

private val PROBABILITY_RANGE = 0.0..1.0

data class DecisionAnalysis(
    val messageClass: MessageClass,
    val classConfidence: Double?,
    val classProbabilities: Map<MessageClass, Double>,
    val spamProbability: Double,
) {
    init {
        require(spamProbability in PROBABILITY_RANGE) {
            "spamProbability must be in [0, 1], but was $spamProbability"
        }
        require(classConfidence == null || classConfidence in PROBABILITY_RANGE) {
            "classConfidence must be in [0, 1], but was $classConfidence"
        }
        require(classProbabilities.values.all { it in PROBABILITY_RANGE }) {
            "classProbabilities must be in [0, 1], but were $classProbabilities"
        }
    }
}
