package com.simarel.vkbot.persistence.adapter.input.inprocess

import com.simarel.vkbot.persistence.port.input.PersistenceDataInputPort
import com.simarel.vkbot.persistence.port.input.PersistenceDataRequest
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.LongPollTs
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import com.simarel.vkbot.share.port.output.persistence.ClaimDailyGreetingPort
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatSummaryMessagesPort
import com.simarel.vkbot.share.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredMessageByAuthorPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLongPollTsPort
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenDatesPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredSummariesInPeriodPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserBirthdayFactsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgeSummaryStatePort
import com.simarel.vkbot.share.port.output.persistence.FindUserProfilesByBirthDatePort
import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.HasStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveLongPollTsPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindStoredMessagesBeforeInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredMessagesBeforePort {
    override fun execute(request: FindStoredMessagesBeforePort.Request): FindStoredMessagesBeforePort.Response {
        val response = persistenceDataInputPort.findMessagesBefore(
            PersistenceDataRequest.FindMessagesBefore(
                request.peerId,
                request.beforeConversationMessageId,
                request.limit,
            ),
        )
        return FindStoredMessagesBeforePort.Response(response.messages)
    }
}

@ApplicationScoped
class FindStoredMessagesBetweenInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredMessagesBetweenPort {
    override fun execute(request: FindStoredMessagesBetweenPort.Request): FindStoredMessagesBetweenPort.Response {
        val response = persistenceDataInputPort.findMessagesBetween(
            PersistenceDataRequest.FindMessagesBetween(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId,
                request.limit,
            ),
        )
        return FindStoredMessagesBetweenPort.Response(response.messages)
    }
}

@ApplicationScoped
class FindLastStoredSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindLastStoredSummaryPort {
    override fun execute(request: FindLastStoredSummaryPort.Request): FindLastStoredSummaryPort.Response {
        val response = persistenceDataInputPort.findLastSummary(
            PersistenceDataRequest.FindLastSummary(request.peerId),
        )
        return FindLastStoredSummaryPort.Response(response.summary)
    }
}

@ApplicationScoped
class HasStoredPendingSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : HasStoredPendingSummaryPort {
    override fun execute(request: HasStoredPendingSummaryPort.Request): HasStoredPendingSummaryPort.Response {
        val response = persistenceDataInputPort.hasPendingSummary(
            PersistenceDataRequest.HasPendingSummary(request.peerId),
        )
        return HasStoredPendingSummaryPort.Response(response.hasPending)
    }
}

@ApplicationScoped
class CreateStoredPendingSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : CreateStoredPendingSummaryPort {
    override fun execute(request: CreateStoredPendingSummaryPort.Request): CreateStoredPendingSummaryPort.Response {
        val response = persistenceDataInputPort.createPendingSummary(
            PersistenceDataRequest.CreatePendingSummary(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId,
            ),
        )
        return CreateStoredPendingSummaryPort.Response(response.id)
    }
}

@ApplicationScoped
class SaveStoredCompletedSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : SaveStoredCompletedSummaryPort {
    override fun execute(request: SaveStoredCompletedSummaryPort.Request): SaveStoredCompletedSummaryPort.Response {
        persistenceDataInputPort.saveCompletedSummary(
            PersistenceDataRequest.SaveCompletedSummary(
                request.summaryId,
                request.shortSummary,
                request.fullSummary,
            ),
        )
        return SaveStoredCompletedSummaryPort.Response()
    }
}

@ApplicationScoped
class MarkStoredSummaryFailedInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : MarkStoredSummaryFailedPort {
    override fun execute(request: MarkStoredSummaryFailedPort.Request): MarkStoredSummaryFailedPort.Response {
        persistenceDataInputPort.markSummaryAsFailed(
            PersistenceDataRequest.MarkSummaryAsFailed(request.summaryId),
        )
        return MarkStoredSummaryFailedPort.Response()
    }
}

@ApplicationScoped
class FindStoredUserProfilesByIdsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredUserProfilesByIdsPort {
    override fun execute(request: FindStoredUserProfilesByIdsPort.Request): FindStoredUserProfilesByIdsPort.Response {
        val response = persistenceDataInputPort.findUserProfilesByIds(
            PersistenceDataRequest.FindUserProfilesByIds(request.ids.map { it.value }),
        )
        return FindStoredUserProfilesByIdsPort.Response(response.profiles)
    }
}

