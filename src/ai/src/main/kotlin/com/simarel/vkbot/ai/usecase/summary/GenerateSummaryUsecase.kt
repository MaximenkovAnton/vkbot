package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.GenerateSummaryInputPortResponse
import com.simarel.vkbot.ai.port.output.persistence.SummaryPersistenceOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateSummaryUsecase(
    private val summarizationPort: SummarizationOutputPort,
    private val persistencePort: SummaryPersistenceOutputPort,
) : GenerateSummaryInputPort {

    override fun execute(request: GenerateSummaryInputPortRequest): GenerateSummaryInputPortResponse {
        val summaryId = persistencePort.createPendingSummary(
            request.peerId,
            request.firstMessageId,
            request.lastMessageId
        )

        try {
            val messages = persistencePort.findMessagesBetween(
                peerId = request.peerId,
                firstMessageId = request.firstMessageId,
                lastMessageId = request.lastMessageId,
                limit = 1000,
            )

            if (messages.isEmpty()) {
                persistencePort.saveCompletedSummary(
                    summaryId,
                    "Нет сообщений",
                    "Нет сообщений для суммаризации"
                )
                return GenerateSummaryInputPortResponse(summaryId)
            }

            val formattedMessages = formatMessagesForSummary(messages)
            val summaryResult = summarizationPort.execute(SummarizationRequest(formattedMessages))

            persistencePort.saveCompletedSummary(
                summaryId,
                summaryResult.shortSummary,
                summaryResult.fullSummary
            )

            return GenerateSummaryInputPortResponse(summaryId)
        } catch (e: Exception) {
            persistencePort.markSummaryAsFailed(summaryId)
            throw e
        }
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
                    userProfiles[msg.fromId]?.let { "${it.firstName} ${it.lastName}" }
                        ?: "Пользователь ${msg.fromId}"
                }
            }
            "[$authorName]: ${msg.messageText}"
        }
    }
}
