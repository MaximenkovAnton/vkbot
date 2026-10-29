package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.ai.testfixtures.FakeConversationContextPortProvider
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream

private const val ENABLED_CHATS = "2000000001,2000000002"
private const val PEER_ID = 2_000_000_001L
private const val CURRENT_MESSAGE_ID = 50L
private const val THRESHOLD = 3
private const val BATCH_SIZE = 10

class ProcessSummaryCommandImplTest {

    @Test
    fun `execute skips a chat that is not enabled`() {
        val hasPending = RecordingHasPendingSummaryPort()
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            hasPendingSummaryPort = hasPending,
            generateSummaryCommand = generateSummaryCommand,
            enabledChats = "2000000002",
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertTrue(hasPending.requests.isEmpty())
        assertTrue(generateSummaryCommand.requests.isEmpty())
    }

    @Test
    fun `execute skips when a pending summary already exists`() {
        val hasPending = RecordingHasPendingSummaryPort(hasPending = true)
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            hasPendingSummaryPort = hasPending,
            generateSummaryCommand = generateSummaryCommand,
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertEquals(
            FakePersistencePortProvider.createHasPendingSummaryRequest(peerId = PEER_ID),
            hasPending.requests.single(),
        )
        assertTrue(generateSummaryCommand.requests.isEmpty())
    }

    @Test
    fun `execute skips when the last summary is still pending`() {
        val lastSummary = FakeModelProvider.createSummary(peerId = PEER_ID, status = SummaryStatus.PENDING)
        val findLastSummary = RecordingFindLastSummaryPort(lastSummary)
        val before = RecordingFindMessagesBeforePort()
        val between = RecordingFindMessagesBetweenPort(
            listOf(storedMessage(51), storedMessage(52), storedMessage(53)),
        )
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            findLastSummaryPort = findLastSummary,
            findMessagesBeforePort = before,
            findMessagesBetweenPort = between,
            generateSummaryCommand = generateSummaryCommand,
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertEquals(
            FakePersistencePortProvider.createFindLastSummaryRequest(peerId = PEER_ID),
            findLastSummary.requests.single(),
        )
        assertTrue(before.requests.isEmpty())
        assertTrue(between.requests.isEmpty())
        assertTrue(generateSummaryCommand.requests.isEmpty())
    }

    @Test
    fun `execute skips when the message window is empty`() {
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            findMessagesBeforePort = RecordingFindMessagesBeforePort(),
            generateSummaryCommand = generateSummaryCommand,
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertTrue(generateSummaryCommand.requests.isEmpty())
    }

    @Test
    fun `execute skips a short window when a previous summary exists`() {
        val lastSummary = FakeModelProvider.createSummary(peerId = PEER_ID, status = SummaryStatus.COMPLETED)
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            findLastSummaryPort = RecordingFindLastSummaryPort(lastSummary),
            findMessagesBetweenPort = RecordingFindMessagesBetweenPort(
                listOf(storedMessage(51), storedMessage(52)),
            ),
            generateSummaryCommand = generateSummaryCommand,
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertTrue(generateSummaryCommand.requests.isEmpty())
    }

    @Test
    fun `execute summarises a short window when there is no previous summary`() {
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            findMessagesBeforePort = RecordingFindMessagesBeforePort(
                listOf(storedMessage(51), storedMessage(52)),
            ),
            generateSummaryCommand = generateSummaryCommand,
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertEquals(1, generateSummaryCommand.requests.size)
    }

    @Test
    fun `execute recognises enabled chats listed with surrounding spaces`() {
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            generateSummaryCommand = generateSummaryCommand,
            enabledChats = " 1, 2 ,3 ",
        )