@ApplicationScoped
class FindStoredGroupProfilesByIdsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredGroupProfilesByIdsPort {
    override fun execute(
        request: FindStoredGroupProfilesByIdsPort.Request,
    ): FindStoredGroupProfilesByIdsPort.Response {
        val response = persistenceDataInputPort.findGroupProfilesByIds(
            PersistenceDataRequest.FindGroupProfilesByIds(request.ids.map { it.value }),
        )
        return FindStoredGroupProfilesByIdsPort.Response(response.profiles)
    }
}

@ApplicationScoped
class FindLongPollTsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindLongPollTsPort {
    override fun execute(request: FindLongPollTsPort.Request): FindLongPollTsPort.Response {
        val response = persistenceDataInputPort.findLongPollTs(PersistenceDataRequest.FindLongPollTs)
        return FindLongPollTsPort.Response(response.ts?.let { LongPollTs(it) })
    }
}

@ApplicationScoped
class SaveLongPollTsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : SaveLongPollTsPort {
    override fun execute(request: SaveLongPollTsPort.Request): SaveLongPollTsPort.Response {
        persistenceDataInputPort.saveLongPollTs(PersistenceDataRequest.SaveLongPollTs(request.ts.value))
        return SaveLongPollTsPort.Response
    }
}

@ApplicationScoped
class FindUserProfilesByBirthDateInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindUserProfilesByBirthDatePort {
    override fun execute(
        request: FindUserProfilesByBirthDatePort.Request,
    ): FindUserProfilesByBirthDatePort.Response {
        val response = persistenceDataInputPort.findUserProfilesByBirthDate(
            PersistenceDataRequest.FindUserProfilesByBirthDate(request.day, request.month),
        )
        return FindUserProfilesByBirthDatePort.Response(response.profiles)
    }
}

@ApplicationScoped
class FindUserChatPeerIdsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindUserChatPeerIdsPort {
    override fun execute(request: FindUserChatPeerIdsPort.Request): FindUserChatPeerIdsPort.Response {
        val response = persistenceDataInputPort.findUserChatPeerIds(
            PersistenceDataRequest.FindUserChatPeerIds(request.userId.value),
        )
        val peerIds = response.peerIds.map { PeerId.of(it) }.toSet()
        return FindUserChatPeerIdsPort.Response(peerIds)
    }
}

@ApplicationScoped
class FindGroupChatPeerIdsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindGroupChatPeerIdsPort {
    override fun execute(request: FindGroupChatPeerIdsPort.Request): FindGroupChatPeerIdsPort.Response {
        val response = persistenceDataInputPort.findGroupChatPeerIds(PersistenceDataRequest.FindGroupChatPeerIds)
        val peerIds = response.peerIds.map { PeerId.of(it) }.toSet()
        return FindGroupChatPeerIdsPort.Response(peerIds)
    }
}

@ApplicationScoped
class FindLastStoredMessageByAuthorInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindLastStoredMessageByAuthorPort {
    override fun execute(
        request: FindLastStoredMessageByAuthorPort.Request,
    ): FindLastStoredMessageByAuthorPort.Response {
        val response = persistenceDataInputPort.findLastMessageByAuthor(
            PersistenceDataRequest.FindLastMessageByAuthor(
                request.peerId,
                request.fromId,
                request.beforeConversationMessageId,
            ),
        )
        return FindLastStoredMessageByAuthorPort.Response(response.message)
    }
}

@ApplicationScoped
class FindStoredMessagesBetweenDatesInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredMessagesBetweenDatesPort {
    override fun execute(
        request: FindStoredMessagesBetweenDatesPort.Request,
    ): FindStoredMessagesBetweenDatesPort.Response {
        val response = persistenceDataInputPort.findMessagesBetweenDates(
            PersistenceDataRequest.FindMessagesBetweenDates(
                request.peerId,
                request.from,
                request.to,
                request.limit,
            ),
        )
        return FindStoredMessagesBetweenDatesPort.Response(response.messages)
    }
}

