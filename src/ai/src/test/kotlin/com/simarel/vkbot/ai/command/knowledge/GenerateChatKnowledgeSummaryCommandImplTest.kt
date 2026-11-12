package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.ai.command.common.MessageAuthorFormatter
import com.simarel.vkbot.ai.domain.exception.ChatKnowledgeSummaryException
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeChatKnowledgeSummaryPort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindChatKnowledgeSummaryPort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindChatSummaryMessagesPort
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

private const val PEER_ID = 2_000_000_001L
private const val IVAN_ID = 7_600_001L
private const val THRESHOLD = 100
private const val SUMMARY_TEXT = "Темы: программирование, мемы и шутки."
private const val MAX_SUMMARY_LENGTH = 2000
private const val FIRST_WINDOW_ID = 101L
private const val BELOW_THRESHOLD_WINDOW = 50
private const val ARCHIVE_START_WINDOW = 3
private val INTERVAL: Duration = Duration.ofDays(7)

class GenerateChatKnowledgeSummaryCommandImplTest {

    @Test
    fun `generatesAndSavesSummaryAfterOneLlmCallWhenThresholdReached`() {
        val findMessages = findMessages(window(THRESHOLD))
        val findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)))
        val llm = FakeChatKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }
        val save = RecordingSaveChatKnowledgeSummaryPort()

        command(findMessages = findMessages, findSummary = findSummary, llm = llm, save = save).execute(request())

        assertEquals(1, llm.executeCalls.size)
        assertEquals(THRESHOLD, llm.executeCalls.single().messages.size)
        assertEquals(
            FakePersistencePortProvider.createFindChatSummaryMessagesRequest(peerId = PEER_ID, limit = THRESHOLD),
            findMessages.executeCalls.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = PEER_ID,
                summaryText = SUMMARY_TEXT,
                firstConversationMessageId = FIRST_WINDOW_ID,
                lastConversationMessageId = FIRST_WINDOW_ID + THRESHOLD - 1,
                messagesCovered = THRESHOLD,
            ),
            save.executeCalls.single(),
        )
    }

    @Test
    fun `skipsWhenBelowThresholdAndIntervalNotElapsed`() {
        val findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)))
        val llm = FakeChatKnowledgeSummaryPort()
        val save = RecordingSaveChatKnowledgeSummaryPort()

        SystemLoggerCapture(GenerateChatKnowledgeSummaryCommandImpl::class.java.name).use { capture ->
            command(
                findMessages = findMessages(window(BELOW_THRESHOLD_WINDOW)),
                findSummary = findSummary,
                llm = llm,
                save = save,
            ).execute(request())

            assertTrue(
                capture.messages(Level.FINE).any {
                    it.contains("peerId=2000000001") && it.contains("below threshold")
                },
                capture.messages(Level.FINE).toString(),
            )
        }
        assertTrue(llm.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `generatesWhenIntervalElapsedEvenBelowThreshold`() {
        val findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusDays(8)))
        val llm = FakeChatKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }
        val save = RecordingSaveChatKnowledgeSummaryPort()

        command(
            findMessages = findMessages(window(BELOW_THRESHOLD_WINDOW)),
            findSummary = findSummary,
            llm = llm,
            save = save,
        ).execute(request())

        assertEquals(1, llm.executeCalls.size)
        assertEquals(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = PEER_ID,
                summaryText = SUMMARY_TEXT,
                firstConversationMessageId = FIRST_WINDOW_ID,
                lastConversationMessageId = FIRST_WINDOW_ID + BELOW_THRESHOLD_WINDOW - 1,
                messagesCovered = BELOW_THRESHOLD_WINDOW,
            ),
            save.executeCalls.single(),
        )
    }

    @Test
    fun `generatesFromArchiveStartWhenNoSummaryExists`() {
        val findSummary = findSummary(null)
        val llm = FakeChatKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }
        val save = RecordingSaveChatKnowledgeSummaryPort()

        command(
            findMessages = findMessages(window(ARCHIVE_START_WINDOW, startId = 1)),
            findSummary = findSummary,
            llm = llm,
            save = save,
        ).execute(request())

        assertEquals(1, llm.executeCalls.size)
        assertEquals(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = PEER_ID,
                summaryText = SUMMARY_TEXT,
                firstConversationMessageId = 1,
                lastConversationMessageId = ARCHIVE_START_WINDOW.toLong(),
                messagesCovered = ARCHIVE_START_WINDOW,
            ),
            save.executeCalls.single(),
        )
    }

    @Test
    fun `skipsWithoutLlmAndSaveWhenWindowIsEmpty`() {
        val findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)))
        val llm = FakeChatKnowledgeSummaryPort()
        val save = RecordingSaveChatKnowledgeSummaryPort()

        SystemLoggerCapture(GenerateChatKnowledgeSummaryCommandImpl::class.java.name).use { capture ->
            command(
                findMessages = findMessages(emptyList()),
                findSummary = findSummary,
                llm = llm,
                save = save,
            ).execute(request())

            assertTrue(
                capture.messages(Level.FINE).any { it.contains("empty window") },
                capture.messages(Level.FINE).toString(),
            )
        }
        assertTrue(findSummary.executeCalls.isEmpty())
        assertTrue(llm.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `rethrowsLlmFailureWithoutSaving`() {
        val llm = FakeChatKnowledgeSummaryPort().apply {
            failure = ChatKnowledgeSummaryException("generation failed", null)
        }
        val save = RecordingSaveChatKnowledgeSummaryPort()

        assertThrows<ChatKnowledgeSummaryException> {
            command(
                findMessages = findMessages(window(THRESHOLD)),
                findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))),
                llm = llm,
                save = save,
            ).execute(request())
        }

        assertTrue(llm.executeCalls.size == 1)
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `rethrowsReadFailureWithoutLlmOrSave`() {
        val findMessages = findMessages(window(THRESHOLD)).apply { failure = RuntimeException("read failed") }
        val llm = FakeChatKnowledgeSummaryPort()
        val save = RecordingSaveChatKnowledgeSummaryPort()

        assertThrows<RuntimeException> {
            command(findMessages = findMessages, findSummary = findSummary(null), llm = llm, save = save)
                .execute(request())
        }

        assertTrue(llm.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `rethrowsSaveFailureAfterLlmCall`() {
        val llm = FakeChatKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }
        val save = RecordingSaveChatKnowledgeSummaryPort(failure = RuntimeException("save failed"))

        assertThrows<RuntimeException> {
            command(
                findMessages = findMessages(window(THRESHOLD)),
                findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))),
                llm = llm,
                save = save,
            ).execute(request())
        }

        assertEquals(1, llm.executeCalls.size)
    }

    @Test
    fun `truncatesOverlongSummaryTo2000Chars`() {
        val llm = FakeChatKnowledgeSummaryPort().apply { text = "а".repeat(MAX_SUMMARY_LENGTH + 500) }
        val save = RecordingSaveChatKnowledgeSummaryPort()

        command(
            findMessages = findMessages(window(THRESHOLD)),
            findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))),
            llm = llm,
            save = save,
        ).execute(request())

        val saved = save.executeCalls.single()
        assertEquals(MAX_SUMMARY_LENGTH, saved.summaryText.length)
        assertEquals("а".repeat(MAX_SUMMARY_LENGTH), saved.summaryText)
    }

    @Test
    fun `logsOnlyCountsAndIds`() {
        val messages = window(THRESHOLD, text = "люблю футбол")

        SystemLoggerCapture(GenerateChatKnowledgeSummaryCommandImpl::class.java.name).use { capture ->
            command(
                findMessages = findMessages(messages),
                findSummary = findSummary(chatKnowledge(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))),
                llm = FakeChatKnowledgeSummaryPort().apply { text = SUMMARY_TEXT },
                save = RecordingSaveChatKnowledgeSummaryPort(),
            ).execute(request())

            val info = capture.messages(Level.INFO)
            assertTrue(info.any { it.contains("peerId=2000000001") && it.contains("messages=100") }, info.toString())
            info.forEach { line ->
                assertTrue(!line.contains("футбол"), line)
                assertTrue(!line.contains("Иван Петров"), line)
                assertTrue(!line.contains("Темы:"), line)
            }
        }
    }

    private fun command(
        findMessages: FakeFindChatSummaryMessagesPort,
        findSummary: FakeFindChatKnowledgeSummaryPort,
        llm: FakeChatKnowledgeSummaryPort,
        save: RecordingSaveChatKnowledgeSummaryPort,
    ) = GenerateChatKnowledgeSummaryCommandImpl(
        findChatSummaryMessagesPort = findMessages,
        findChatKnowledgeSummaryPort = findSummary,
        chatKnowledgeSummaryPort = llm,
        saveChatKnowledgeSummaryPort = save,
        messageAuthorFormatter = MessageAuthorFormatter(
            RecordingUserProfilesPort(
                listOf(FakeModelProvider.createVkUserProfile(id = IVAN_ID, firstName = "Иван", lastName = "Петров")),
            ),
            RecordingGroupProfilesPort(),
        ),
        threshold = THRESHOLD,
        interval = INTERVAL,
    )

    private fun request() = FakeKnowledgeProvider.createGenerateChatKnowledgeSummaryCommandRequest(
        payload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(peerId = PEER_ID),
    )

    private fun findMessages(messages: List<StoredMessage>) = FakeFindChatSummaryMessagesPort().apply {
        this.messages = messages
    }

    private fun findSummary(knowledge: ChatKnowledge?) = FakeFindChatKnowledgeSummaryPort().apply {
        this.knowledge = knowledge
    }

    private fun chatKnowledge(summarizedAt: OffsetDateTime) = FakeModelProvider.createChatKnowledge(
        peerId = PEER_ID,
        summary = "прежняя сводка",
        summarizedAt = summarizedAt,
    )

    private fun window(
        count: Int,
        startId: Long = FIRST_WINDOW_ID,
        fromId: Long = IVAN_ID,
        text: String = "люблю футбол",
    ): List<StoredMessage> = (0 until count).map { index ->
        FakeModelProvider.createStoredMessage(
            peerId = PEER_ID,
            conversationMessageId = startId + index,
            fromId = fromId,
            messageText = text,
        )
    }

    private class RecordingUserProfilesPort(
        private val profiles: List<VkUserProfile>,
    ) : FindStoredUserProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredUserProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredUserProfilesByIdsPort.Request,
        ): FindStoredUserProfilesByIdsPort.Response {
            requests.add(request)
            return FakeProfileProvider.createFindStoredUserProfilesByIdsResponse(profiles = profiles)
        }
    }

    private class RecordingGroupProfilesPort : FindStoredGroupProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredGroupProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredGroupProfilesByIdsPort.Request,
        ): FindStoredGroupProfilesByIdsPort.Response {
            requests.add(request)
            return FakeProfileProvider.createFindStoredGroupProfilesByIdsResponse()
        }
    }

    private class RecordingSaveChatKnowledgeSummaryPort(
        private val failure: RuntimeException? = null,
    ) : SaveChatKnowledgeSummaryPort {
        val executeCalls = ConcurrentLinkedQueue<SaveChatKnowledgeSummaryPort.Request>()

        override fun execute(
            request: SaveChatKnowledgeSummaryPort.Request,
        ): SaveChatKnowledgeSummaryPort.Response {
            executeCalls.add(request)
            failure?.let { throw it }
            return FakePersistencePortProvider.createSaveChatKnowledgeSummaryResponse()
        }
    }
}
