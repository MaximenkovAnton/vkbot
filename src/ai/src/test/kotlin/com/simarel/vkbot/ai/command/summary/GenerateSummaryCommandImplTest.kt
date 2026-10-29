package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.ai.port.output.summary.SummarizationResponse
import com.simarel.vkbot.ai.testfixtures.FakeConversationContextPortProvider
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

private const val PEER_ID = 2_000_000_001L
private const val FIRST_MESSAGE_ID = 10L
private const val LAST_MESSAGE_ID = 20L
private const val MESSAGES_LIMIT = 1000
private val SUMMARY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
private val REQUEST = FakeSummaryProvider.createGenerateSummaryCommandRequest(
    peerId = PEER_ID,
    firstMessageId = FIRST_MESSAGE_ID,
    lastMessageId = LAST_MESSAGE_ID,
)

class GenerateSummaryCommandImplTest {

    @Test
    fun `execute creates the pending summary first and completes it with the llm result`() {
        val calls = mutableListOf<String>()
        val createPending = RecordingCreatePendingSummaryPort(SUMMARY_ID, calls)
        val findMessages = RecordingFindMessagesBetweenPort(listOf(storedMessage(10)), calls)
        val saveCompleted = RecordingSaveCompletedSummaryPort()
        val markFailed = RecordingMarkSummaryFailedPort()
        val command = generateSummaryCommand(
            summarizationPort = RecordingSummarizationOutputPort(
                FakeSummaryProvider.createSummarizationResponse(
                    shortSummary = "llm short",
                    fullSummary = "llm full",
                ),
            ),
            createPendingSummaryPort = createPending,
            findMessagesBetweenPort = findMessages,
            saveCompletedSummaryPort = saveCompleted,
            markSummaryAsFailedPort = markFailed,
        )

        val response = command.execute(REQUEST)

        assertEquals(listOf("createPending", "findMessages"), calls)
        assertEquals(
            FakePersistencePortProvider.createCreatePendingSummaryRequest(
                peerId = PEER_ID,
                firstMessageId = FIRST_MESSAGE_ID,
                lastMessageId = LAST_MESSAGE_ID,
            ),
            createPending.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createFindMessagesBetweenRequest(
                peerId = PEER_ID,
                firstMessageId = FIRST_MESSAGE_ID,
                lastMessageId = LAST_MESSAGE_ID,
                limit = MESSAGES_LIMIT,
            ),
            findMessages.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createSaveCompletedSummaryRequest(
                summaryId = SUMMARY_ID,
                shortSummary = "llm short",
                fullSummary = "llm full",
            ),
            saveCompleted.requests.single(),
        )
        assertEquals(SUMMARY_ID, response.summaryId)
        assertTrue(markFailed.requests.isEmpty())
    }

    @Test
    fun `execute completes with the no-messages texts and skips the llm when the range is empty`() {
        val summarization = RecordingSummarizationOutputPort()
        val saveCompleted = RecordingSaveCompletedSummaryPort()
        val command = generateSummaryCommand(
            summarizationPort = summarization,
            saveCompletedSummaryPort = saveCompleted,
        )

        val response = command.execute(REQUEST)

        assertEquals(
            FakePersistencePortProvider.createSaveCompletedSummaryRequest(
                summaryId = SUMMARY_ID,
                shortSummary = "Нет сообщений",
                fullSummary = "Нет сообщений для суммаризации",
            ),
            saveCompleted.requests.single(),
        )
        assertTrue(summarization.requests.isEmpty())
        assertEquals(SUMMARY_ID, response.summaryId)
    }

    @Test
    fun `execute marks the summary failed and rethrows when the llm fails`() {
        val failure = IllegalStateException("llm is down")
        val saveCompleted = RecordingSaveCompletedSummaryPort()
        val markFailed = RecordingMarkSummaryFailedPort()
        val command = generateSummaryCommand(
            summarizationPort = RecordingSummarizationOutputPort(failure = failure),
            findMessagesBetweenPort = RecordingFindMessagesBetweenPort(listOf(storedMessage(10))),
            saveCompletedSummaryPort = saveCompleted,
            markSummaryAsFailedPort = markFailed,
        )

        val thrown = assertThrows(IllegalStateException::class.java) { command.execute(REQUEST) }

        assertSame(failure, thrown)
        assertEquals(
            FakePersistencePortProvider.createMarkSummaryFailedRequest(summaryId = SUMMARY_ID),
            markFailed.requests.single(),
        )
        assertTrue(saveCompleted.requests.isEmpty())
    }

