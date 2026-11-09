package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.command.isSpam.IsSpamCommandImpl
import com.simarel.vkbot.processor.domain.decision.MessageIntent
import com.simarel.vkbot.processor.domain.exception.DecisionModelException
import com.simarel.vkbot.processor.domain.exception.DecisionModelResponseException
import com.simarel.vkbot.processor.test.SystemLoggerCapture
import com.simarel.vkbot.processor.testfixtures.command.FakeIsRequireAnswerCommand
import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import com.simarel.vkbot.processor.testfixtures.port.input.FakeMessageNewInputPortProvider
import com.simarel.vkbot.processor.testfixtures.port.output.decision.FakeAnalyzeMessageOutputPort
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.logging.Level
import java.util.stream.Stream

private const val GROUP_CHAT_PEER_ID = 2_000_000_001L
private const val PRIVATE_CHAT_PEER_ID = 123_456_789L
private const val LOGGER_NAME =
    "com.simarel.vkbot.processor.usecase.message.messageReceived.MessageReceivedUsecaseInput"

class MessageReceivedUsecaseInputTest {

    @Test
    fun `execute publishes MESSAGE_REQUIRE_ANSWER event when requireAnswer is true`() {
        val publishCommand = FakePublishEventCommand()
        val usecase = createUsecase(publishCommand, requiresAnswer = true)
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        val result = usecase.execute(request)

        assertEquals("ok", result.value)
        assertEquals(1, publishCommand.executeCalls.size)
        val publishedEvent = publishCommand.executeCalls.first()
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishedEvent.event)
        assertEquals(request.message, publishedEvent.payload.value)
    }

    @Test
    fun `execute does not publish event when requireAnswer is false`() {
        val publishCommand = FakePublishEventCommand()
        val usecase = createUsecase(publishCommand, requiresAnswer = false)

        val result = usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals("ok", result.value)
        assertEquals(0, publishCommand.executeCalls.size)
    }

    @ParameterizedTest
    @CsvSource("0.9, 90", "0.908, 91", "0.95, 95", "0.999, 100")
    fun `execute publishes exactly one spam answer for a human in a group chat`(
        spamProbability: Double,
        percent: Long,
    ) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(spamProbability)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = false)
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        val result = usecase.execute(request)

        assertEquals("ok", result.value)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value
        assertTrue(payload is ResponseMessage)
        payload as ResponseMessage
        assertEquals("Сработал автодетект спама. Вероятность спама: $percent%.", payload.messageText.value)
        assertSame(request.message, payload.responseTo)
        assertEquals(1, analyzePort.executeCalls.size)
    }

    @ParameterizedTest
    @CsvSource("true, false", "false, true", "false, false")
    fun `execute does not check spam outside a group chat or for a non-human`(
        human: Boolean,
        groupChat: Boolean,
    ) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.99)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val fromId = if (human) FakeVoProvider.createHumanFromId() else FakeVoProvider.createGroupFromId()
        val peerId = FakeVoProvider.createPeerId(if (groupChat) GROUP_CHAT_PEER_ID else PRIVATE_CHAT_PEER_ID)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(fromId = fromId, peerId = peerId),
            ),
        )

        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @Test
    fun `execute does not answer spam below the threshold`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.899)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = true,
            spamThreshold = 0.9,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
    }

    @Test
    fun `execute skips the decision call when the spam kill-switch is off`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.99)
        }
        val usecase = createUsecase(
            publishCommand,
            analyzePort,
            requiresAnswer = true,
            spamReplyEnabled = false,
        )

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
    }

    @ParameterizedTest
    @MethodSource("decisionFailures")
    fun `execute fails open on a decision model failure`(failure: RuntimeException) {
        SystemLoggerCapture(LOGGER_NAME).use { capture ->
            val publishCommand = FakePublishEventCommand()
            val analyzePort = FakeAnalyzeMessageOutputPort().apply { this.failure = failure }
            val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
            val message = incomingMessage()
            val request = FakeMessageNewInputPortProvider.createRequest(message)

            assertDoesNotThrow { usecase.execute(request) }

            assertEquals(1, publishCommand.executeCalls.size)
            assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
            val warnings = capture.messages(Level.WARNING)
            assertEquals(1, warnings.size)
            assertEquals(
                "Decision model failed for peerId=${message.peerId.value}: ${failure.javaClass.simpleName}",
                warnings.single(),
            )
            assertNull(capture.records.single { it.level == Level.WARNING }.thrown)
            assertTrue(capture.records.none { it.message?.contains(message.messageText.value) == true })
        }
    }

    @Test
    fun `execute fails open with no events when no answer is required`() {
        SystemLoggerCapture(LOGGER_NAME).use { capture ->
            val publishCommand = FakePublishEventCommand()
            val analyzePort = FakeAnalyzeMessageOutputPort().apply {
                failure = DecisionModelException("Decision model call failed", RuntimeException("transport"))
            }
            val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = false)

            assertDoesNotThrow {
                usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))
            }

            assertEquals(0, publishCommand.executeCalls.size)
            assertEquals(1, capture.messages(Level.WARNING).size)
        }
    }

    @Test
    fun `execute rethrows InterruptedException and keeps the interrupt flag`() {
        SystemLoggerCapture(LOGGER_NAME).use { capture ->
            val publishCommand = FakePublishEventCommand()
            val analyzePort = FakeAnalyzeMessageOutputPort().apply { failure = InterruptedException() }
            val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)

            try {
                assertThrows(InterruptedException::class.java) {
                    usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))
                }
                assertTrue(Thread.currentThread().isInterrupted)
                assertTrue(capture.messages(Level.WARNING).isEmpty())
                assertTrue(publishCommand.executeCalls.isEmpty())
            } finally {
                Thread.interrupted()
            }
        }
    }

    @ParameterizedTest
    @CsvSource("HELP", "NEWS")
    fun `execute does not route intents in this board task`(intent: MessageIntent) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, intent)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)

        usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    private fun createUsecase(
        publishCommand: FakePublishEventCommand = FakePublishEventCommand(),
        analyzePort: FakeAnalyzeMessageOutputPort = FakeAnalyzeMessageOutputPort(),
        requiresAnswer: Boolean = true,
        spamThreshold: Double = 0.9,
        spamReplyEnabled: Boolean = true,
    ): MessageReceivedUsecaseInput {
        val isRequireAnswerCommand = FakeIsRequireAnswerCommand()
        isRequireAnswerCommand.shouldRequireAnswer = requiresAnswer
        return MessageReceivedUsecaseInput(
            publishCommand,
            isRequireAnswerCommand,
            analyzePort,
            IsSpamCommandImpl(spamThreshold),
            spamReplyEnabled,
        )
    }

    private fun incomingMessage(
        fromId: FromId = FakeVoProvider.createHumanFromId(),
        peerId: PeerId = FakeVoProvider.createPeerId(GROUP_CHAT_PEER_ID),
        messageText: MessageText = FakeVoProvider.createMessageText("Что нового в чате?"),
    ): Message = FakeMessageProvider.createMessage(
        fromId = fromId,
        peerId = peerId,
        messageText = messageText,
        forwardedMessages = emptyList(),
    )

    private fun analysis(
        spamProbability: Double,
        intent: MessageIntent = MessageIntent.GENERAL,
    ) = FakeDecisionProvider.createAnalyzeMessageResponse(
        FakeDecisionProvider.createAnalysis(spamProbability = spamProbability, intent = intent),
    )

    private companion object {
        @JvmStatic
        fun decisionFailures(): Stream<RuntimeException> = Stream.of(
            DecisionModelException("Decision model call failed", RuntimeException("transport")),
            DecisionModelResponseException("Decision model response is invalid: unexpected class probabilities"),
            RuntimeException("decision transport failed"),
        )
    }
}
