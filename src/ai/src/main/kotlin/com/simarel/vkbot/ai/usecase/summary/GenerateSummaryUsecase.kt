package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.adapter.output.ai.SummarizationAiService
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.PersistenceDataOutputPort
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class GenerateSummaryUsecase(
    private val summarizationAiService: SummarizationAiService,
    private val persistencePort: PersistenceDataOutputPort,
) {

    data class SummaryResult(
        val fullSummary: String,
        val shortSummary: String,
    )

    fun createPendingSummary(peerId: Long, firstMessageId: Long, lastMessageId: Long): UUID {
        return persistencePort.createPendingSummary(peerId, firstMessageId, lastMessageId)
    }

    fun generateSummary(messages: List<StoredMessage>): SummaryResult {
        if (messages.isEmpty()) {
            return SummaryResult("Нет сообщений для суммаризации", "Нет сообщений")
        }

        val formattedMessages = formatMessagesForSummary(messages)
        val response = summarizationAiService.generateSummary(formattedMessages)

        return SummaryResult(
            fullSummary = response.fullSummary,
            shortSummary = response.shortSummary
        )
    }

    fun saveCompletedSummary(summaryId: UUID, result: SummaryResult) {
        persistencePort.saveCompletedSummary(summaryId, result.shortSummary, result.fullSummary)
    }

    fun markAsFailed(summaryId: UUID) {
        persistencePort.markSummaryAsFailed(summaryId)
    }

    private fun formatMessagesForSummary(messages: List<StoredMessage>): String {
        val fromIds = messages.map { it.fromId }.distinct()
        val (groupFromIds, userIds) = fromIds.partition { it < 0 }

        val userProfiles = persistencePort.findUserProfilesByIds(userIds.map { FromId.of(it) })
            .associateBy { it.id.value }
        val groupProfiles = persistencePort.findGroupProfilesByIds(groupFromIds.map { FromId.of(it) })
            .associateBy { it.id.value }

        return messages.joinToString("\n\n") { msg ->
            val authorName = when {
                msg.fromId < 0 -> {
                    val groupId = -msg.fromId
                    groupProfiles[groupId]?.name ?: "Группа ${msg.fromId}"
                }
                else -> {
                    userProfiles[msg.fromId]?.let { "${it.firstName} ${it.lastName}" } ?: "Пользователь ${msg.fromId}"
                }
            }
            "[$authorName]: ${msg.messageText}"
        }
    }
}
