package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.testfixtures.port.input.FakeMessageNewInputPortProvider
import com.simarel.vkbot.processor.testfixtures.port.output.decision.FakeAnalyzeMessageOutputPort
import com.simarel.vkbot.processor.testfixtures.port.output.knowledge.FakeHasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageReceivedUsecaseInputKnowledgeTest {

    @Test
    fun `triggersBeforeRequiresAnswerEarlyReturn`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }
        val usecase = createUsecase(
            publishCommand = publish,
            requiresAnswer = false,
            hasPendingKnowledgeBatch = hasPending,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, hasPending.executeCalls.size)
        assertEquals(1, publish.executeCalls.size)
        assertEquals(Event.USER_KNOWLEDGE_REQUESTED, publish.executeCalls.single().event)
    }

    @Test
    fun `triggersBeforeSpamEarlyReturn`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }
        val analyzePort = FakeAnalyzeMessageOutputPort().apply { response = analysis(spamProbability = 0.95) }
        val usecase = createUsecase(
            publishCommand = publish,
            analyzePort = analyzePort,
            hasPendingKnowledgeBatch = hasPending,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(
            listOf(Event.USER_KNOWLEDGE_REQUESTED, Event.ANSWER_MESSAGE),
            publish.executeCalls.map { it.event },
        )
    }

    @Test
    fun `keepsRoutingWhenTriggerFails`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { failure = RuntimeException("boom") }
        val usecase = createUsecase(publishCommand = publish, hasPendingKnowledgeBatch = hasPending)

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, hasPending.executeCalls.size)
        assertEquals(listOf(Event.MESSAGE_REQUIRE_ANSWER), publish.executeCalls.map { it.event })
    }
}