@ApplicationScoped
class FindStoredSummariesInPeriodInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredSummariesInPeriodPort {
    override fun execute(
        request: FindStoredSummariesInPeriodPort.Request,
    ): FindStoredSummariesInPeriodPort.Response {
        val response = persistenceDataInputPort.findSummariesInPeriod(
            PersistenceDataRequest.FindSummariesInPeriod(
                request.peerId,
                request.from,
                request.to,
            ),
        )
        return FindStoredSummariesInPeriodPort.Response(response.summaries)
    }
}

@ApplicationScoped
class ClaimDailyGreetingInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : ClaimDailyGreetingPort {
    override fun execute(request: ClaimDailyGreetingPort.Request): ClaimDailyGreetingPort.Response {
        val response = persistenceDataInputPort.claimDailyGreeting(
            PersistenceDataRequest.ClaimDailyGreeting(request.date, request.peerId.value),
        )
        return ClaimDailyGreetingPort.Response(response.claimed)
    }
}

@ApplicationScoped
class FindChatAuthorsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindChatAuthorsPort {
    override fun execute(request: FindChatAuthorsPort.Request): FindChatAuthorsPort.Response {
        val response = persistenceDataInputPort.findChatAuthors(
            PersistenceDataRequest.FindChatAuthors(request.peerId.value),
        )
        return FindChatAuthorsPort.Response(response.authors)
    }
}

@ApplicationScoped
class FindStoredUserStatisticsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredUserStatisticsPort {
    override fun execute(request: FindStoredUserStatisticsPort.Request): FindStoredUserStatisticsPort.Response {
        val response = persistenceDataInputPort.findUserStatistics(
            PersistenceDataRequest.FindUserStatistics(request.userId.value),
        )
        return FindStoredUserStatisticsPort.Response(response.statistics)
    }
}

@ApplicationScoped
class FindStoredUserProfileHistoryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindStoredUserProfileHistoryPort {
    override fun execute(
        request: FindStoredUserProfileHistoryPort.Request,
    ): FindStoredUserProfileHistoryPort.Response {
        val response = persistenceDataInputPort.findUserProfileHistory(
            PersistenceDataRequest.FindUserProfileHistory(request.profileId.value, request.limit, 0),
        )
        return FindStoredUserProfileHistoryPort.Response(response.entries)
    }
}

@ApplicationScoped
class HasPendingKnowledgeBatchInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : HasPendingKnowledgeBatchPort {
    override fun execute(
        request: HasPendingKnowledgeBatchPort.Request,
    ): HasPendingKnowledgeBatchPort.Response {
        val response = persistenceDataInputPort.hasPendingKnowledgeBatch(
            PersistenceDataRequest.HasPendingKnowledgeBatch(
                request.peerId,
                request.minCount,
                request.minInterval,
            ),
        )
        return HasPendingKnowledgeBatchPort.Response(response.hasPending)
    }
}

@ApplicationScoped
class FindPendingKnowledgeBatchInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindPendingKnowledgeBatchPort {
    override fun execute(
        request: FindPendingKnowledgeBatchPort.Request,
    ): FindPendingKnowledgeBatchPort.Response {
        val response = persistenceDataInputPort.findPendingKnowledgeBatch(
            PersistenceDataRequest.FindPendingKnowledgeBatch(
                request.peerId,
                request.upToConversationMessageId,
                request.limit,
            ),
        )
        return FindPendingKnowledgeBatchPort.Response(response.messages)
    }
}

@ApplicationScoped
class SaveUserFactsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : SaveUserFactsPort {
    override fun execute(request: SaveUserFactsPort.Request): SaveUserFactsPort.Response {
        val response = persistenceDataInputPort.saveUserFacts(
            PersistenceDataRequest.SaveUserFacts(request.candidates),
        )
        return SaveUserFactsPort.Response(response.saved, response.skipped)
    }
}

