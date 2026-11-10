package com.simarel.vkbot.persistence.adapter.input.inprocess

import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.persistence.port.input.PersistenceDataResponse
import com.simarel.vkbot.persistence.testfixtures.LongPollTsPortProvider
import com.simarel.vkbot.persistence.testfixtures.PersistenceDataRequestProvider
import com.simarel.vkbot.persistence.testfixtures.PersistenceDataResponseProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findMessagesBefore(
                request: PersistenceDataRequest.FindMessagesBefore,
            ): PersistenceDataResponse.Messages {
                requests.add(request)
                return PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage))
            }
        }
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findMessagesBetween(
                request: PersistenceDataRequest.FindMessagesBetween,
            ): PersistenceDataResponse.Messages {
                requests.add(request)
                return PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage))
            }
        }
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
    fun `find last stored message by author forwards request and returns message`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findLastMessageByAuthor(
                request: PersistenceDataRequest.FindLastMessageByAuthor,
            ): PersistenceDataResponse.OptionalMessage {
                requests.add(request)
                return PersistenceDataResponseProvider.createOptionalMessageResponse(storedMessage)
            }
        }
        val adapter = FindLastStoredMessageByAuthorInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindLastStoredMessageByAuthorRequest(
                peerId = 10,
                fromId = 30,
                beforeConversationMessageId = 20,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindLastMessageByAuthorRequest(10, 30, 20),
            port.requests.single(),
        )
        assertEquals(storedMessage, response.message)
    }

    @Test
    fun `find stored messages between dates forwards request and returns messages`() {
        val from = OffsetDateTime.parse("2026-11-02T00:00:00Z")
        val to = OffsetDateTime.parse("2026-11-03T23:59:59.999999999Z")
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findMessagesBetweenDates(
                request: PersistenceDataRequest.FindMessagesBetweenDates,
            ): PersistenceDataResponse.Messages {
                requests.add(request)
                return PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage))
            }
        }
        val adapter = FindStoredMessagesBetweenDatesInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindStoredMessagesBetweenDatesRequest(
                peerId = 10,
                from = from,
                to = to,
                limit = 5,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindMessagesBetweenDatesRequest(10, from, to, 5),
            port.requests.single(),
        )
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun `find stored summaries in period forwards request and returns summaries`() {
        val from = OffsetDateTime.parse("2026-11-02T00:00:00Z")
        val to = OffsetDateTime.parse("2026-11-03T23:59:59.999999999Z")
        val summary = FakeModelProvider.createSummary(peerId = 10, firstMessageId = 20, lastMessageId = 30)
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findSummariesInPeriod(
                request: PersistenceDataRequest.FindSummariesInPeriod,
            ): PersistenceDataResponse.Summaries {
                requests.add(request)
                return PersistenceDataResponseProvider.createSummariesResponse(listOf(summary))
            }
        }
        val adapter = FindStoredSummariesInPeriodInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindStoredSummariesInPeriodRequest(
                peerId = 10,
                from = from,
                to = to,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindSummariesInPeriodRequest(10, from, to),
            port.requests.single(),
        )
        assertEquals(listOf(summary), response.summaries)
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findLastSummary(
                request: PersistenceDataRequest.FindLastSummary,
            ): PersistenceDataResponse.OptionalSummary {
                requests.add(request)
                return PersistenceDataResponseProvider.createOptionalSummaryResponse(summary)
            }
        }
        val adapter = FindLastStoredSummaryInProcessAdapter(port)

        val response = adapter.execute(FakePersistencePortProvider.createFindLastSummaryRequest(peerId = 1))

        assertEquals(PersistenceDataRequestProvider.createFindLastSummaryRequest(1), port.requests.single())
        assertEquals(summary, response.summary)
    }

    @Test
    fun `has stored pending summary returns status`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun hasPendingSummary(
                request: PersistenceDataRequest.HasPendingSummary,
            ): PersistenceDataResponse.PendingStatus {
                requests.add(request)
                return PersistenceDataResponseProvider.createPendingStatusResponse(hasPending = true)
            }
        }
        val adapter = HasStoredPendingSummaryInProcessAdapter(port)

        val response = adapter.execute(FakePersistencePortProvider.createHasPendingSummaryRequest(peerId = 1))

        assertEquals(PersistenceDataRequestProvider.createHasPendingSummaryRequest(1), port.requests.single())
        assertEquals(true, response.hasPending)
    }

    @Test
    fun `create stored pending summary returns id`() {
        val summaryId = UUID.randomUUID()
        val port = object : RecordingPersistenceDataInputPort() {
            override fun createPendingSummary(
                request: PersistenceDataRequest.CreatePendingSummary,
            ): PersistenceDataResponse.CreatedSummaryId {
                requests.add(request)
                return PersistenceDataResponseProvider.createCreatedSummaryIdResponse(summaryId)
            }
        }
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun saveCompletedSummary(
                request: PersistenceDataRequest.SaveCompletedSummary,
            ): PersistenceDataResponse.Empty {
                requests.add(request)
                return PersistenceDataResponseProvider.createEmptyResponse()
            }
        }
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun markSummaryAsFailed(
                request: PersistenceDataRequest.MarkSummaryAsFailed,
            ): PersistenceDataResponse.Empty {
                requests.add(request)
                return PersistenceDataResponseProvider.createEmptyResponse()
            }
        }
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserProfilesByIds(
                request: PersistenceDataRequest.FindUserProfilesByIds,
            ): PersistenceDataResponse.UserProfiles {
                requests.add(request)
                return PersistenceDataResponseProvider.createUserProfilesResponse(listOf(profile))
            }
        }
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
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findGroupProfilesByIds(
                request: PersistenceDataRequest.FindGroupProfilesByIds,
            ): PersistenceDataResponse.GroupProfiles {
                requests.add(request)
                return PersistenceDataResponseProvider.createGroupProfilesResponse(listOf(profile))
            }
        }
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

    @Test
    fun `find long poll ts returns ts`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findLongPollTs(
                request: PersistenceDataRequest.FindLongPollTs,
            ): PersistenceDataResponse.LongPollTs {
                requests.add(request)
                return PersistenceDataResponseProvider.createLongPollTsResponse(ts = "ts-1")
            }
        }
        val adapter = FindLongPollTsInProcessAdapter(port)

        val response = adapter.execute(LongPollTsPortProvider.createFindLongPollTsRequest())

        assertEquals(PersistenceDataRequestProvider.createFindLongPollTsRequest(), port.requests.single())
        assertEquals("ts-1", response.ts?.value)
    }

    @Test
    fun `save long poll ts forwards ts`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun saveLongPollTs(
                request: PersistenceDataRequest.SaveLongPollTs,
            ): PersistenceDataResponse.Empty {
                requests.add(request)
                return PersistenceDataResponseProvider.createEmptyResponse()
            }
        }
        val adapter = SaveLongPollTsInProcessAdapter(port)

        adapter.execute(LongPollTsPortProvider.createSaveLongPollTsRequest(ts = "ts-2"))

        assertEquals(
            PersistenceDataRequestProvider.createSaveLongPollTsRequest(ts = "ts-2"),
            port.requests.single(),
        )
    }

    @Test
    fun `find user profiles by birth date forwards day and month and returns profiles`() {
        val profile = FakeModelProvider.createVkUserProfile(id = 5, firstName = "Ivan", lastName = "Petrov")
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserProfilesByBirthDate(
                request: PersistenceDataRequest.FindUserProfilesByBirthDate,
            ): PersistenceDataResponse.UserProfiles {
                requests.add(request)
                return PersistenceDataResponseProvider.createUserProfilesResponse(listOf(profile))
            }
        }
        val adapter = FindUserProfilesByBirthDateInProcessAdapter(port)

        val response = adapter.execute(
            FakeGreetingPortProvider.createFindUserProfilesByBirthDateRequest(day = 12, month = 9),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserProfilesByBirthDateRequest(day = 12, month = 9),
            port.requests.single(),
        )
        assertEquals(listOf(profile), response.profiles)
    }

    @Test
    fun `find user chat peer ids maps the user id and returns peer ids`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserChatPeerIds(
                request: PersistenceDataRequest.FindUserChatPeerIds,
            ): PersistenceDataResponse.PeerIds {
                requests.add(request)
                return PersistenceDataResponseProvider.createPeerIdsResponse(setOf(2_000_000_001, 1_234_567))
            }
        }
        val adapter = FindUserChatPeerIdsInProcessAdapter(port)

        val response = adapter.execute(
            FakeGreetingPortProvider.createFindUserChatPeerIdsRequest(FakeVoProvider.createFromId(7)),
        )

        assertEquals(PersistenceDataRequestProvider.createFindUserChatPeerIdsRequest(7), port.requests.single())
        assertEquals(
            setOf(FakeVoProvider.createPeerId(2_000_000_001), FakeVoProvider.createPeerId(1_234_567)),
            response.peerIds,
        )
    }

    @Test
    fun `find group chat peer ids returns group chat peer ids`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findGroupChatPeerIds(
                request: PersistenceDataRequest.FindGroupChatPeerIds,
            ): PersistenceDataResponse.PeerIds {
                requests.add(request)
                return PersistenceDataResponseProvider.createPeerIdsResponse(setOf(2_000_000_001))
            }
        }
        val adapter = FindGroupChatPeerIdsInProcessAdapter(port)

        val response = adapter.execute(FakeGreetingPortProvider.createFindGroupChatPeerIdsRequest())

        assertEquals(PersistenceDataRequestProvider.createFindGroupChatPeerIdsRequest(), port.requests.single())
        assertEquals(setOf(FakeVoProvider.createPeerId(2_000_000_001)), response.peerIds)
    }

    @Test
    fun `claim daily greeting forwards date and peer id and returns claimed`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun claimDailyGreeting(
                request: PersistenceDataRequest.ClaimDailyGreeting,
            ): PersistenceDataResponse.Claimed {
                requests.add(request)
                return PersistenceDataResponseProvider.createClaimedResponse(claimed = true)
            }
        }
        val adapter = ClaimDailyGreetingInProcessAdapter(port)

        val response = adapter.execute(FakeGreetingPortProvider.createClaimDailyGreetingRequest())

        assertEquals(
            PersistenceDataRequestProvider.createClaimDailyGreetingRequest(
                date = LocalDate.parse("2026-09-13"),
                peerId = 2_000_000_001,
            ),
            port.requests.single(),
        )
        assertEquals(true, response.claimed)
    }

    private open class RecordingPersistenceDataInputPort : PersistenceDataInputPort {
        val requests = mutableListOf<PersistenceDataRequest>()

        override fun findMessagesBefore(
            request: PersistenceDataRequest.FindMessagesBefore,
        ): PersistenceDataResponse.Messages = unsupported()

        override fun findMessagesBetween(
            request: PersistenceDataRequest.FindMessagesBetween,
        ): PersistenceDataResponse.Messages = unsupported()

        override fun findLastMessageByAuthor(
            request: PersistenceDataRequest.FindLastMessageByAuthor,
        ): PersistenceDataResponse.OptionalMessage = unsupported()

        override fun findMessagesBetweenDates(
            request: PersistenceDataRequest.FindMessagesBetweenDates,
        ): PersistenceDataResponse.Messages = unsupported()

        override fun findSummariesInPeriod(
            request: PersistenceDataRequest.FindSummariesInPeriod,
        ): PersistenceDataResponse.Summaries = unsupported()

        override fun findLastSummary(
            request: PersistenceDataRequest.FindLastSummary,
        ): PersistenceDataResponse.OptionalSummary = unsupported()

        override fun hasPendingSummary(
            request: PersistenceDataRequest.HasPendingSummary,
        ): PersistenceDataResponse.PendingStatus = unsupported()

        override fun createPendingSummary(
            request: PersistenceDataRequest.CreatePendingSummary,
        ): PersistenceDataResponse.CreatedSummaryId = unsupported()

        override fun saveCompletedSummary(
            request: PersistenceDataRequest.SaveCompletedSummary,
        ): PersistenceDataResponse.Empty = unsupported()

        override fun markSummaryAsFailed(
            request: PersistenceDataRequest.MarkSummaryAsFailed,
        ): PersistenceDataResponse.Empty = unsupported()

        override fun findUserProfilesByIds(
            request: PersistenceDataRequest.FindUserProfilesByIds,
        ): PersistenceDataResponse.UserProfiles = unsupported()

        override fun findGroupProfilesByIds(
            request: PersistenceDataRequest.FindGroupProfilesByIds,
        ): PersistenceDataResponse.GroupProfiles = unsupported()

        override fun findLongPollTs(
            request: PersistenceDataRequest.FindLongPollTs,
        ): PersistenceDataResponse.LongPollTs = unsupported()

        override fun saveLongPollTs(
            request: PersistenceDataRequest.SaveLongPollTs,
        ): PersistenceDataResponse.Empty = unsupported()

        override fun findUserProfilesByBirthDate(
            request: PersistenceDataRequest.FindUserProfilesByBirthDate,
        ): PersistenceDataResponse.UserProfiles = unsupported()

        override fun findUserChatPeerIds(
            request: PersistenceDataRequest.FindUserChatPeerIds,
        ): PersistenceDataResponse.PeerIds = unsupported()

        override fun findGroupChatPeerIds(
            request: PersistenceDataRequest.FindGroupChatPeerIds,
        ): PersistenceDataResponse.PeerIds = unsupported()

        override fun claimDailyGreeting(
            request: PersistenceDataRequest.ClaimDailyGreeting,
        ): PersistenceDataResponse.Claimed = unsupported()

        override fun findUserProfileHistory(
            request: PersistenceDataRequest.FindUserProfileHistory,
        ): PersistenceDataResponse.ProfileHistory = unsupported()

        override fun findUserStatistics(
            request: PersistenceDataRequest.FindUserStatistics,
        ): PersistenceDataResponse.Statistics = unsupported()

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
