package com.simarel.vkbot.processor.domain.decision

import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DecisionAnalysisTest {

    @Test
    fun `DecisionAnalysis rejects a spam probability outside the unit interval`() {
        assertThrows(DecisionAnalysisSpamProbabilityValidationException::class.java) {
            FakeDecisionProvider.createAnalysis(spamProbability = 1.5)
        }
    }

    @Test
    fun `DecisionAnalysis rejects a jailbreak probability outside the unit interval`() {
        val exception = assertThrows(DecisionAnalysisJailbreakProbabilityValidationException::class.java) {
            FakeDecisionProvider.createAnalysis(jailbreakProbability = 1.5)
        }

        assertEquals("jailbreakProbability must be in [0, 1], but was 1.5", exception.message)
    }

    @Test
    fun `DecisionAnalysis rejects a class confidence outside the unit interval`() {
        assertThrows(DecisionAnalysisClassConfidenceValidationException::class.java) {
            FakeDecisionProvider.createAnalysis(classConfidence = -0.1)
        }
    }

    @Test
    fun `DecisionAnalysis rejects a class probability outside the unit interval`() {
        assertThrows(DecisionAnalysisClassProbabilitiesValidationException::class.java) {
            FakeDecisionProvider.createAnalysis(
                classProbabilities = mapOf(MessageClass.SPAM to 1.5),
            )
        }
    }
}
