package com.simarel.vkbot.processor.testfixtures.decision

import com.simarel.vkbot.processor.domain.decision.DecisionAnalysis
import com.simarel.vkbot.processor.domain.decision.MessageClass
import com.simarel.vkbot.processor.domain.decision.MessageIntent
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageRequest
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeDecisionProvider {

    fun createAnalyzeMessageRequest(
        messageText: MessageText? = null,
    ): AnalyzeMessageRequest = AnalyzeMessageRequest(
        messageText = messageText ?: FakeVoProvider.createMessageText(),
    )

    fun createAnalyzeMessageResponse(
        analysis: DecisionAnalysis? = null,
    ): AnalyzeMessageResponse = AnalyzeMessageResponse(
        analysis = analysis ?: createAnalysis(),
    )

    fun createAnalysis(
        messageClass: MessageClass = MessageClass.SPAM,
        classConfidence: Double? = 0.959,
        classProbabilities: Map<MessageClass, Double> = mapOf(
            MessageClass.SPAM to 0.959,
            MessageClass.COMMAND to 0.009,
            MessageClass.HAM to 0.032,
        ),
        spamProbability: Double = 0.908,
        jailbreakProbability: Double = 0.0,
        intent: MessageIntent = MessageIntent.GENERAL,
    ): DecisionAnalysis = DecisionAnalysis(
        messageClass = messageClass,
        classConfidence = classConfidence,
        classProbabilities = classProbabilities,
        spamProbability = spamProbability,
        jailbreakProbability = jailbreakProbability,
        intent = intent,
    )
}
