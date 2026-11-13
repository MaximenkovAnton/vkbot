package com.simarel.vkbot.ai.domain.knowledge

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory

private const val SELF_STATEMENT_CAP = 0.8
private const val OTHER_STATEMENT_CAP = 0.6
private const val CONGRATULATION_CAP = 0.6
private const val INFERENCE_CAP = 0.4

object UserKnowledgeValidator {

    private val WHITESPACE = Regex("\\s+")

    private val SOURCE_CONFIDENCE_CAPS = mapOf(
        KnowledgeSourceKind.SELF_STATEMENT to SELF_STATEMENT_CAP,
        KnowledgeSourceKind.OTHER_STATEMENT to OTHER_STATEMENT_CAP,
        KnowledgeSourceKind.CONGRATULATION to CONGRATULATION_CAP,
        KnowledgeSourceKind.INFERENCE to INFERENCE_CAP,
    )

    fun validate(
        drafts: List<KnowledgeFactDraft>,
        messages: List<StoredMessage>,
        authorNames: Map<Long, String>,
        peerId: Long,
    ): List<UserFactCandidate> {
        val candidates = mutableListOf<UserFactCandidate>()
        val seen = mutableSetOf<String>()
        drafts.forEach { draft ->
            val candidate = toCandidate(draft, messages, authorNames, peerId) ?: return@forEach
            if (seen.add(deduplicationKey(candidate))) {
                candidates += candidate
            }
        }
        return candidates
    }

    private fun toCandidate(
        draft: KnowledgeFactDraft,
        messages: List<StoredMessage>,
        authorNames: Map<Long, String>,
        peerId: Long,
    ): UserFactCandidate? {
        val source = messages.firstOrNull {
            it.conversationMessageId == draft.sourceConversationMessageId
        } ?: return null
        val category = draft.category?.uppercase()?.let { name ->
            UserFactCategory.entries.firstOrNull { it.name == name }
        } ?: return null
        val factKey = draft.key?.takeIf { UserFactCandidate.FACT_KEY_REGEX.matches(it) } ?: return null
        val value = draft.value?.trim()?.takeIf {
            it.isNotEmpty() && it.length <= UserFactCandidate.MAX_VALUE_LENGTH
        } ?: return null
        val rawConfidence = draft.confidence?.takeIf { it.isFinite() } ?: return null
        val userId = resolveUserId(draft, authorNames) ?: return null
        val sourceKind = resolveSourceKind(draft.sourceKind, userId, source.fromId)
        return UserFactCandidate(
            userId = userId,
            peerId = peerId,
            category = category,
            factKey = factKey,
            value = value,
            valuePayload = draft.valuePayload ?: emptyMap(),
            sourceKind = sourceKind,
            confidence = rawConfidence.coerceIn(0.0, 1.0).coerceAtMost(SOURCE_CONFIDENCE_CAPS.getValue(sourceKind)),
            messageId = source.id ?: return null,
            conversationMessageId = source.conversationMessageId,
            saidById = source.fromId,
            observedAt = source.date ?: return null,
        )
    }

    private fun resolveUserId(draft: KnowledgeFactDraft, authorNames: Map<Long, String>): Long? {
        draft.subjectId?.let { return it.takeIf(authorNames::containsKey) }
        val normalizedSubject = normalizeName(draft.subjectName ?: return null).ifEmpty { return null }
        return authorNames.filterValues { normalizeName(it) == normalizedSubject }.keys.singleOrNull()
    }

    private fun resolveSourceKind(raw: String?, userId: Long, saidById: Long): KnowledgeSourceKind {
        val kind = raw?.uppercase()?.let { name ->
            KnowledgeSourceKind.entries.firstOrNull { it.name == name }
        } ?: KnowledgeSourceKind.INFERENCE
        return if (kind == KnowledgeSourceKind.SELF_STATEMENT && userId != saidById) {
            KnowledgeSourceKind.OTHER_STATEMENT
        } else {
            kind
        }
    }

    private fun normalizeName(value: String): String = value.trim().replace(WHITESPACE, " ").lowercase()

    private fun deduplicationKey(candidate: UserFactCandidate): String {
        val canonicalValue = candidate.value.trim().replace(WHITESPACE, " ").lowercase()
        return "${candidate.userId}|${candidate.category.name}|${candidate.factKey}|$canonicalValue"
    }
}
