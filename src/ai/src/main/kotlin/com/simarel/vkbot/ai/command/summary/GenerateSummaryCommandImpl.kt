package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.ai.port.output.persistence.CreateAiStoredPendingSummaryPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredGroupProfilesByIdsPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredMessagesBetweenPort
import com.simarel.vkbot.ai.port.output.persistence.FindAiStoredUserProfilesByIdsPort
import com.simarel.vkbot.ai.port.output.persistence.MarkAiStoredSummaryFailedPort
import com.simarel.vkbot.ai.port.output.persistence.SaveAiStoredCompletedSummaryPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateSummaryCommandImpl(
    private val summarizationPort: SummarizationOutputPort,
    private val createPendingSummaryPort: CreateAiStoredPendingSummaryPort,
    private val findMessagesBetweenPort: FindAiStoredMessagesBetweenPort,
    private val saveCompletedSummaryPort: SaveAiStoredCompletedSummaryPort,
    private val markSummaryAsFailedPort: MarkAiStoredSummaryFailedPort,
    private val findUserProfilesByIdsPort: FindAiStoredUserProfilesByIdsPort,
    private val findGroupProfilesByIdsPort: FindAiStoredGroupProfilesByIdsPort,
) : GenerateSummaryCommand {

    override fun execute(request: GenerateSummaryCommand.GenerateSummaryRequest): GenerateSummaryCommand.GenerateSummaryResponse {
        val summaryId = createPendingSummaryPort.execute(
            CreateAiStoredPendingSummaryPort.Request(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId
            )
        ).summaryId

        try {
            val messages = findMessagesBetweenPort.execute(
                FindAiStoredMessagesBetweenPort.Request(
                    peerId = request.peerId,
                    firstMessageId = request.firstMessageId,
                    lastMessageId = request.lastMessageId,
                    limit = 1000,
                )
            ).messages

            if (messages.isEmpty()) {
                saveCompletedSummaryPort.execute(
                    SaveAiStoredCompletedSummaryPort.Request(summaryId, "Нет сообщений", "Нет сообщений для суммаризации")
                )
                return GenerateSummaryCommand.GenerateSummaryResponse(summaryId)
            }

            val formattedMessages = formatMessagesForSummary(messages)
            val summaryResult = summarizationPort.execute(SummarizationRequest(formattedMessages))

            saveCompletedSummaryPort.execute(
                SaveAiStoredCompletedSummaryPort.Request(summaryId, summaryResult.shortSummary, summaryResult.fullSummary)
            )
            return GenerateSummaryCommand.GenerateSummaryResponse(summaryId)
        } catch (e: Exception) {
            markSummaryAsFailedPort.execute(MarkAiStoredSummaryFailedPort.Request(summaryId))
            throw e
        }
    }

    private fun formatMessagesForSummary(messages: List<StoredMessage>): String {
        val fromIds = messages.map { it.fromId }.distinct()
        val (groupFromIds, userIds) = fromIds.partition { it < 0 }

        val userProfiles = findUserProfilesByIdsPort.execute(
            FindAiStoredUserProfilesByIdsPort.Request(userIds.map { FromId.of(it) })
        ).profiles.associateBy { it.id.value }
        val groupProfiles = findGroupProfilesByIdsPort.execute(
            FindAiStoredGroupProfilesByIdsPort.Request(groupFromIds.map { FromId.of(it) })
        ).profiles.associateBy { it.id.value }

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
