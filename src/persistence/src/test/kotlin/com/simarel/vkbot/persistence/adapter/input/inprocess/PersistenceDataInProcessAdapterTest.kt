package com.simarel.vkbot.persistence.adapter.input.inprocess

import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.persistence.testfixtures.PersistenceDataRequestProvider
import com.simarel.vkbot.persistence.testfixtures.PersistenceDataResponseProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PersistenceDataInProcessAdapterTest {

    private val storedMessage = FakeModelProvider.createStoredMessage(
        peerId = 10,
        conversationMessageId = 20,
        fromId = 30,
        messageText = "text",
    )

    @Test
    fun `find messages before forwards request and returns messages`() {
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage)),
        )
        val adapter = FindStoredMessagesBeforeInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindMessagesBeforeRequest(
                peerId = 1,
                beforeConversationMessageId = 2,
                limit = 3,
            ),
        )

        assertEquals(PersistenceDataRequestProvider.createFindMessagesBeforeRequest(1, 2, 3), port.requests.single())
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun `find messages between forwards request and returns messages`() {
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage)),
        )
        val adapter = FindStoredMessagesBetweenInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindMessagesBetweenRequest(
                peerId = 1,
                firstMessageId = 2,
                lastMessageId = 3,
                limit = 4,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindMessagesBetweenRequest(1, 2, 3, 4),
            port.requests.single(),
        )
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun `find last stored summary returns summary`() {
        val summary = FakeModelProvider.createSummary(
            peerId = 1,
            firstMessageId = 2,
            lastMessageId = 3,
            shortSummary = "short",
            fullSummary = "full",
        )
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createOptionalSummaryResponse(summary),
        )
        val adapter = FindLastStoredSummaryInProcessAdapter(port)

        val response = adapter.execute(FakePersistencePortProvider.createFindLastSummaryRequest(peerId = 1))

        assertEquals(PersistenceDataRequestProvider.createFindLastSummaryRequest(1), port.requests.single())
        assertEquals(summary, response.summary)
    }

    @Test
    fun `has stored pending summary returns status`() {
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createPendingStatusResponse(hasPending = true),
        )
        val adapter = HasStoredPendingSummaryInProcessAdapter(port)

        val response = adapter.execute(FakePersistencePortProvider.createHasPendingSummaryRequest(peerId = 1))

        assertEquals(PersistenceDataRequestProvider.createHasPendingSummaryRequest(1), port.requests.single())
        assertEquals(true, response.hasPending)
    }

    @Test
    fun `create stored pending summary returns id`() {
        val summaryId = UUID.randomUUID()
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createCreatedSummaryIdResponse(summaryId),
        )
        val adapter = CreateStoredPendingSummaryInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createCreatePendingSummaryRequest(
                peerId = 1,
                firstMessageId = 2,
                lastMessageId = 3,
            ),
        )

        assertEquals(PersistenceDataRequestProvider.createCreatePendingSummaryRequest(1, 2, 3), port.requests.single())
        assertEquals(summaryId, response.summaryId)
    }

    @Test
    fun `save stored completed summary forwards summaries`() {
        val summaryId = UUID.randomUUID()
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponseProvider.createEmptyResponse())
        val adapter = SaveStoredCompletedSummaryInProcessAdapter(port)

        adapter.execute(
            FakePersistencePortProvider.createSaveCompletedSummaryRequest(summaryId, "short", "full"),
        )

        assertEquals(
            PersistenceDataRequestProvider.createSaveCompletedSummaryRequest(summaryId, "short", "full"),
            port.requests.single(),
        )
    }

    @Test
    fun `mark stored summary failed forwards id`() {
        val summaryId = UUID.randomUUID()
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponseProvider.createEmptyResponse())
        val adapter = MarkStoredSummaryFailedInProcessAdapter(port)

        adapter.execute(FakePersistencePortProvider.createMarkSummaryFailedRequest(summaryId))

        assertEquals(
            PersistenceDataRequestProvider.createMarkSummaryFailedRequest(summaryId),
            port.requests.single(),
        )
    }

    @Test
    fun `find stored user profiles by ids maps ids and returns profiles`() {
        val profile = FakeModelProvider.createVkUserProfile(id = 5, firstName = "Ivan", lastName = "Petrov")
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createUserProfilesResponse(listOf(profile)),
        )
        val adapter = FindStoredUserProfilesByIdsInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindUserProfilesByIdsRequest(
                listOf(FakeVoProvider.createFromId(5), FakeVoProvider.createFromId(6)),
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserProfilesByIdsRequest(listOf(5, 6)),
            port.requests.single(),
        )
        assertEquals(listOf(profile), response.profiles)
    }

    @Test
    fun `find stored group profiles by ids maps ids and returns profiles`() {
        val profile = FakeModelProvider.createVkGroupProfile(id = 7, name = "Group")
        val port = RecordingPersistenceDataInputPort(
            PersistenceDataResponseProvider.createGroupProfilesResponse(listOf(profile)),
        )
        val adapter = FindStoredGroupProfilesByIdsInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindGroupProfilesByIdsRequest(listOf(FakeVoProvider.createFromId(7))),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindGroupProfilesByIdsRequest(listOf(7)),
            port.requests.single(),
        )
        assertEquals(listOf(profile), response.profiles)
    }

    private class RecordingPersistenceDataInputPort(
        private val response: PersistenceDataResponse,
    ) : PersistenceDataInputPort {
        val requests = mutableListOf<PersistenceDataRequest>()

        override fun execute(request: PersistenceDataRequest): PersistenceDataResponse {
            requests.add(request)
            return response
        }
    }
}
