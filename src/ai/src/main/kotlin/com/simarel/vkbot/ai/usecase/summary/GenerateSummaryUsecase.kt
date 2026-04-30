package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.adapter.output.ai.SummarizationAiService
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryStatus
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class GenerateSummaryUsecase(
    private val summarizationAiService: SummarizationAiService,
    private val summaryRepository: JooqSummaryRepository,
    private val findUserProfilesPort: FindUserProfilesByIdsPort,
    private val findGroupProfilesPort: FindGroupProfilesByIdsPort,
) {

    data class SummaryResult(
        val fullSummary: String,
        val shortSummary: String,
    )

    @Transactional
    fun createPendingSummary(peerId: Long, firstMessageId: Long, lastMessageId: Long): UUID {
        val summaryId = UUID.randomUUID()
        val pendingSummary = SummaryEntity().apply {
            id = summaryId
            this.peerId = peerId
            this.firstMessageId = firstMessageId
            this.lastMessageId = lastMessageId
            this.fullSummary = ""
            this.shortSummary = ""
            this.status = SummaryStatus.PENDING
            this.createdAt = OffsetDateTime.now()
            this.updatedAt = OffsetDateTime.now()
        }
        summaryRepository.save(pendingSummary)
        return summaryId
    }

    fun generateSummary(messages: List<MessageEntity>): SummaryResult {
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

    @Transactional
    fun saveCompletedSummary(summaryId: UUID, result: SummaryResult) {
        val completedSummary = SummaryEntity().apply {
            id = summaryId
            this.fullSummary = result.fullSummary
            this.shortSummary = result.shortSummary
            this.status = SummaryStatus.COMPLETED
            this.updatedAt = OffsetDateTime.now()
        }
        summaryRepository.save(completedSummary)
    }

    @Transactional
    fun markAsFailed(summaryId: UUID) {
        val failedSummary = SummaryEntity().apply {
            id = summaryId
            this.status = SummaryStatus.FAILED
            this.updatedAt = OffsetDateTime.now()
        }
        summaryRepository.save(failedSummary)
    }

    private fun formatMessagesForSummary(messages: List<MessageEntity>): String {
        val fromIds = messages.map { it.fromId!! }.distinct()
        val (groupFromIds, userIds) = fromIds.partition { it < 0 }

        val userProfiles = findUserProfilesPort.findByIds(userIds.map { FromId.of(it) })
            .associateBy { it.id.value }
        val groupProfiles = findGroupProfilesPort.findByIds(groupFromIds.map { FromId.of(it) })
            .associateBy { it.id.value }

        return messages.joinToString("\n\n") { msg ->
            val authorName = when {
                msg.fromId!! < 0 -> {
                    val groupId = -msg.fromId!!
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