    @Test
    fun `execute formats messages as author and text separated by a blank line`() {
        val summarization = RecordingSummarizationOutputPort()
        val command = generateSummaryCommand(
            summarizationPort = summarization,
            findMessagesBetweenPort = RecordingFindMessagesBetweenPort(
                listOf(
                    storedMessage(10, fromId = 5, messageText = "hello"),
                    storedMessage(11, fromId = -8, messageText = "hi"),
                ),
            ),
            findUserProfilesByIdsPort = RecordingFindUserProfilesByIdsPort(
                listOf(FakeModelProvider.createVkUserProfile(id = 5, firstName = "Ivan", lastName = "Petrov")),
            ),
            findGroupProfilesByIdsPort = RecordingFindGroupProfilesByIdsPort(
                listOf(FakeModelProvider.createVkGroupProfile(id = 8, name = "Kotlin Group")),
            ),
        )

        command.execute(REQUEST)

        assertEquals("[Ivan Petrov]: hello\n\n[Kotlin Group]: hi", summarization.requests.single().messages)
    }

    @Test
    fun `execute asks for user and group profiles separately`() {
        val findUserProfiles = RecordingFindUserProfilesByIdsPort()
        val findGroupProfiles = RecordingFindGroupProfilesByIdsPort()
        val command = generateSummaryCommand(
            findMessagesBetweenPort = RecordingFindMessagesBetweenPort(
                listOf(storedMessage(10, fromId = 5), storedMessage(11, fromId = -8)),
            ),
            findUserProfilesByIdsPort = findUserProfiles,
            findGroupProfilesByIdsPort = findGroupProfiles,
        )

        command.execute(REQUEST)

        assertEquals(
            FakePersistencePortProvider.createFindUserProfilesByIdsRequest(
                ids = listOf(FakeVoProvider.createFromId(5)),
            ),
            findUserProfiles.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createFindGroupProfilesByIdsRequest(
                ids = listOf(FakeVoProvider.createFromId(-8)),
            ),
            findGroupProfiles.requests.single(),
        )
    }

    @ParameterizedTest
    @CsvSource("5, 'Пользователь 5'", "-8, 'Группа -8'")
    fun `execute falls back to placeholders when a profile is missing`(fromId: Long, expectedAuthor: String) {
        val summarization = RecordingSummarizationOutputPort()
        val command = generateSummaryCommand(
            summarizationPort = summarization,
            findMessagesBetweenPort = RecordingFindMessagesBetweenPort(
                listOf(storedMessage(10, fromId = fromId, messageText = "hello")),
            ),
        )

        command.execute(REQUEST)

        assertEquals("[$expectedAuthor]: hello", summarization.requests.single().messages)
    }

    private fun generateSummaryCommand(
        summarizationPort: SummarizationOutputPort = RecordingSummarizationOutputPort(),
        createPendingSummaryPort: CreateStoredPendingSummaryPort = RecordingCreatePendingSummaryPort(),
        findMessagesBetweenPort: FindStoredMessagesBetweenPort = RecordingFindMessagesBetweenPort(),
        saveCompletedSummaryPort: SaveStoredCompletedSummaryPort = RecordingSaveCompletedSummaryPort(),
        markSummaryAsFailedPort: MarkStoredSummaryFailedPort = RecordingMarkSummaryFailedPort(),
        findUserProfilesByIdsPort: FindStoredUserProfilesByIdsPort = RecordingFindUserProfilesByIdsPort(),
        findGroupProfilesByIdsPort: FindStoredGroupProfilesByIdsPort = RecordingFindGroupProfilesByIdsPort(),
    ) = GenerateSummaryCommandImpl(
        summarizationPort = summarizationPort,
        createPendingSummaryPort = createPendingSummaryPort,
        findMessagesBetweenPort = findMessagesBetweenPort,
        saveCompletedSummaryPort = saveCompletedSummaryPort,
        markSummaryAsFailedPort = markSummaryAsFailedPort,
        findUserProfilesByIdsPort = findUserProfilesByIdsPort,
        findGroupProfilesByIdsPort = findGroupProfilesByIdsPort,
    )

