package com.simarel.vkbot.ai.command.summary

import com.simarel.vkbot.ai.port.output.summary.SummarizationOutputPort
import com.simarel.vkbot.ai.port.output.summary.SummarizationRequest
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.CreateStoredPendingSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.MarkStoredSummaryFailedPort
import com.simarel.vkbot.share.port.output.persistence.SaveStoredCompletedSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateSummaryCommandImpl(
    private val summarizationPort: SummarizationOutputPort,
    private val createPendingSummaryPort: CreateStoredPendingSummaryPort,
    private val findMessagesBetweenPort: FindStoredMessagesBetweenPort,
    private val saveCompletedSummaryPort: SaveStoredCompletedSummaryPort,
    private val markSummaryAsFailedPort: MarkStoredSummaryFailedPort,
    private val findUserProfilesByIdsPort: FindStoredUserProfilesByIdsPort,
    private val findGroupProfilesByIdsPort: FindStoredGroupProfilesByIdsPort,
) : GenerateSummaryCommand {

    override fun execute(request: GenerateSummaryCommand.GenerateSummaryRequest): GenerateSummaryCommand.GenerateSummaryResponse {
        val summaryId = createPendingSummaryPort.execute(
            CreateStoredPendingSummaryPort.Request(
                request.peerId,
                request.firstMessageId,
                request.lastMessageId,
            ),
        ).summaryId

        try {
            val messages = findMessagesBetweenPort.execute(
                FindStoredMessagesBetweenPort.Request(
                    peerId = request.peerId,
                    firstMessageId = request.firstMessageId,
                    lastMessageId = request.lastMessageId,
                    limit = 1000,
                ),
            ).messages

            if (messages.isEmpty()) {
                saveCompletedSummaryPort.execute(
                    SaveStoredCompletedSummaryPort.Request(summaryId, "Нет сообщений", "Нет сообщений для суммаризации"),
                )
                return GenerateSummaryCommand.GenerateSummaryResponse(summaryId)
            }

            val formattedMessages = formatMessagesForSummary(messages)
            val summaryResult = summarizationPort.execute(SummarizationRequest(formattedMessages))

            saveCompletedSummaryPort.execute(
                SaveStoredCompletedSummaryPort.Request(summaryId, summaryResult.shortSummary, summaryResult.fullSummary),
            )
            return GenerateSummaryCommand.GenerateSummaryResponse(summaryId)
        } catch (e: Exception) {
            markSummaryAsFailedPort.execute(MarkStoredSummaryFailedPort.Request(summaryId))
            throw e
        }
    }

    private fun formatMessagesForSummary(messages: List<StoredMessage>): String {
        val fromIds = messages.map { it.fromId }.distinct()
        val (groupFromIds, userIds) = fromIds.partition { it < 0 }

        val userProfiles = findUserProfilesByIdsPort.execute(
            FindStoredUserProfilesByIdsPort.Request(userIds.map { FromId.of(it) }),
        ).profiles.associateBy { it.id.value }
        val groupProfiles = findGroupProfilesByIdsPort.execute(
            FindStoredGroupProfilesByIdsPort.Request(groupFromIds.map { FromId.of(it) }),
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
