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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.MonthDay
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

    @Test
    fun `chat authors adapter delegates the peer id`() {
        val authors = listOf(FakeModelProvider.createChatAuthor(fromId = 7_600_001, messageCount = 2))
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findChatAuthors(
                request: PersistenceDataRequest.FindChatAuthors,
            ): PersistenceDataResponse.ChatAuthors {
                requests.add(request)
                return PersistenceDataResponseProvider.createChatAuthorsResponse(authors)
            }
        }
        val adapter = FindChatAuthorsInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindChatAuthorsRequest(peerId = 2_600_000_001),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindChatAuthorsRequest(peerId = 2_600_000_001),
            port.requests.single(),
        )
        assertEquals(authors, response.authors)
    }

    @Test
    fun `statistics adapter delegates the user id`() {
        val statistics = FakeModelProvider.createUserStatistics(userId = 7_600_002, messageCount = 3)
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserStatistics(
                request: PersistenceDataRequest.FindUserStatistics,
            ): PersistenceDataResponse.Statistics {
                requests.add(request)
                return PersistenceDataResponseProvider.createStatisticsResponse(statistics)
            }
        }
        val adapter = FindStoredUserStatisticsInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindStoredUserStatisticsRequest(userId = 7_600_002),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserStatisticsRequest(userId = 7_600_002),
            port.requests.single(),
        )
        assertEquals(statistics, response.statistics)
    }

    @Test
    fun `history adapter delegates id and limit`() {
        val entries = listOf(FakeModelProvider.createProfileHistoryEntry(profileId = 7_600_003))
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserProfileHistory(
                request: PersistenceDataRequest.FindUserProfileHistory,
            ): PersistenceDataResponse.ProfileHistory {
                requests.add(request)
                return PersistenceDataResponseProvider.createProfileHistoryResponse(entries)
            }
        }
        val adapter = FindStoredUserProfileHistoryInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindStoredUserProfileHistoryRequest(
                profileId = 7_600_003,
                limit = 20,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserProfileHistoryRequest(
                profileId = 7_600_003,
                limit = 20,
                offset = 0,
            ),
            port.requests.single(),
        )
        assertEquals(entries, response.entries)
    }

    @Test
    fun `has pending knowledge batch adapter delegates thresholds`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun hasPendingKnowledgeBatch(
                request: PersistenceDataRequest.HasPendingKnowledgeBatch,
            ): PersistenceDataResponse.PendingStatus {
                requests.add(request)
                return PersistenceDataResponseProvider.createPendingStatusResponse(hasPending = true)
            }
        }
        val adapter = HasPendingKnowledgeBatchInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createHasPendingKnowledgeBatchRequest(
                peerId = 10,
                minCount = 25,
                minInterval = Duration.ofMinutes(30),
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createHasPendingKnowledgeBatchRequest(10, 25, Duration.ofMinutes(30)),
            port.requests.single(),
        )
        assertEquals(true, response.hasPending)
    }

    @Test
    fun `find pending knowledge batch adapter delegates limit and returns messages`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findPendingKnowledgeBatch(
                request: PersistenceDataRequest.FindPendingKnowledgeBatch,
            ): PersistenceDataResponse.Messages {
                requests.add(request)
                return PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage))
            }
        }
        val adapter = FindPendingKnowledgeBatchInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindPendingKnowledgeBatchRequest(
                peerId = 10,
                upToConversationMessageId = 30,
                limit = 25,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindPendingKnowledgeBatchRequest(10, 30, 25),
            port.requests.single(),
        )
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun `save user facts adapter delegates candidates and returns counts`() {
        val candidate = FakeModelProvider.createUserFactCandidate()
        val port = object : RecordingPersistenceDataInputPort() {
            override fun saveUserFacts(
                request: PersistenceDataRequest.SaveUserFacts,
            ): PersistenceDataResponse.SavedUserFacts {
                requests.add(request)
                return PersistenceDataResponseProvider.createSavedUserFactsResponse(saved = 1, skipped = 0)
            }
        }
        val adapter = SaveUserFactsInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createSaveUserFactsRequest(candidates = listOf(candidate)),
        )

        assertEquals(
            PersistenceDataRequestProvider.createSaveUserFactsRequest(candidates = listOf(candidate)),
            port.requests.single(),
        )
        assertEquals(1, response.saved)
        assertEquals(0, response.skipped)
    }

    @Test
    fun `advance extraction cursor adapter delegates cursor values`() {
        val completedAt = OffsetDateTime.parse("2026-11-01T12:00:00Z")
        val port = object : RecordingPersistenceDataInputPort() {
            override fun advanceKnowledgeExtractionCursor(
                request: PersistenceDataRequest.AdvanceKnowledgeExtractionCursor,
            ): PersistenceDataResponse.Empty {
                requests.add(request)
                return PersistenceDataResponseProvider.createEmptyResponse()
            }
        }
        val adapter = AdvanceExtractionCursorInProcessAdapter(port)

        adapter.execute(
            FakePersistencePortProvider.createAdvanceExtractionCursorRequest(
                peerId = 10,
                lastConversationMessageId = 30,
                completedAt = completedAt,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createAdvanceKnowledgeExtractionCursorRequest(10, 30, completedAt),
            port.requests.single(),
        )
    }

    @Test
    fun `save user knowledge summary adapter delegates text`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun saveUserKnowledgeSummary(
                request: PersistenceDataRequest.SaveUserKnowledgeSummary,
            ): PersistenceDataResponse.Empty {
                requests.add(request)
                return PersistenceDataResponseProvider.createEmptyResponse()
            }
        }
        val adapter = SaveUserKnowledgeSummaryInProcessAdapter(port)

        adapter.execute(
            FakePersistencePortProvider.createSaveUserKnowledgeSummaryRequest(userId = 10, summaryText = "summary"),
        )

        assertEquals(
            PersistenceDataRequestProvider.createSaveUserKnowledgeSummaryRequest(10, "summary"),
            port.requests.single(),
        )
    }

    @Test
    fun `save chat knowledge summary adapter delegates covered messages`() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun saveChatKnowledgeSummary(
                request: PersistenceDataRequest.SaveChatKnowledgeSummary,
            ): PersistenceDataResponse.Empty {
                requests.add(request)
                return PersistenceDataResponseProvider.createEmptyResponse()
            }
        }
        val adapter = SaveChatKnowledgeSummaryInProcessAdapter(port)

        adapter.execute(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = 10,
                summaryText = "chat summary",
                firstConversationMessageId = 10,
                lastConversationMessageId = 70,
                messagesCovered = 7,
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createSaveChatKnowledgeSummaryRequest(10, "chat summary", 10, 70, 7),
            port.requests.single(),
        )
    }

    @Test
    fun `find chat knowledge summary forwards request and returns knowledge`() {
        val knowledge = FakeModelProvider.createChatKnowledge(peerId = 2_000_000_001)
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findChatKnowledgeSummary(
                request: PersistenceDataRequest.FindChatKnowledgeSummary,
            ): PersistenceDataResponse.ChatKnowledgeSummary {
                requests.add(request)
                return PersistenceDataResponseProvider.createChatKnowledgeSummaryResponse(knowledge = knowledge)
            }
        }
        val adapter = FindChatKnowledgeSummaryInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindChatKnowledgeSummaryRequest(peerId = 2_000_000_001),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindChatKnowledgeSummaryRequest(peerId = 2_000_000_001),
            port.requests.single(),
        )
        assertEquals(knowledge, response.knowledge)
    }

    @Test
    fun `find chat summary messages forwards request and returns messages`() {
        val storedMessage = FakeModelProvider.createStoredMessage(conversationMessageId = 101)
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findChatSummaryMessages(
                request: PersistenceDataRequest.FindChatSummaryMessages,
            ): PersistenceDataResponse.Messages {
                requests.add(request)
                return PersistenceDataResponseProvider.createMessagesResponse(listOf(storedMessage))
            }
        }
        val adapter = FindChatSummaryMessagesInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindChatSummaryMessagesRequest(peerId = 2_000_000_001, limit = 100),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindChatSummaryMessagesRequest(peerId = 2_000_000_001, limit = 100),
            port.requests.single(),
        )
        assertEquals(listOf(storedMessage), response.messages)
    }

    @Test
    fun findUserKnowledgeWithEmptyIdsReturnsEmptyMapWithoutCallingPort() {
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserKnowledge(
                request: PersistenceDataRequest.FindUserKnowledge,
            ): PersistenceDataResponse.UserKnowledgeByUserIds {
                requests.add(request)
                return PersistenceDataResponseProvider.createUserKnowledgeByUserIdsResponse()
            }
        }
        val adapter = FindUserKnowledgeInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindUserKnowledgeRequest(userIds = emptyList()),
        )

        assertTrue(response.knowledge.isEmpty())
        assertTrue(port.requests.isEmpty())
    }

    @Test
    fun findUserKnowledgeMapsFromIdsBothWays() {
        val first = FakeModelProvider.createUserKnowledge(
            facts = listOf(FakeModelProvider.createUserFact(userId = 5)),
            summary = "сводка",
            summaryFresh = true,
        )
        val second = FakeModelProvider.createUserKnowledge()
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserKnowledge(
                request: PersistenceDataRequest.FindUserKnowledge,
            ): PersistenceDataResponse.UserKnowledgeByUserIds {
                requests.add(request)
                return PersistenceDataResponseProvider.createUserKnowledgeByUserIdsResponse(
                    knowledge = mapOf(5L to first, 6L to second),
                )
            }
        }
        val adapter = FindUserKnowledgeInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindUserKnowledgeRequest(
                userIds = listOf(FakeVoProvider.createFromId(5), FakeVoProvider.createFromId(6)),
            ),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserKnowledgeRequest(userIds = listOf(5, 6)),
            port.requests.single(),
        )
        assertEquals(setOf(FakeVoProvider.createFromId(5), FakeVoProvider.createFromId(6)), response.knowledge.keys)
        assertEquals(first, response.knowledge[FakeVoProvider.createFromId(5)])
        assertEquals(second, response.knowledge[FakeVoProvider.createFromId(6)])
    }

    @Test
    fun findUserBirthdayFactsDelegatesMonthDayAndReturnsFacts() {
        val fact = FakeModelProvider.createUserFact(userId = 5)
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserBirthdayFacts(
                request: PersistenceDataRequest.FindUserBirthdayFacts,
            ): PersistenceDataResponse.UserFacts {
                requests.add(request)
                return PersistenceDataResponseProvider.createUserFactsResponse(facts = listOf(fact))
            }
        }
        val adapter = FindUserBirthdayFactsInProcessAdapter(port)

        val response = adapter.execute(
            FakeGreetingPortProvider.createFindUserBirthdayFactsRequest(monthDay = MonthDay.of(5, 14)),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserBirthdayFactsRequest(monthDay = MonthDay.of(5, 14)),
            port.requests.single(),
        )
        assertEquals(listOf(fact), response.facts)
    }

    @Test
    fun findUserKnowledgeSummaryStateDelegatesUserIdAndReturnsState() {
        val summarizedAt = OffsetDateTime.parse("2026-10-01T00:00:00Z")
        val port = object : RecordingPersistenceDataInputPort() {
            override fun findUserKnowledgeSummaryState(
                request: PersistenceDataRequest.FindUserKnowledgeSummaryState,
            ): PersistenceDataResponse.UserKnowledgeSummaryState {
                requests.add(request)
                return PersistenceDataResponseProvider.createUserKnowledgeSummaryStateResponse(
                    summarizedAt = summarizedAt,
                    factsSinceSummary = 3,
                )
            }
        }
        val adapter = FindUserKnowledgeSummaryStateInProcessAdapter(port)

        val response = adapter.execute(
            FakePersistencePortProvider.createFindUserKnowledgeSummaryStateRequest(userId = 42),
        )

        assertEquals(
            PersistenceDataRequestProvider.createFindUserKnowledgeSummaryStateRequest(userId = 42),
            port.requests.single(),
        )
        assertEquals(summarizedAt, response.summarizedAt)
        assertEquals(3, response.factsSinceSummary)
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

        override fun findChatAuthors(
            request: PersistenceDataRequest.FindChatAuthors,
        ): PersistenceDataResponse.ChatAuthors = unsupported()

        override fun hasPendingKnowledgeBatch(
            request: PersistenceDataRequest.HasPendingKnowledgeBatch,
        ): PersistenceDataResponse.PendingStatus = unsupported()

        override fun findPendingKnowledgeBatch(
            request: PersistenceDataRequest.FindPendingKnowledgeBatch,
        ): PersistenceDataResponse.Messages = unsupported()

        override fun saveUserFacts(
            request: PersistenceDataRequest.SaveUserFacts,
        ): PersistenceDataResponse.SavedUserFacts = unsupported()

        override fun advanceKnowledgeExtractionCursor(
            request: PersistenceDataRequest.AdvanceKnowledgeExtractionCursor,
        ): PersistenceDataResponse.Empty = unsupported()

        override fun saveUserKnowledgeSummary(
            request: PersistenceDataRequest.SaveUserKnowledgeSummary,
        ): PersistenceDataResponse.Empty = unsupported()

        override fun saveChatKnowledgeSummary(
            request: PersistenceDataRequest.SaveChatKnowledgeSummary,
        ): PersistenceDataResponse.Empty = unsupported()

        override fun findChatKnowledgeSummary(
            request: PersistenceDataRequest.FindChatKnowledgeSummary,
        ): PersistenceDataResponse.ChatKnowledgeSummary = unsupported()

        override fun findChatSummaryMessages(
            request: PersistenceDataRequest.FindChatSummaryMessages,
        ): PersistenceDataResponse.Messages = unsupported()

        override fun findUserKnowledge(
            request: PersistenceDataRequest.FindUserKnowledge,
        ): PersistenceDataResponse.UserKnowledgeByUserIds = unsupported()

        override fun findUserKnowledgeSummaryState(
            request: PersistenceDataRequest.FindUserKnowledgeSummaryState,
        ): PersistenceDataResponse.UserKnowledgeSummaryState = unsupported()

        override fun findUserBirthdayFacts(
            request: PersistenceDataRequest.FindUserBirthdayFacts,
        ): PersistenceDataResponse.UserFacts = unsupported()

        private fun <T> unsupported(): T = throw UnsupportedOperationException()
    }
}
