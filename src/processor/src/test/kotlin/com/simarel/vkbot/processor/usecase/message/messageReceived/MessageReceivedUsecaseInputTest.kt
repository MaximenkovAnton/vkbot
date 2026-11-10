package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.domain.decision.MessageIntent
import com.simarel.vkbot.processor.domain.exception.DecisionModelException
import com.simarel.vkbot.processor.domain.exception.DecisionModelResponseException
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.logging.Level
import java.util.stream.Stream

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
    fun `execute does not check spam outside a group chat or for a non-human when no answer is required`(
        human: Boolean,
        groupChat: Boolean,
    ) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.99)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = false)
        val fromId = if (human) FakeVoProvider.createHumanFromId() else FakeVoProvider.createGroupFromId()
        val peerId = FakeVoProvider.createPeerId(if (groupChat) GROUP_CHAT_PEER_ID else PRIVATE_CHAT_PEER_ID)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(fromId = fromId, peerId = peerId),
            ),
        )

        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(0, publishCommand.executeCalls.size)
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

    @ParameterizedTest
    @CsvSource("true, false", "false, true", "false, false")
    fun `execute resolves only the intent outside a group chat or for a non-human`(
        human: Boolean,
        groupChat: Boolean,
    ) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.99, jailbreakProbability = 0.99)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val fromId = if (human) FakeVoProvider.createHumanFromId() else FakeVoProvider.createGroupFromId()
        val peerId = FakeVoProvider.createPeerId(if (groupChat) GROUP_CHAT_PEER_ID else PRIVATE_CHAT_PEER_ID)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(fromId = fromId, peerId = peerId),
            ),
        )

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @Test
    fun `execute calls the model once for intent when the spam kill-switch is off`() {
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

        assertEquals(1, analyzePort.executeCalls.size)
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

            assertEquals(2, analyzePort.executeCalls.size)
            assertEquals(1, publishCommand.executeCalls.size)
            assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
            val expectedWarning =
                "Decision model failed for peerId=${message.peerId.value}: ${failure.javaClass.simpleName}"
            assertEquals(listOf(expectedWarning, expectedWarning), capture.messages(Level.WARNING))
            assertTrue(capture.records.filter { it.level == Level.WARNING }.all { it.thrown == null })
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

    @Test
    fun `execute answers an exact help command without calling the decision model`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply { response = analysis(0.0, MessageIntent.NEWS) }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val request = FakeMessageNewInputPortProvider.createRequest(
            incomingMessage(
                peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID),
                messageText = FakeVoProvider.createMessageText("/help"),
            ),
        )

        val result = usecase.execute(request)

        assertEquals("ok", result.value)
        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value
        assertTrue(payload is ResponseMessage)
        payload as ResponseMessage
        assertEquals(helpText(), payload.messageText)
        assertSame(request.message, payload.responseTo)
    }

    @Test
    fun `execute answers the help intent from the spam stage analysis`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.HELP)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value
        assertTrue(payload is ResponseMessage)
        payload as ResponseMessage
        assertEquals(helpText(), payload.messageText)
        assertSame(request.message, payload.responseTo)
    }

    @Test
    fun `execute answers the help intent when no analysis exists`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.HELP)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val message = incomingMessage(peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID))
        val request = FakeMessageNewInputPortProvider.createRequest(message)

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value
        assertTrue(payload is ResponseMessage)
        payload as ResponseMessage
        assertEquals(helpText(), payload.messageText)
        assertSame(request.message, payload.responseTo)
    }

    @Test
    fun `execute prefers an exact help command over the model intent`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.NEWS)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(messageText = FakeVoProvider.createMessageText("/help")),
            ),
        )

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.ANSWER_MESSAGE, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.NEWS_DIGEST_REQUESTED })
    }

    @Test
    fun `execute routes a help command with arguments to general without calling the model`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort()
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(
                    peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID),
                    messageText = FakeVoProvider.createMessageText("/help что нового"),
                ),
            ),
        )

        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @Test
    fun `execute routes general to exactly one MESSAGE_REQUIRE_ANSWER`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.GENERAL)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val request = FakeMessageNewInputPortProvider.createRequest(
            incomingMessage(peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID)),
        )

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, published.event)
        assertSame(request.message, published.payload.value)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
        assertTrue(publishCommand.executeCalls.none { it.event == Event.NEWS_DIGEST_REQUESTED })
    }

    @Test
    fun `execute routes profile to exactly one PROFILE_REQUESTED`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.PROFILE)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val request = FakeMessageNewInputPortProvider.createRequest(
            incomingMessage(peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID)),
        )

        val result = usecase.execute(request)

        assertEquals("ok", result.value)
        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.PROFILE_REQUESTED, published.event)
        assertSame(request.message, published.payload.value)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.MESSAGE_REQUIRE_ANSWER })
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
        assertTrue(publishCommand.executeCalls.none { it.event == Event.NEWS_DIGEST_REQUESTED })
    }

    @ParameterizedTest
    @CsvSource("private", "spam")
    fun `execute routes news to exactly one NEWS_DIGEST_REQUESTED`(analysisSource: String) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.NEWS)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val peerId = if (analysisSource == "spam") {
            FakeVoProvider.createPeerId(GROUP_CHAT_PEER_ID)
        } else {
            FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID)
        }
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage(peerId = peerId))

        val result = usecase.execute(request)

        assertEquals("ok", result.value)
        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.NEWS_DIGEST_REQUESTED, published.event)
        assertSame(request.message, published.payload.value)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.MESSAGE_REQUIRE_ANSWER })
        assertTrue(publishCommand.executeCalls.none { it.event == Event.ANSWER_MESSAGE })
    }

    @ParameterizedTest
    @CsvSource(
        "HELP, ANSWER_MESSAGE",
        "NEWS, NEWS_DIGEST_REQUESTED",
        "GENERAL, MESSAGE_REQUIRE_ANSWER",
    )
    fun `execute reuses the spam stage analysis for intent routing`(
        intent: MessageIntent,
        expectedEvent: Event,
    ) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, intent)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val request = FakeMessageNewInputPortProvider.createRequest(incomingMessage())

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(expectedEvent, published.event)
        if (intent == MessageIntent.HELP) {
            val payload = published.payload.value
            assertTrue(payload is ResponseMessage)
            payload as ResponseMessage
            assertEquals(helpText(), payload.messageText)
        } else {
            assertSame(request.message, published.payload.value)
        }
    }

    @Test
    fun `execute calls the model once for intent in a private chat`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, MessageIntent.GENERAL)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID)),
            ),
        )

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
    }

    @Test
    fun `execute fails open to general when the intent decision call fails`() {
        SystemLoggerCapture(LOGGER_NAME).use { capture ->
            val publishCommand = FakePublishEventCommand()
            val failure = DecisionModelException("Decision model call failed", RuntimeException("transport"))
            val analyzePort = FakeAnalyzeMessageOutputPort().apply { this.failure = failure }
            val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
            val message = incomingMessage(peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID))

            assertDoesNotThrow {
                usecase.execute(FakeMessageNewInputPortProvider.createRequest(message))
            }

            assertEquals(1, analyzePort.executeCalls.size)
            assertEquals(1, publishCommand.executeCalls.size)
            assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishCommand.executeCalls.single().event)
            assertEquals(
                listOf("Decision model failed for peerId=${message.peerId.value}: ${failure.javaClass.simpleName}"),
                capture.messages(Level.WARNING),
            )
        }
    }

    @Test
    fun `execute rethrows InterruptedException from the intent stage and keeps the interrupt flag`() {
        SystemLoggerCapture(LOGGER_NAME).use { capture ->
            val publishCommand = FakePublishEventCommand()
            val analyzePort = FakeAnalyzeMessageOutputPort().apply { failure = InterruptedException() }
            val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
            val message = incomingMessage(peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID))

            try {
                assertThrows(InterruptedException::class.java) {
                    usecase.execute(FakeMessageNewInputPortProvider.createRequest(message))
                }
                assertTrue(Thread.currentThread().isInterrupted)
                assertTrue(capture.messages(Level.WARNING).isEmpty())
                assertTrue(publishCommand.executeCalls.isEmpty())
            } finally {
                Thread.interrupted()
            }
        }
    }

    @Test
    fun `execute does not route intents when no answer is required`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort()
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = false)

        usecase.execute(
            FakeMessageNewInputPortProvider.createRequest(
                incomingMessage(
                    peerId = FakeVoProvider.createPeerId(PRIVATE_CHAT_PEER_ID),
                    messageText = FakeVoProvider.createMessageText("/help"),
                ),
            ),
        )

        assertEquals(0, analyzePort.executeCalls.size)
        assertEquals(0, publishCommand.executeCalls.size)
    }

    @ParameterizedTest
    @CsvSource("HELP", "NEWS", "PROFILE")
    fun `execute does not route a ready analysis when no answer is required`(intent: MessageIntent) {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.0, intent)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = false)

        val result = usecase.execute(FakeMessageNewInputPortProvider.createRequest(incomingMessage()))

        assertEquals("ok", result.value)
        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(0, publishCommand.executeCalls.size)
    }

    @Test
    fun `execute stops before intent routing when the message is spam`() {
        val publishCommand = FakePublishEventCommand()
        val analyzePort = FakeAnalyzeMessageOutputPort().apply {
            response = analysis(0.95, MessageIntent.HELP)
        }
        val usecase = createUsecase(publishCommand, analyzePort, requiresAnswer = true)
        val message = incomingMessage(messageText = FakeVoProvider.createMessageText("/help"))
        val request = FakeMessageNewInputPortProvider.createRequest(message)

        usecase.execute(request)

        assertEquals(1, analyzePort.executeCalls.size)
        assertEquals(1, publishCommand.executeCalls.size)
        val published = publishCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val payload = published.payload.value
        assertTrue(payload is ResponseMessage)
        payload as ResponseMessage
        assertEquals("Сработал автодетект спама. Вероятность спама: 95%.", payload.messageText.value)
        assertSame(request.message, payload.responseTo)
        assertTrue(publishCommand.executeCalls.none { it.event == Event.MESSAGE_REQUIRE_ANSWER })
        assertTrue(publishCommand.executeCalls.none { it.event == Event.NEWS_DIGEST_REQUESTED })
    }

    private companion object {
        @JvmStatic
        fun decisionFailures(): Stream<RuntimeException> = Stream.of(
            DecisionModelException("Decision model call failed", RuntimeException("transport")),
            DecisionModelResponseException("Decision model response is invalid: unexpected class probabilities"),
            RuntimeException("decision transport failed"),
        )
    }
}
