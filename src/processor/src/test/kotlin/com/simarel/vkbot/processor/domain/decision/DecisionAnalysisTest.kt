package com.simarel.vkbot.processor.domain.decision

import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DecisionAnalysisTest {

    @Test
    fun `DecisionAnalysis rejects a spam probability outside the unit interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            FakeDecisionProvider.createAnalysis(spamProbability = 1.5)
        }
    }

    @Test
    fun `DecisionAnalysis rejects a class confidence outside the unit interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            FakeDecisionProvider.createAnalysis(classConfidence = -0.1)
        }
    }

    @Test
    fun `DecisionAnalysis rejects a class probability outside the unit interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            FakeDecisionProvider.createAnalysis(
                classProbabilities = mapOf(MessageClass.SPAM to 1.5),
            )
        }
    }
}