        command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = 2))

        assertEquals(1, generateSummaryCommand.requests.size)
    }

    @Test
    fun `execute requests messages before the current one when there is no previous summary`() {
        val messages = listOf(storedMessage(10), storedMessage(12))
        val findMessagesBefore = RecordingFindMessagesBeforePort(messages)
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            findMessagesBeforePort = findMessagesBefore,
            generateSummaryCommand = generateSummaryCommand,
        )

        val response = command.execute(
            FakeSummaryProvider.createProcessSummaryCommandRequest(
                peerId = PEER_ID,
                conversationMessageId = CURRENT_MESSAGE_ID,
            ),
        )

        assertEquals(
            FakePersistencePortProvider.createFindMessagesBeforeRequest(
                peerId = PEER_ID,
                beforeConversationMessageId = CURRENT_MESSAGE_ID + 1,
                limit = BATCH_SIZE,
            ),
            findMessagesBefore.requests.single(),
        )
        assertEquals(
            FakeSummaryProvider.createGenerateSummaryCommandRequest(
                peerId = PEER_ID,
                firstMessageId = 10,
                lastMessageId = 12,
            ),
            generateSummaryCommand.requests.single(),
        )
        assertEquals(ProcessSummaryCommand.ProcessSummaryResponse, response)
    }

    @ParameterizedTest
    @MethodSource("betweenWindows")
    fun `execute requests messages between the previous summary and the current one`(
        lastMessageId: Long?,
        expectedFirstMessageId: Long,
    ) {
        val lastSummary = FakeModelProvider.createSummary(
            peerId = PEER_ID,
            lastMessageId = lastMessageId,
            status = SummaryStatus.COMPLETED,
        )
        val between = RecordingFindMessagesBetweenPort(
            listOf(storedMessage(51), storedMessage(52), storedMessage(53)),
        )
        val before = RecordingFindMessagesBeforePort()
        val generateSummaryCommand = RecordingGenerateSummaryCommand()
        val command = processSummaryCommand(
            findLastSummaryPort = RecordingFindLastSummaryPort(lastSummary),
            findMessagesBeforePort = before,
            findMessagesBetweenPort = between,
            generateSummaryCommand = generateSummaryCommand,
        )

        command.execute(
            FakeSummaryProvider.createProcessSummaryCommandRequest(
                peerId = PEER_ID,
                conversationMessageId = CURRENT_MESSAGE_ID,
            ),
        )

        assertEquals(
            FakePersistencePortProvider.createFindMessagesBetweenRequest(
                peerId = PEER_ID,
                firstMessageId = expectedFirstMessageId,
                lastMessageId = CURRENT_MESSAGE_ID,
                limit = BATCH_SIZE,
            ),
            between.requests.single(),
        )
        assertTrue(before.requests.isEmpty())
        assertEquals(
            FakeSummaryProvider.createGenerateSummaryCommandRequest(
                peerId = PEER_ID,
                firstMessageId = 51,
                lastMessageId = 53,
            ),
            generateSummaryCommand.requests.single(),
        )
    }

    @Test
    fun `execute swallows a generate summary failure`() {
        val failure = IllegalStateException("llm is down")
        val generateSummaryCommand = RecordingGenerateSummaryCommand(failure)
        val command = processSummaryCommand(generateSummaryCommand = generateSummaryCommand)

        val response = command.execute(FakeSummaryProvider.createProcessSummaryCommandRequest(peerId = PEER_ID))

        assertEquals(1, generateSummaryCommand.requests.size)
        assertEquals(ProcessSummaryCommand.ProcessSummaryResponse, response)
    }

    private fun processSummaryCommand(
        hasPendingSummaryPort: HasStoredPendingSummaryPort = RecordingHasPendingSummaryPort(),
        findLastSummaryPort: FindLastStoredSummaryPort = RecordingFindLastSummaryPort(),
        findMessagesBeforePort: FindStoredMessagesBeforePort = RecordingFindMessagesBeforePort(
            listOf(storedMessage(CURRENT_MESSAGE_ID - 1)),
        ),
        findMessagesBetweenPort: FindStoredMessagesBetweenPort = RecordingFindMessagesBetweenPort(),
        generateSummaryCommand: GenerateSummaryCommand = RecordingGenerateSummaryCommand(),
        enabledChats: String = ENABLED_CHATS,
    ) = ProcessSummaryCommandImpl(
        hasPendingSummaryPort = hasPendingSummaryPort,
        findLastSummaryPort = findLastSummaryPort,
        findMessagesBeforePort = findMessagesBeforePort,
        findMessagesBetweenPort = findMessagesBetweenPort,
        generateSummaryCommand = generateSummaryCommand,
        enabledChatsConfig = enabledChats,
        summaryThreshold = THRESHOLD,
        summaryBatchSize = BATCH_SIZE,
    )

    private fun storedMessage(conversationMessageId: Long) = FakeModelProvider.createStoredMessage(
        peerId = PEER_ID,
        conversationMessageId = conversationMessageId,
    )

    private class RecordingHasPendingSummaryPort(
        private val hasPending: Boolean = false,
    ) : HasStoredPendingSummaryPort {
        val requests = ConcurrentLinkedQueue<HasStoredPendingSummaryPort.Request>()

        override fun execute(request: HasStoredPendingSummaryPort.Request): HasStoredPendingSummaryPort.Response {
            requests.add(request)
            return FakeSummaryProvider.createHasPendingSummaryResponse(hasPending)
        }
    }

    private class RecordingFindLastSummaryPort(
        private val summary: Summary? = null,
    ) : FindLastStoredSummaryPort {
        val requests = ConcurrentLinkedQueue<FindLastStoredSummaryPort.Request>()

        override fun execute(request: FindLastStoredSummaryPort.Request): FindLastStoredSummaryPort.Response {
            requests.add(request)
            return FakeSummaryProvider.createFindLastSummaryResponse(summary)
        }
    }

    private class RecordingFindMessagesBeforePort(
        private val messages: List<StoredMessage> = emptyList(),
    ) : FindStoredMessagesBeforePort {
        val requests = ConcurrentLinkedQueue<FindStoredMessagesBeforePort.Request>()

        override fun execute(request: FindStoredMessagesBeforePort.Request): FindStoredMessagesBeforePort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindMessagesBeforeResponse(messages)
        }
    }

    private class RecordingFindMessagesBetweenPort(
        private val messages: List<StoredMessage> = emptyList(),
    ) : FindStoredMessagesBetweenPort {
        val requests = ConcurrentLinkedQueue<FindStoredMessagesBetweenPort.Request>()

        override fun execute(request: FindStoredMessagesBetweenPort.Request): FindStoredMessagesBetweenPort.Response {
            requests.add(request)
            return FakeSummaryProvider.createFindMessagesBetweenResponse(messages)
        }
    }

    private class RecordingGenerateSummaryCommand(
        private val failure: Exception? = null,
    ) : GenerateSummaryCommand {
        val requests = ConcurrentLinkedQueue<GenerateSummaryCommand.GenerateSummaryRequest>()

        override fun execute(
            request: GenerateSummaryCommand.GenerateSummaryRequest,
        ): GenerateSummaryCommand.GenerateSummaryResponse {
            requests.add(request)
            failure?.let { throw it }
            return FakeSummaryProvider.createGenerateSummaryCommandResponse()
        }
    }

    companion object {
        @JvmStatic
        fun betweenWindows(): Stream<Arguments> = Stream.of(
            Arguments.of(10L, 10L),
            Arguments.of(null, 0L),
        )
    }
}