    private fun storedMessage(
        conversationMessageId: Long,
        fromId: Long = 5,
        messageText: String = "hello",
    ) = FakeModelProvider.createStoredMessage(
        peerId = PEER_ID,
        conversationMessageId = conversationMessageId,
        fromId = fromId,
        messageText = messageText,
    )

    private class RecordingSummarizationOutputPort(
        private val response: SummarizationResponse = FakeSummaryProvider.createSummarizationResponse(),
        private val failure: Exception? = null,
    ) : SummarizationOutputPort {
        val requests = ConcurrentLinkedQueue<SummarizationRequest>()

        override fun execute(request: SummarizationRequest): SummarizationResponse {
            requests.add(request)
            failure?.let { throw it }
            return response
        }
    }

    private class RecordingCreatePendingSummaryPort(
        private val summaryId: UUID = SUMMARY_ID,
        private val calls: MutableList<String>? = null,
    ) : CreateStoredPendingSummaryPort {
        val requests = ConcurrentLinkedQueue<CreateStoredPendingSummaryPort.Request>()

        override fun execute(
            request: CreateStoredPendingSummaryPort.Request,
        ): CreateStoredPendingSummaryPort.Response {
            requests.add(request)
            calls?.add("createPending")
            return FakeSummaryProvider.createCreatePendingSummaryResponse(summaryId)
        }
    }

    private class RecordingFindMessagesBetweenPort(
        private val messages: List<StoredMessage> = emptyList(),
        private val calls: MutableList<String>? = null,
    ) : FindStoredMessagesBetweenPort {
        val requests = ConcurrentLinkedQueue<FindStoredMessagesBetweenPort.Request>()

        override fun execute(
            request: FindStoredMessagesBetweenPort.Request,
        ): FindStoredMessagesBetweenPort.Response {
            requests.add(request)
            calls?.add("findMessages")
            return FakeSummaryProvider.createFindMessagesBetweenResponse(messages)
        }
    }

    private class RecordingSaveCompletedSummaryPort : SaveStoredCompletedSummaryPort {
        val requests = ConcurrentLinkedQueue<SaveStoredCompletedSummaryPort.Request>()

        override fun execute(
            request: SaveStoredCompletedSummaryPort.Request,
        ): SaveStoredCompletedSummaryPort.Response {
            requests.add(request)
            return FakeSummaryProvider.createSaveCompletedSummaryResponse()
        }
    }

    private class RecordingMarkSummaryFailedPort : MarkStoredSummaryFailedPort {
        val requests = ConcurrentLinkedQueue<MarkStoredSummaryFailedPort.Request>()

        override fun execute(request: MarkStoredSummaryFailedPort.Request): MarkStoredSummaryFailedPort.Response {
            requests.add(request)
            return FakeSummaryProvider.createMarkSummaryFailedResponse()
        }
    }

    private class RecordingFindUserProfilesByIdsPort(
        private val profiles: List<VkUserProfile> = emptyList(),
    ) : FindStoredUserProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredUserProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredUserProfilesByIdsPort.Request,
        ): FindStoredUserProfilesByIdsPort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindUserProfilesByIdsResponse(profiles)
        }
    }

    private class RecordingFindGroupProfilesByIdsPort(
        private val profiles: List<VkGroupProfile> = emptyList(),
    ) : FindStoredGroupProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredGroupProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredGroupProfilesByIdsPort.Request,
        ): FindStoredGroupProfilesByIdsPort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindGroupProfilesByIdsResponse(profiles)
        }
    }
}
