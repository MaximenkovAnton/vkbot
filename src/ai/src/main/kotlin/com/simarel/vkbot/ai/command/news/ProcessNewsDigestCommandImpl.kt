package com.simarel.vkbot.ai.command.news

import com.simarel.vkbot.ai.command.common.MessageAuthorFormatter
import com.simarel.vkbot.ai.domain.exception.NewsDigestGenerationException
import com.simarel.vkbot.ai.domain.news.NewsPeriod
import com.simarel.vkbot.ai.domain.news.NewsPeriodParser
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationPort
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredMessageByAuthorPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenDatesPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredSummariesInPeriodPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

private const val MAX_PERIOD_DAYS = 7L
private val MAX_PERIOD: Duration = Duration.ofDays(MAX_PERIOD_DAYS)
private const val TOO_LONG_PERIOD_REPLY = "Слишком длинный срок: сводка возможна максимум за неделю."
private const val NO_MESSAGES_REPLY = "Нет сообщений за этот период."
private const val GENERATION_FAILED_REPLY = "Не удалось собрать сводку. Попробуйте позже."
private const val SUMMARIES_SECTION_HEADER = "Сводки обсуждений за период:"
private const val MESSAGES_SECTION_HEADER = "Сообщения за период:"

@ApplicationScoped
@Suppress("LongParameterList", "TooManyFunctions")
class ProcessNewsDigestCommandImpl(
    private val publishEventCommand: PublishEventCommand,
    private val findLastStoredMessageByAuthorPort: FindLastStoredMessageByAuthorPort,
    private val findStoredSummariesInPeriodPort: FindStoredSummariesInPeriodPort,
    private val findStoredMessagesBetweenDatesPort: FindStoredMessagesBetweenDatesPort,
    private val newsDigestGenerationPort: NewsDigestGenerationPort,
    private val messageAuthorFormatter: MessageAuthorFormatter,
    @ConfigProperty(name = "summary.batch-size", defaultValue = "100")
    private val summaryBatchSize: Int,
    private val findUserKnowledgePort: FindUserKnowledgePort,
    private val findChatKnowledgeSummaryPort: FindChatKnowledgeSummaryPort,
    @ConfigProperty(name = "knowledge.news-personalization.enabled", defaultValue = "true")
    private val newsPersonalizationEnabled: Boolean,
) : ProcessNewsDigestCommand {

    private val logger = System.getLogger(ProcessNewsDigestCommandImpl::class.java.name)
    private val periodParser = NewsPeriodParser()

    override fun execute(
        request: ProcessNewsDigestCommand.ProcessNewsDigestRequest,
    ): ProcessNewsDigestCommand.ProcessNewsDigestResponse {
        val message = request.message
        val period = periodParser.parse(message.messageText.value, message.date.value)

        if (period != null && isTooLong(period)) {
            publish(TOO_LONG_PERIOD_REPLY, message)
            return ProcessNewsDigestCommand.ProcessNewsDigestResponse
        }

        val resolved = period ?: resolvePeriod(message)
        val summaries = findStoredSummariesInPeriodPort.execute(
            FindStoredSummariesInPeriodPort.Request(message.peerId.value, resolved.from, resolved.to),
        ).summaries
        val shortSummaries = summaries.mapNotNull { it.shortSummary?.takeIf(String::isNotBlank) }
        val maxLastMessageId = summaries.mapNotNull { it.lastMessageId }.maxOrNull()
        val messages = findStoredMessagesBetweenDatesPort.execute(
            FindStoredMessagesBetweenDatesPort.Request(
                message.peerId.value,
                resolved.from,
                resolved.to,
                summaryBatchSize,
            ),
        ).messages
            .filter { it.conversationMessageId != message.conversationMessageId.value }
            .filter { maxLastMessageId == null || it.conversationMessageId > maxLastMessageId }

        if (shortSummaries.isEmpty() && messages.isEmpty()) {
            publish(NO_MESSAGES_REPLY, message)
            return ProcessNewsDigestCommand.ProcessNewsDigestResponse
        }

        val knowledge = readKnowledge(message)
        val data = buildData(shortSummaries, messageAuthorFormatter.format(messages))
        val text = try {
            newsDigestGenerationPort.execute(
                NewsDigestGenerationRequest(data, knowledge.interests, knowledge.chatTopics),
            ).text
        } catch (e: NewsDigestGenerationException) {
            logGenerationFailure(message.peerId.value, e)
            publish(GENERATION_FAILED_REPLY, message)
            return ProcessNewsDigestCommand.ProcessNewsDigestResponse
        }

        publish(text, message)
        return ProcessNewsDigestCommand.ProcessNewsDigestResponse
    }

    private fun resolvePeriod(message: Message): NewsPeriod {
        val to = message.date.value
        val lastMessageDate = findLastStoredMessageByAuthorPort.execute(
            FindLastStoredMessageByAuthorPort.Request(
                peerId = message.peerId.value,
                fromId = message.fromId.value,
                beforeConversationMessageId = message.conversationMessageId.value,
            ),
        ).message?.date
        val from = lastMessageDate
            ?.takeIf { it <= to && Duration.between(it, to) <= MAX_PERIOD }
            ?: to.minusDays(MAX_PERIOD_DAYS)
        return NewsPeriod(from, to)
    }

    private fun isTooLong(period: NewsPeriod): Boolean = Duration.between(period.from, period.to) > MAX_PERIOD

    private fun buildData(shortSummaries: List<String>, messageLines: List<String>): String {
        val sections = listOfNotNull(
            shortSummaries.takeIf { it.isNotEmpty() }?.let { section(SUMMARIES_SECTION_HEADER, it) },
            messageLines.takeIf { it.isNotEmpty() }?.let { section(MESSAGES_SECTION_HEADER, it) },
        )
        return sections.joinToString("\n\n").trimEnd()
    }

    private fun section(header: String, lines: List<String>): String = (listOf(header) + lines).joinToString("\n")

    private fun publish(text: String, message: Message) {
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.ANSWER_MESSAGE,
                payload = Payload(
                    ResponseMessage(
                        messageText = MessageText.of(text),
                        responseTo = message,
                    ),
                ),
            ),
        )
    }

    private fun logGenerationFailure(peerId: Long, exception: NewsDigestGenerationException) {
        logger.log(
            System.Logger.Level.ERROR,
            "News digest generation failed for peerId=$peerId: ${exception.javaClass.simpleName}",
        )
    }

    private fun readKnowledge(message: Message): KnowledgeSelection {
        if (!newsPersonalizationEnabled) {
            return KnowledgeSelection(null, null)
        }
        return KnowledgeSelection(
            selectInterests(readAuthorKnowledge(message)),
            selectChatTopics(readChatKnowledge(message)),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readAuthorKnowledge(message: Message): UserKnowledge? = try {
        findUserKnowledgePort.execute(FindUserKnowledgePort.Request(listOf(message.fromId)))
            .knowledge[message.fromId]
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (e: RuntimeException) {
        logKnowledgeReadFailure(message, e)
        null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readChatKnowledge(message: Message): ChatKnowledge? = try {
        findChatKnowledgeSummaryPort.execute(FindChatKnowledgeSummaryPort.Request(message.peerId.value)).knowledge
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (e: RuntimeException) {
        logKnowledgeReadFailure(message, e)
        null
    }

    private fun selectInterests(knowledge: UserKnowledge?): String? {
        if (knowledge != null && knowledge.summaryFresh && !knowledge.summary.isNullOrBlank()) {
            return knowledge.summary
        }
        val interests = knowledge?.facts.orEmpty()
            .filter {
                it.category == UserFactCategory.INTEREST && it.status == UserFactStatus.CONFIRMED && !it.stale
            }
            .map { it.value }
            .distinct()
        return interests.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" }
    }

    private fun selectChatTopics(
        knowledge: ChatKnowledge?,
    ): String? = knowledge?.summary?.takeIf { it.isNotBlank() }

    private fun logKnowledgeReadFailure(message: Message, exception: Exception) {
        logger.log(
            System.Logger.Level.WARNING,
            "News digest knowledge read failed for peerId=${message.peerId.value}, " +
                "authorId=${message.fromId.value}: ${exception.javaClass.simpleName}",
        )
    }

    private data class KnowledgeSelection(val interests: String?, val chatTopics: String?)
}
