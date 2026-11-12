package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.ai.domain.exception.KnowledgeExtractionException
import com.simarel.vkbot.ai.domain.knowledge.UserKnowledgeBatchFormatter
import com.simarel.vkbot.ai.domain.knowledge.UserKnowledgeValidator
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionPort
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.ChatKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.domain.event.UserKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
class ProcessUserKnowledgeCommandImpl(
    private val findPendingKnowledgeBatchPort: FindPendingKnowledgeBatchPort,
    private val knowledgeExtractionPort: KnowledgeExtractionPort,
    private val saveUserFactsPort: SaveUserFactsPort,
    private val advanceExtractionCursorPort: AdvanceExtractionCursorPort,
    private val publishEventCommand: PublishEventCommand,
    private val findStoredUserProfilesByIdsPort: FindStoredUserProfilesByIdsPort,
    @ConfigProperty(name = "knowledge.batch-size", defaultValue = "25")
    private val batchSize: Int,
) : ProcessUserKnowledgeCommand {

    private val logger: System.Logger = System.getLogger(ProcessUserKnowledgeCommandImpl::class.java.name)

    override fun execute(request: ProcessUserKnowledgeCommand.Request): ProcessUserKnowledgeCommand.Response {
        val payload = request.payload
        val peerId = payload.peerId
        val messages = findPendingKnowledgeBatchPort.execute(
            FindPendingKnowledgeBatchPort.Request(
                peerId = peerId,
                upToConversationMessageId = payload.upToConversationMessageId,
                limit = batchSize,
            ),
        ).messages
        if (messages.isEmpty()) {
            advance(peerId, payload.upToConversationMessageId)
            logger.log(System.Logger.Level.DEBUG, "Knowledge extraction skipped for peerId=$peerId: empty batch")
            return ProcessUserKnowledgeCommand.Response
        }
        val processable = messages.filter(::isProcessable)
        if (processable.isEmpty()) {
            advance(peerId, messages.last().conversationMessageId)
            logger.log(
                System.Logger.Level.DEBUG,
                "Knowledge extraction skipped for peerId=$peerId: no processable messages",
            )
            return ProcessUserKnowledgeCommand.Response
        }

        val authorNames = authorNames(processable)
        val lines = UserKnowledgeBatchFormatter.format(processable, authorNames)
        val response = knowledgeExtractionPort.execute(KnowledgeExtractionRequest(lines))
        val drafts = response.facts
            ?: throw KnowledgeExtractionException("Knowledge extraction returned no facts field", null)
        val candidates = UserKnowledgeValidator.validate(drafts, processable, authorNames, peerId)
        val skipped = drafts.size - candidates.size
        val saved = if (candidates.isEmpty()) {
            0
        } else {
            saveUserFactsPort.execute(SaveUserFactsPort.Request(candidates)).saved
        }
        advance(peerId, messages.last().conversationMessageId)
        publishSummaries(peerId, candidates)
        logger.log(
            System.Logger.Level.INFO,
            "Knowledge extraction completed for peerId=$peerId, messages=${processable.size}, " +
                "saved=$saved, skipped=$skipped",
        )
        return ProcessUserKnowledgeCommand.Response
    }

    private fun isProcessable(message: StoredMessage): Boolean = message.messageText?.isNotBlank() == true &&
        message.date != null &&
        message.id != null &&
        FromId.of(message.fromId).isHuman()

    private fun authorNames(processable: List<StoredMessage>): Map<Long, String> {
        val authorIds = processable.map { FromId.of(it.fromId) }.distinct()
        val profiles = findStoredUserProfilesByIdsPort.execute(
            FindStoredUserProfilesByIdsPort.Request(authorIds),
        ).profiles.associateBy { it.id.value }
        return authorIds.associate { authorId ->
            val name = profiles[authorId.value]?.let { profile ->
                listOfNotNull(profile.firstName, profile.lastName)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString(" ")
            }.orEmpty()
            authorId.value to name.ifEmpty { "Пользователь ${authorId.value}" }
        }
    }

    private fun advance(peerId: Long, lastConversationMessageId: Long) {
        advanceExtractionCursorPort.execute(
            AdvanceExtractionCursorPort.Request(
                peerId = peerId,
                lastConversationMessageId = lastConversationMessageId,
                completedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
    }

    private fun publishSummaries(peerId: Long, candidates: List<UserFactCandidate>) {
        candidates.map { it.userId }.distinct().forEach { userId ->
            publishSummary(
                event = Event.USER_KNOWLEDGE_SUMMARY_REQUESTED,
                payload = UserKnowledgeSummaryRequestedPayload(userId),
                peerId = peerId,
                userId = userId,
            )
        }
        publishSummary(
            event = Event.CHAT_KNOWLEDGE_SUMMARY_REQUESTED,
            payload = ChatKnowledgeSummaryRequestedPayload(peerId),
            peerId = peerId,
            userId = null,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun publishSummary(event: Event, payload: Any, peerId: Long, userId: Long?) {
        try {
            publishEventCommand.execute(PublishEventRequest(event = event, payload = Payload(payload)))
        } catch (e: RuntimeException) {
            logger.log(
                System.Logger.Level.WARNING,
                "Knowledge summary event publication failed for peerId=$peerId, userId=${userId ?: "-"}: " +
                    e.javaClass.simpleName,
            )
        }
    }
}
