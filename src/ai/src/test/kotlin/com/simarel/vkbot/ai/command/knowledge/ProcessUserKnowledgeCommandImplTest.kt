package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.ai.domain.exception.KnowledgeExtractionException
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeAdvanceExtractionCursorPort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindPendingKnowledgeBatchPort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeKnowledgeExtractionPort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeSaveUserFactsPort
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventResponse
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.UUID
import java.util.logging.Level

private const val PEER_ID = 2_000_000_001L
private const val IVAN_ID = 7_600_001L
private const val PETR_ID = 7_600_002L
private const val FIRST_MESSAGE_ID = 101L
private const val SECOND_MESSAGE_ID = 102L
private val OBSERVED_AT: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

class ProcessUserKnowledgeCommandImplTest {

    @Test
    fun `savesValidCandidatesAndAdvancesCursorAfterOneExtractionCall`() {
        val findPending = findPending(message(FIRST_MESSAGE_ID, IVAN_ID, "люблю футбол"))
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(FakeKnowledgeProvider.createKnowledgeFactDraft()),
            )
        }
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort()

        command(findPending = findPending, extraction = extraction, save = save, advance = advance).execute(
            request(FIRST_MESSAGE_ID),
        )

        assertEquals(
            FakePersistencePortProvider.createFindPendingKnowledgeBatchRequest(
                peerId = PEER_ID,
                upToConversationMessageId = FIRST_MESSAGE_ID,
                limit = 25,
            ),
            findPending.executeCalls.single(),
        )
        assertEquals(
            listOf("[msg=101|from=7600001|Иван Петров]: люблю футбол"),
            extraction.executeCalls.single().messages,
        )
        val candidate = save.executeCalls.single().candidates.single()
        assertEquals(IVAN_ID, candidate.userId)
        assertEquals("interest_football", candidate.factKey)
        assertEquals("футбол", candidate.value)
        assertEquals(FIRST_MESSAGE_ID, candidate.conversationMessageId)
        assertEquals(FIRST_MESSAGE_ID, advance.executeCalls.single().lastConversationMessageId)
    }

    @Test
    fun `advancesCursorAndSkipsSaveWhenAllDraftsAreInvalid`() {
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(FakeKnowledgeProvider.createKnowledgeFactDraft(category = "HOBBY")),
            )
        }
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort()
        val publish = FakePublishEventCommand()

        command(extraction = extraction, save = save, advance = advance, publish = publish).execute(
            request(FIRST_MESSAGE_ID),
        )

        assertTrue(save.executeCalls.isEmpty())
        assertEquals(FIRST_MESSAGE_ID, advance.executeCalls.single().lastConversationMessageId)
        assertEquals(listOf(Event.CHAT_KNOWLEDGE_SUMMARY_REQUESTED), publish.executeCalls.map { it.event })
    }

    @Test
    fun `advancesCursorToUpToWithoutCallingLlmWhenBatchIsEmpty`() {
        val findPending = FakeFindPendingKnowledgeBatchPort()
        val extraction = FakeKnowledgeExtractionPort()
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort()

        command(findPending = findPending, extraction = extraction, save = save, advance = advance).execute(
            request(FIRST_MESSAGE_ID),
        )

        assertTrue(extraction.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
        assertEquals(FIRST_MESSAGE_ID, advance.executeCalls.single().lastConversationMessageId)
    }

    @Test
    fun `advancesToBatchTailWithoutCallingLlmWhenEveryMessageIsFiltered`() {
        val lastMessageId = SECOND_MESSAGE_ID + 3
        val findPending = findPending(
            message(FIRST_MESSAGE_ID, IVAN_ID, null),
            message(SECOND_MESSAGE_ID, IVAN_ID, "   "),
            message(SECOND_MESSAGE_ID + 1, -IVAN_ID, "текст"),
            message(SECOND_MESSAGE_ID + 2, IVAN_ID, "текст", date = null),
            message(lastMessageId, IVAN_ID, "текст", id = null),
        )
        val extraction = FakeKnowledgeExtractionPort()
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort()

        command(findPending = findPending, extraction = extraction, save = save, advance = advance).execute(
            request(lastMessageId),
        )

        assertTrue(extraction.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
        assertEquals(lastMessageId, advance.executeCalls.single().lastConversationMessageId)
    }

    @Test
    fun `publishesOneProfileSummaryPerUserAndOneChatSummaryAfterSuccess`() {
        val findPending = findPending(
            message(FIRST_MESSAGE_ID, IVAN_ID, "люблю футбол"),
            message(SECOND_MESSAGE_ID, PETR_ID, "играю в шахматы"),
        )
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(
                    FakeKnowledgeProvider.createKnowledgeFactDraft(subjectId = IVAN_ID),
                    FakeKnowledgeProvider.createKnowledgeFactDraft(
                        subjectId = PETR_ID,
                        key = "knowledge_chess",
                        value = "шахматы",
                        sourceConversationMessageId = SECOND_MESSAGE_ID,
                    ),
                ),
            )
        }
        val publish = FakePublishEventCommand()

        command(findPending = findPending, extraction = extraction, publish = publish).execute(
            request(SECOND_MESSAGE_ID),
        )

        assertEquals(
            listOf(
                Event.USER_KNOWLEDGE_SUMMARY_REQUESTED,
                Event.USER_KNOWLEDGE_SUMMARY_REQUESTED,
                Event.CHAT_KNOWLEDGE_SUMMARY_REQUESTED,
            ),
            publish.executeCalls.map { it.event },
        )
        assertEquals(
            listOf(
                FakeEventProvider.createUserKnowledgeSummaryRequestedPayload(userId = IVAN_ID),
                FakeEventProvider.createUserKnowledgeSummaryRequestedPayload(userId = PETR_ID),
                FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(peerId = PEER_ID),
            ),
            publish.executeCalls.map { it.payload.value },
        )
    }

    @Test
    fun `keepsExtractionResultWhenSummaryPublicationFails`() {
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort()
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(FakeKnowledgeProvider.createKnowledgeFactDraft()),
            )
        }

        SystemLoggerCapture(ProcessUserKnowledgeCommandImpl::class.java.name).use { capture ->
            command(
                extraction = extraction,
                save = save,
                advance = advance,
                publish = FailingPublishEventCommand(),
            ).execute(request(FIRST_MESSAGE_ID))

            assertEquals(1, save.executeCalls.size)
            assertEquals(1, advance.executeCalls.size)
            val warnings = capture.messages(Level.WARNING)
            assertEquals(2, warnings.size)
            warnings.forEach { warning ->
                assertTrue(warning.contains("peerId=2000000001"), warning)
                assertTrue(warning.contains("RuntimeException"), warning)
            }
        }
    }

    @Test
    fun `rethrowsLlmFailureWithoutSavingOrAdvancing`() {
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort()
        val extraction = FakeKnowledgeExtractionPort().apply {
            failure = KnowledgeExtractionException("boom", null)
        }

        assertThrows<KnowledgeExtractionException> {
            command(extraction = extraction, save = save, advance = advance).execute(request(FIRST_MESSAGE_ID))
        }

        assertTrue(save.executeCalls.isEmpty())
        assertTrue(advance.executeCalls.isEmpty())
    }

    @Test
    fun `rethrowsSaveFailureWithoutAdvancing`() {
        val save = FakeSaveUserFactsPort().apply { failure = RuntimeException("boom") }
        val advance = FakeAdvanceExtractionCursorPort()
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(FakeKnowledgeProvider.createKnowledgeFactDraft()),
            )
        }

        assertThrows<RuntimeException> {
            command(extraction = extraction, save = save, advance = advance).execute(request(FIRST_MESSAGE_ID))
        }

        assertEquals(1, save.executeCalls.size)
        assertTrue(advance.executeCalls.isEmpty())
    }

    @Test
    fun `rethrowsCursorFailureAfterSavingFacts`() {
        val save = FakeSaveUserFactsPort()
        val advance = FakeAdvanceExtractionCursorPort().apply { failure = RuntimeException("boom") }
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(FakeKnowledgeProvider.createKnowledgeFactDraft()),
            )
        }

        assertThrows<RuntimeException> {
            command(extraction = extraction, save = save, advance = advance).execute(request(FIRST_MESSAGE_ID))
        }

        assertEquals(1, save.executeCalls.size)
        assertEquals(1, advance.executeCalls.size)
    }

    @Test
    fun `logsOnlyCountsAndIds`() {
        val extraction = FakeKnowledgeExtractionPort().apply {
            response = FakeKnowledgeProvider.createKnowledgeExtractionResponse(
                listOf(
                    FakeKnowledgeProvider.createKnowledgeFactDraft(),
                    FakeKnowledgeProvider.createKnowledgeFactDraft(category = "HOBBY"),
                ),
            )
        }

        SystemLoggerCapture(ProcessUserKnowledgeCommandImpl::class.java.name).use { capture ->
            command(extraction = extraction).execute(request(FIRST_MESSAGE_ID))

            val info = capture.messages(Level.INFO).single()
            assertTrue(info.contains("peerId=2000000001"), info)
            assertTrue(info.contains("saved=1"), info)
            assertTrue(info.contains("skipped=1"), info)
            assertTrue(!info.contains("люблю футбол"), info)
            assertTrue(!info.contains("футбол"), info)
            assertTrue(!info.contains("Иван Петров"), info)
        }
    }

    private fun command(
        findPending: FakeFindPendingKnowledgeBatchPort = findPending(
            message(FIRST_MESSAGE_ID, IVAN_ID, "люблю футбол"),
        ),
        extraction: FakeKnowledgeExtractionPort = FakeKnowledgeExtractionPort(),
        save: FakeSaveUserFactsPort = FakeSaveUserFactsPort(),
        advance: FakeAdvanceExtractionCursorPort = FakeAdvanceExtractionCursorPort(),
        publish: PublishEventCommand = FakePublishEventCommand(),
        profiles: List<VkUserProfile> = listOf(
            FakeModelProvider.createVkUserProfile(id = IVAN_ID, firstName = "Иван", lastName = "Петров"),
            FakeModelProvider.createVkUserProfile(id = PETR_ID, firstName = "Пётр", lastName = "Иванов"),
        ),
    ) = ProcessUserKnowledgeCommandImpl(
        findPendingKnowledgeBatchPort = findPending,
        knowledgeExtractionPort = extraction,
        saveUserFactsPort = save,
        advanceExtractionCursorPort = advance,
        publishEventCommand = publish,
        findStoredUserProfilesByIdsPort = RecordingFindStoredUserProfilesByIdsPort(profiles),
        batchSize = 25,
    )

    private fun request(upToConversationMessageId: Long): ProcessUserKnowledgeCommand.Request {
        val payload = FakeEventProvider.createUserKnowledgeRequestedPayload(
            peerId = PEER_ID,
            upToConversationMessageId = upToConversationMessageId,
        )
        return FakeKnowledgeProvider.createProcessUserKnowledgeCommandRequest(payload = payload)
    }

    private fun findPending(vararg messages: StoredMessage) = FakeFindPendingKnowledgeBatchPort().apply {
        this.messages = messages.toList()
    }

    private fun message(
        conversationMessageId: Long,
        fromId: Long,
        messageText: String?,
        date: OffsetDateTime? = OBSERVED_AT,
        id: UUID? = UUID.randomUUID(),
    ) = FakeModelProvider.createStoredMessage(
        id = id,
        peerId = PEER_ID,
        conversationMessageId = conversationMessageId,
        fromId = fromId,
        messageText = messageText,
        date = date,
    )

    private class RecordingFindStoredUserProfilesByIdsPort(
        private val profiles: List<VkUserProfile>,
    ) : FindStoredUserProfilesByIdsPort {
        private val response = FakeProfileProvider.createFindStoredUserProfilesByIdsResponse(profiles = profiles)

        override fun execute(
            request: FindStoredUserProfilesByIdsPort.Request,
        ): FindStoredUserProfilesByIdsPort.Response = response
    }

    @Suppress("TooGenericExceptionThrown")
    private class FailingPublishEventCommand : PublishEventCommand {
        override fun execute(request: PublishEventRequest): PublishEventResponse = throw RuntimeException("boom")
    }
}