@ApplicationScoped
class AdvanceExtractionCursorInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : AdvanceExtractionCursorPort {
    override fun execute(request: AdvanceExtractionCursorPort.Request): AdvanceExtractionCursorPort.Response {
        persistenceDataInputPort.advanceKnowledgeExtractionCursor(
            PersistenceDataRequest.AdvanceKnowledgeExtractionCursor(
                request.peerId,
                request.lastConversationMessageId,
                request.completedAt,
            ),
        )
        return AdvanceExtractionCursorPort.Response()
    }
}

@ApplicationScoped
class SaveUserKnowledgeSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : SaveUserKnowledgeSummaryPort {
    override fun execute(
        request: SaveUserKnowledgeSummaryPort.Request,
    ): SaveUserKnowledgeSummaryPort.Response {
        persistenceDataInputPort.saveUserKnowledgeSummary(
            PersistenceDataRequest.SaveUserKnowledgeSummary(request.userId, request.summaryText),
        )
        return SaveUserKnowledgeSummaryPort.Response()
    }
}

@ApplicationScoped
class SaveChatKnowledgeSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : SaveChatKnowledgeSummaryPort {
    override fun execute(request: SaveChatKnowledgeSummaryPort.Request): SaveChatKnowledgeSummaryPort.Response {
        persistenceDataInputPort.saveChatKnowledgeSummary(
            PersistenceDataRequest.SaveChatKnowledgeSummary(
                request.peerId,
                request.summaryText,
                request.firstConversationMessageId,
                request.lastConversationMessageId,
                request.messagesCovered,
            ),
        )
        return SaveChatKnowledgeSummaryPort.Response()
    }
}

@ApplicationScoped
class FindChatKnowledgeSummaryInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindChatKnowledgeSummaryPort {
    override fun execute(
        request: FindChatKnowledgeSummaryPort.Request,
    ): FindChatKnowledgeSummaryPort.Response = FindChatKnowledgeSummaryPort.Response(
        persistenceDataInputPort.findChatKnowledgeSummary(
            PersistenceDataRequest.FindChatKnowledgeSummary(request.peerId),
        ).knowledge,
    )
}

@ApplicationScoped
class FindChatSummaryMessagesInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindChatSummaryMessagesPort {
    override fun execute(
        request: FindChatSummaryMessagesPort.Request,
    ): FindChatSummaryMessagesPort.Response = FindChatSummaryMessagesPort.Response(
        persistenceDataInputPort.findChatSummaryMessages(
            PersistenceDataRequest.FindChatSummaryMessages(request.peerId, request.limit),
        ).messages,
    )
}

@ApplicationScoped
class FindUserKnowledgeInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindUserKnowledgePort {
    override fun execute(
        request: FindUserKnowledgePort.Request,
    ): FindUserKnowledgePort.Response {
        if (request.userIds.isEmpty()) {
            return FindUserKnowledgePort.Response(emptyMap())
        }
        val knowledge = persistenceDataInputPort.findUserKnowledge(
            PersistenceDataRequest.FindUserKnowledge(request.userIds.map { it.value }),
        ).knowledge
        return FindUserKnowledgePort.Response(knowledge.mapKeys { (userId, _) -> FromId(userId) })
    }
}

@ApplicationScoped
class FindUserBirthdayFactsInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindUserBirthdayFactsPort {
    override fun execute(
        request: FindUserBirthdayFactsPort.Request,
    ): FindUserBirthdayFactsPort.Response = FindUserBirthdayFactsPort.Response(
        persistenceDataInputPort.findUserBirthdayFacts(
            PersistenceDataRequest.FindUserBirthdayFacts(request.monthDay),
        ).facts,
    )
}

@ApplicationScoped
class FindUserKnowledgeSummaryStateInProcessAdapter(
    private val persistenceDataInputPort: PersistenceDataInputPort,
) : FindUserKnowledgeSummaryStatePort {
    override fun execute(
        request: FindUserKnowledgeSummaryStatePort.Request,
    ): FindUserKnowledgeSummaryStatePort.Response {
        val state = persistenceDataInputPort.findUserKnowledgeSummaryState(
            PersistenceDataRequest.FindUserKnowledgeSummaryState(request.userId),
        )
        return FindUserKnowledgeSummaryStatePort.Response(
            summarizedAt = state.summarizedAt,
            factsSinceSummary = state.factsSinceSummary,
        )
    }
}
