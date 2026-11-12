package com.simarel.vkbot.processor.command.knowledge

import com.simarel.vkbot.processor.test.SystemLoggerCapture
import com.simarel.vkbot.processor.testfixtures.command.FakeKnowledgeProvider
import com.simarel.vkbot.processor.testfixtures.port.output.knowledge.FakeHasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventResponse
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.util.logging.Level

private const val PEER_ID = 2_000_000_001L
private const val EXCLUDED_PEER_ID = 2_000_000_002L
private const val BOT_ID = 9_000_001L
private const val AUTHOR_ID = 7_000_001L
private const val CONVERSATION_MESSAGE_ID = 101L
private val BATCH_INTERVAL: Duration = Duration.ofMinutes(30)

class RequestKnowledgeExtractionCommandImplTest {

    @Test
    fun `publishesOneEventWhenAllConditionsHold`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }
        val command = command(publish = publish, hasPending = hasPending)

        command.execute(FakeKnowledgeProvider.createRequest(message()))

        val request = hasPending.executeCalls.single()
        assertEquals(
            FakePersistencePortProvider.createHasPendingKnowledgeBatchRequest(
                peerId = PEER_ID,
                minCount = 25,
                minInterval = BATCH_INTERVAL,
            ),
            request,
        )
        val published = publish.executeCalls.single()
        assertEquals(Event.USER_KNOWLEDGE_REQUESTED, published.event)
        assertEquals(
            FakeEventProvider.createUserKnowledgeRequestedPayload(
                peerId = PEER_ID,
                upToConversationMessageId = CONVERSATION_MESSAGE_ID,
            ),
            published.payload.value,
        )
    }

    @Test
    fun `publishesNothingWhenDisabled`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }

        command(publish = publish, hasPending = hasPending, enabled = false).execute(
            FakeKnowledgeProvider.createRequest(message()),
        )

        assertEquals(0, hasPending.executeCalls.size)
        assertEquals(0, publish.executeCalls.size)
    }

    @Test
    fun `publishesNothingForCommunityAuthor`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }

        command(publish = publish, hasPending = hasPending).execute(
            FakeKnowledgeProvider.createRequest(message(fromId = -2_000_000_001)),
        )

        assertEquals(0, hasPending.executeCalls.size)
        assertEquals(0, publish.executeCalls.size)
    }

    @Test
    fun `publishesNothingForBotAuthor`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }

        command(publish = publish, hasPending = hasPending).execute(
            FakeKnowledgeProvider.createRequest(message(fromId = BOT_ID)),
        )

        assertEquals(0, hasPending.executeCalls.size)
        assertEquals(0, publish.executeCalls.size)
    }

    @ParameterizedTest
    @ValueSource(longs = [EXCLUDED_PEER_ID])
    fun `publishesNothingForExcludedChat`(excludedPeerId: Long) {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }
        val command = command(
            publish = publish,
            hasPending = hasPending,
            excludedChatIds = excludedPeerId.toString(),
        )

        command.execute(FakeKnowledgeProvider.createRequest(message(peerId = excludedPeerId)))

        assertEquals(0, hasPending.executeCalls.size)
        assertEquals(0, publish.executeCalls.size)
    }

    @Test
    fun `publishesNothingWhenNoBatchPending`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort()

        command(publish = publish, hasPending = hasPending).execute(
            FakeKnowledgeProvider.createRequest(message()),
        )

        assertEquals(1, hasPending.executeCalls.size)
        assertEquals(0, publish.executeCalls.size)
    }

    @Test
    fun `failsOpenWhenPublicationFails`() {
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { this.hasPending = true }

        SystemLoggerCapture(RequestKnowledgeExtractionCommandImpl::class.java.name).use { capture ->
            command(publish = FailingPublishEventCommand(), hasPending = hasPending).execute(
                FakeKnowledgeProvider.createRequest(message()),
            )

            val warnings = capture.messages(Level.WARNING)
            assertEquals(1, warnings.size)
            assertTrue(warnings.single().contains("peerId=2000000001"), warnings.single())
            assertTrue(warnings.single().contains("RuntimeException"), warnings.single())
        }
    }

    @Test
    fun `failsOpenWithPeerIdAndErrorClass`() {
        val publish = FakePublishEventCommand()
        val hasPending = FakeHasPendingKnowledgeBatchPort().apply { failure = RuntimeException("boom") }

        SystemLoggerCapture(RequestKnowledgeExtractionCommandImpl::class.java.name).use { capture ->
            command(publish = publish, hasPending = hasPending).execute(
                FakeKnowledgeProvider.createRequest(message()),
            )

            val warnings = capture.messages(Level.WARNING)
            assertEquals(1, warnings.size)
            assertTrue(warnings.single().contains("peerId=2000000001"), warnings.single())
            assertTrue(warnings.single().contains("RuntimeException"), warnings.single())
        }
        assertEquals(0, publish.executeCalls.size)
    }

    private fun command(
        publish: PublishEventCommand = FakePublishEventCommand(),
        hasPending: FakeHasPendingKnowledgeBatchPort = FakeHasPendingKnowledgeBatchPort(),
        enabled: Boolean = true,
        excludedChatIds: String = "",
    ) = RequestKnowledgeExtractionCommandImpl(
        publishEventCommand = publish,
        hasPendingKnowledgeBatchPort = hasPending,
        enabled = enabled,
        batchSize = 25,
        batchInterval = BATCH_INTERVAL,
        excludedChatIdsConfig = excludedChatIds,
        botId = BOT_ID,
    )

    private fun message(
        fromId: Long = AUTHOR_ID,
        peerId: Long = PEER_ID,
    ) = FakeMessageProvider.createMessage(
        fromId = FakeVoProvider.createFromId(fromId),
        peerId = FakeVoProvider.createPeerId(peerId),
        conversationMessageId = FakeVoProvider.createConversationMessageId(CONVERSATION_MESSAGE_ID),
    )

    @Suppress("TooGenericExceptionThrown")
    private class FailingPublishEventCommand : PublishEventCommand {
        override fun execute(request: PublishEventRequest): PublishEventResponse = throw RuntimeException("boom")
    }
}
