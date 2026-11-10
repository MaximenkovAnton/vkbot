package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.domain.exception.DecisionModelException
import com.simarel.vkbot.processor.test.SystemLoggerCapture
import com.simarel.vkbot.processor.testfixtures.port.input.FakeMessageNewInputPortProvider
import com.simarel.vkbot.processor.testfixtures.port.output.decision.FakeAnalyzeMessageOutputPort
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.logging.Level

class MessageReceivedUsecaseInputJailbreakTest {

    @Test
    fun `execute does not publish a spam answer when the spam kill-switch is off`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.95, jailbreakProbability = 0.1)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = false,
            spamReplyEnabled = false,
            jailbreakReplyEnabled = true,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, analyzePort.executeCalls.size)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @Test
    fun `execute calls the decision model once and publishes the jailbreak answer when the spam kill-switch is off`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.99, jailbreakProbability = 0.95)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = true,
            spamReplyEnabled = false,
            jailbreakReplyEnabled = true,
        )
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value as ResponseMessage
        assertEquals("${JAILBREAK_NOTICE}95%.", payload.messageText.value)
        assertSame(request.message, payload.responseTo)
    }

    @ParameterizedTest
    @CsvSource("0.9, 90", "0.908, 91", "0.95, 95", "0.999, 100")
    fun `execute publishes exactly one jailbreak answer for a human in a group chat`(
        jailbreakProbability: Double,
        percent: Long,
    ) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.0, jailbreakProbability = jailbreakProbability)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = false,
            jailbreakReplyEnabled = true,
        )
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value as ResponseMessage
        assertEquals("$JAILBREAK_NOTICE$percent%.", payload.messageText.value)
        assertSame(request.message, payload.responseTo)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.MESSAGE_REQUIRE_ANSWER })
    }

    @Test
    fun `execute does not answer jailbreak below the threshold`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.0, jailbreakProbability = 0.899)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
    }

    @Test
    fun `execute prefers the spam answer when both thresholds are exceeded`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.95, jailbreakProbability = 0.95)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = false,
            spamReplyEnabled = true,
            jailbreakReplyEnabled = true,
        )
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value as ResponseMessage
        assertEquals("Сработал автодетект спама. Вероятность спама: 95%.", payload.messageText.value)
        assertSame(request.message, payload.responseTo)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.MESSAGE_REQUIRE_ANSWER })
    }

    @Test
    fun `execute does not publish a jailbreak answer when the jailbreak kill-switch is off`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.95, jailbreakProbability = 0.99)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = false,
            spamReplyEnabled = true,
            jailbreakReplyEnabled = false,
        )
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val payload = publishCommand.executeCalls.single().payload.value as ResponseMessage
        assertEquals("Сработал автодетект спама. Вероятность спама: 95%.", payload.messageText.value)
        assertSame(request.message, payload.responseTo)
    }

    @Test
    fun `execute requires an answer when the jailbreak kill-switch is off and spam is below the threshold`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.0, jailbreakProbability = 0.99)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = true,
            spamReplyEnabled = true,
            jailbreakReplyEnabled = false,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @Test
    fun `execute requires an answer when the spam kill-switch is off and jailbreak is below the threshold`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.0, jailbreakProbability = 0.0)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = true,
            spamReplyEnabled = false,
            jailbreakReplyEnabled = true,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @Test
    fun `execute does not run the decision stage when both kill-switches are off`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability = 0.99, jailbreakProbability = 0.99)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = true,
            spamReplyEnabled = false,
            jailbreakReplyEnabled = false,
        )

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(messageText = FakeVoProvider.createMessageText("/help")),
            ),
        )

        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.ANSWER_MESSAGE, publishCommand.executeCalls.single().event)
    }

    @Test
    fun `execute fails open on a decision model failure when only the jailbreak branch is on`() {
        SystemLoggerCapture(LOGGER_NAME).use { capture ->
            val publishCommand = FakePublishEventCommand()
            val analyzePort = FakeAnalyzeMessageOutputPort().apply {
                failure = DecisionModelException("Decision model call failed", RuntimeException("transport"))
            }
            val usecase = createUsecase(
                publishCommand,
                analyzePort,
                requiresAnswer = true,
                spamReplyEnabled = false,
                jailbreakReplyEnabled = true,
            )

            assertDoesNotThrow {
                usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))
            }

            assertEquals(2, analyzePort.executeCalls.size)
            assertEquals(1, publishCommand.executeCalls.size)
            assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
            assertEquals(2, capture.messages(Level.WARNING).size)
        }
    }
}
