package com.simarel.vkbot.persistence.adapter.input.inprocess

import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class PersistenceDataInProcessAdapterTest {

    private val storedMessage = StoredMessage(
        id = UUID.randomUUID(),
        peerId = 10,
        conversationMessageId = 20,
        fromId = 30,
        messageText = "text",
        date = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        forwardedMessages = null,
    )

    @Test
    fun `find messages before forwards request and returns messages`() {
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.Messages(listOf(storedMessage)))
        val adapter = FindStoredMessagesBeforeInProcessAdapter(port)

        val response = adapter.execute(
            FindStoredMessagesBeforePort.Request(peerId = 1, beforeConversationMessageId = 2, limit = 3),
        )

        assertEquals(PersistenceDataRequest.FindMessagesBefore(1, 2, 3), port.requests.single())
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun `find messages between forwards request and returns messages`() {
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.Messages(listOf(storedMessage)))
        val adapter = FindStoredMessagesBetweenInProcessAdapter(port)

        val response = adapter.execute(
            FindStoredMessagesBetweenPort.Request(peerId = 1, firstMessageId = 2, lastMessageId = 3, limit = 4),
        )

        assertEquals(PersistenceDataRequest.FindMessagesBetween(1, 2, 3, 4), port.requests.single())
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun `find last stored summary returns summary`() {
        val summary = Summary(
            id = UUID.randomUUID(),
            peerId = 1,
            firstMessageId = 2,
            lastMessageId = 3,
            shortSummary = "short",
            fullSummary = "full",
            status = SummaryStatus.COMPLETED,
            createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        )
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.OptionalSummary(summary))
        val adapter = FindLastStoredSummaryInProcessAdapter(port)

        val response = adapter.execute(FindLastStoredSummaryPort.Request(peerId = 1))

        assertEquals(PersistenceDataRequest.FindLastSummary(1), port.requests.single())
        assertEquals(summary, response.summary)
    }

    @Test
    fun `has stored pending summary returns status`() {
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.PendingStatus(true))
        val adapter = HasStoredPendingSummaryInProcessAdapter(port)

        val response = adapter.execute(HasStoredPendingSummaryPort.Request(peerId = 1))

        assertEquals(PersistenceDataRequest.HasPendingSummary(1), port.requests.single())
        assertEquals(true, response.hasPending)
    }

    @Test
    fun `create stored pending summary returns id`() {
        val summaryId = UUID.randomUUID()
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.CreatedSummaryId(summaryId))
        val adapter = CreateStoredPendingSummaryInProcessAdapter(port)

        val response = adapter.execute(
            CreateStoredPendingSummaryPort.Request(peerId = 1, firstMessageId = 2, lastMessageId = 3),
        )

        assertEquals(PersistenceDataRequest.CreatePendingSummary(1, 2, 3), port.requests.single())
        assertEquals(summaryId, response.summaryId)
    }

    @Test
    fun `save stored completed summary forwards summaries`() {
        val summaryId = UUID.randomUUID()
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.Empty)
        val adapter = SaveStoredCompletedSummaryInProcessAdapter(port)

        adapter.execute(SaveStoredCompletedSummaryPort.Request(summaryId, "short", "full"))

        assertEquals(
            PersistenceDataRequest.SaveCompletedSummary(summaryId, "short", "full"),
            port.requests.single(),
        )
    }

    @Test
    fun `mark stored summary failed forwards id`() {
        val summaryId = UUID.randomUUID()
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.Empty)
        val adapter = MarkStoredSummaryFailedInProcessAdapter(port)

        adapter.execute(MarkStoredSummaryFailedPort.Request(summaryId))

        assertEquals(PersistenceDataRequest.MarkSummaryAsFailed(summaryId), port.requests.single())
    }

    @Test
    fun `find stored user profiles by ids maps ids and returns profiles`() {
        val profile = VkUserProfile.of(id = 5, firstName = "Ivan", lastName = "Petrov")
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.UserProfiles(listOf(profile)))
        val adapter = FindStoredUserProfilesByIdsInProcessAdapter(port)

        val response = adapter.execute(
            FindStoredUserProfilesByIdsPort.Request(listOf(FromId.of(5), FromId.of(6))),
        )

        assertEquals(PersistenceDataRequest.FindUserProfilesByIds(listOf(5, 6)), port.requests.single())
        assertEquals(listOf(profile), response.profiles)
    }

    @Test
    fun `find stored group profiles by ids maps ids and returns profiles`() {
        val profile = VkGroupProfile.of(id = 7, name = "Group")
        val port = RecordingPersistenceDataInputPort(PersistenceDataResponse.GroupProfiles(listOf(profile)))
        val adapter = FindStoredGroupProfilesByIdsInProcessAdapter(port)

        val response = adapter.execute(
            FindStoredGroupProfilesByIdsPort.Request(listOf(FromId.of(7))),
        )

        assertEquals(PersistenceDataRequest.FindGroupProfilesByIds(listOf(7)), port.requests.single())
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
