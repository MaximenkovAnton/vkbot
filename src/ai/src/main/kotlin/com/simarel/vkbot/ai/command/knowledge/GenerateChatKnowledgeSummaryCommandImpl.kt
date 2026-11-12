package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.ai.command.common.MessageAuthorFormatter
import com.simarel.vkbot.ai.domain.knowledge.PromptBudget
import com.simarel.vkbot.ai.port.output.knowledge.ChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatSummaryMessagesPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val MAX_SUMMARY_LENGTH = 2000

@ApplicationScoped
@Suppress("LongParameterList")
class GenerateChatKnowledgeSummaryCommandImpl(
    private val findChatSummaryMessagesPort: FindChatSummaryMessagesPort,
    private val findChatKnowledgeSummaryPort: FindChatKnowledgeSummaryPort,
    private val chatKnowledgeSummaryPort: ChatKnowledgeSummaryPort,
    private val saveChatKnowledgeSummaryPort: SaveChatKnowledgeSummaryPort,
    private val messageAuthorFormatter: MessageAuthorFormatter,
    @ConfigProperty(name = "knowledge.chat-summary-message-threshold", defaultValue = "100")
    private val threshold: Int,
    @ConfigProperty(name = "knowledge.chat-summary-interval", defaultValue = "7d")
    private val interval: Duration,
    @ConfigProperty(name = "knowledge.prompt-max-chars", defaultValue = "12000")
    private val promptMaxChars: Int,
) : GenerateChatKnowledgeSummaryCommand {

    private val logger: System.Logger = System.getLogger(GenerateChatKnowledgeSummaryCommandImpl::class.java.name)

    override fun execute(
        request: GenerateChatKnowledgeSummaryCommand.Request,
    ): GenerateChatKnowledgeSummaryCommand.Response {
        val peerId = request.payload.peerId
        val window = findChatSummaryMessagesPort.execute(
            FindChatSummaryMessagesPort.Request(peerId = peerId, limit = threshold),
        ).messages
        if (window.isEmpty()) {
            logger.log(
                System.Logger.Level.DEBUG,
                "Chat knowledge summary skipped for peerId=$peerId: empty window",
            )
            return GenerateChatKnowledgeSummaryCommand.Response
        }

        val summary = findChatKnowledgeSummaryPort.execute(
            FindChatKnowledgeSummaryPort.Request(peerId),
        ).knowledge
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (summary != null && window.size < threshold && summary.summarizedAt.isAfter(now.minus(interval))) {
            logger.log(
                System.Logger.Level.DEBUG,
                "Chat knowledge summary skipped for peerId=$peerId: below threshold and interval",
            )
            return GenerateChatKnowledgeSummaryCommand.Response
        }

        val lines = PromptBudget.fit(messageAuthorFormatter.format(window), promptMaxChars)
        val text = chatKnowledgeSummaryPort.execute(
            ChatKnowledgeSummaryPort.Request(lines),
        ).text
        val clipped = text.take(MAX_SUMMARY_LENGTH)
        saveChatKnowledgeSummaryPort.execute(
            SaveChatKnowledgeSummaryPort.Request(
                peerId = peerId,
                summaryText = clipped,
                firstConversationMessageId = window.first().conversationMessageId,
                lastConversationMessageId = window.last().conversationMessageId,
                messagesCovered = window.size,
            ),
        )
        logger.log(
            System.Logger.Level.INFO,
            "Chat knowledge summary completed for peerId=$peerId, messages=${window.size}, length=${clipped.length}",
        )
        return GenerateChatKnowledgeSummaryCommand.Response
    }
}
