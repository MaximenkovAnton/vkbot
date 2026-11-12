package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.ai.domain.knowledge.UserKnowledgeSummaryFormatter
import com.simarel.vkbot.ai.port.output.knowledge.UserKnowledgeSummaryPort
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgeSummaryStatePort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val MAX_SUMMARY_LENGTH = 2000

@ApplicationScoped
class GenerateUserKnowledgeSummaryCommandImpl(
    private val findUserKnowledgeSummaryStatePort: FindUserKnowledgeSummaryStatePort,
    private val findUserKnowledgePort: FindUserKnowledgePort,
    private val userKnowledgeSummaryPort: UserKnowledgeSummaryPort,
    private val saveUserKnowledgeSummaryPort: SaveUserKnowledgeSummaryPort,
    @ConfigProperty(name = "knowledge.summary-fact-threshold", defaultValue = "20")
    private val threshold: Int,
    @ConfigProperty(name = "knowledge.summary-interval", defaultValue = "14d")
    private val interval: Duration,
) : GenerateUserKnowledgeSummaryCommand {

    private val logger: System.Logger = System.getLogger(GenerateUserKnowledgeSummaryCommandImpl::class.java.name)

    override fun execute(
        request: GenerateUserKnowledgeSummaryCommand.Request,
    ): GenerateUserKnowledgeSummaryCommand.Response {
        val userId = request.payload.userId
        val state = findUserKnowledgeSummaryStatePort.execute(
            FindUserKnowledgeSummaryStatePort.Request(userId),
        )
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val summarizedAt = state.summarizedAt
        if (summarizedAt != null &&
            state.factsSinceSummary < threshold &&
            summarizedAt.isAfter(now.minus(interval))
        ) {
            logger.log(
                System.Logger.Level.DEBUG,
                "Knowledge summary skipped for userId=$userId: below threshold and interval",
            )
            return GenerateUserKnowledgeSummaryCommand.Response
        }

        val fromId = FromId.of(userId)
        val facts = findUserKnowledgePort.execute(
            FindUserKnowledgePort.Request(listOf(fromId)),
        ).knowledge[fromId]?.facts.orEmpty()
        val factsBlock = UserKnowledgeSummaryFormatter.format(facts)
        if (factsBlock.isEmpty()) {
            logger.log(
                System.Logger.Level.DEBUG,
                "Knowledge summary skipped for userId=$userId: no fresh facts",
            )
            return GenerateUserKnowledgeSummaryCommand.Response
        }

        val text = userKnowledgeSummaryPort.execute(
            UserKnowledgeSummaryPort.Request(factsBlock),
        ).text
        val clipped = text.take(MAX_SUMMARY_LENGTH)
        saveUserKnowledgeSummaryPort.execute(
            SaveUserKnowledgeSummaryPort.Request(userId = userId, summaryText = clipped),
        )
        logger.log(
            System.Logger.Level.INFO,
            "Knowledge summary completed for userId=$userId, " +
                "facts=${facts.count { fact -> !fact.stale }}, length=${clipped.length}",
        )
        return GenerateUserKnowledgeSummaryCommand.Response
    }
}
