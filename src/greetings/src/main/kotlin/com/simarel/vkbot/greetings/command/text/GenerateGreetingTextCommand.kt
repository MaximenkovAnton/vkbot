package com.simarel.vkbot.greetings.command.text

import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.greeting.GreetingPerson
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.ai.GenerateGreetingTextPort
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.lang.System.Logger.Level
import java.time.LocalDate

interface GenerateGreetingTextCommand :
    Command<
        GenerateGreetingTextCommand.Request,
        GenerateGreetingTextCommand.Response,
        > {
    data class Request(val date: LocalDate, val plan: GreetingPlan) : CommandRequest
    data class Response(val text: MessageText) : CommandResponse
}

@ApplicationScoped
@Suppress("TooGenericExceptionCaught")
open class GenerateGreetingTextCommandImpl(
    private val generateGreetingText: GenerateGreetingTextPort,
    private val findChatKnowledgeSummaryPort: FindChatKnowledgeSummaryPort,
    private val findUserKnowledgePort: FindUserKnowledgePort,
    @ConfigProperty(name = "greetings.knowledge.enabled", defaultValue = "true")
    private val knowledgeEnabled: Boolean,
) : GenerateGreetingTextCommand {
    private val logger = System.getLogger(GenerateGreetingTextCommandImpl::class.java.name)

    override fun execute(request: GenerateGreetingTextCommand.Request): GenerateGreetingTextCommand.Response {
        val text = try {
            generateGreetingText.execute(aiRequest(request.date, request.plan)).text
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Greeting text generation failed, using the template", e)
            return GenerateGreetingTextCommand.Response(MessageText.of(template(request.plan)))
        }
        if (text.value.isBlank()) {
            logger.log(Level.WARNING, "Greeting text is blank, using the template")
            return GenerateGreetingTextCommand.Response(MessageText.of(template(request.plan)))
        }
        return GenerateGreetingTextCommand.Response(text)
    }

    private fun aiRequest(date: LocalDate, plan: GreetingPlan): GenerateGreetingTextPort.Request = when (plan) {
        is GreetingPlan.HolidayGreeting -> GenerateGreetingTextPort.Request(
            date = date,
            occasion = plan.holiday.title,
            chatFacts = chatFacts(plan.peerId),
        )
        is GreetingPlan.BirthdayGreeting -> {
            val people = plan.people.sortedBy { person -> person.name }
            GenerateGreetingTextPort.Request(
                date = date,
                occasion = BIRTHDAY_OCCASION,
                people = people,
                chatFacts = chatFacts(plan.peerId),
                personalKnowledge = personalKnowledge(plan.peerId, people),
            )
        }
    }

    private fun chatFacts(peerId: PeerId): String? {
        if (!knowledgeEnabled) {
            return null
        }
        return try {
            findChatKnowledgeSummaryPort.execute(FindChatKnowledgeSummaryPort.Request(peerId.value))
                .knowledge?.summary?.takeIf(String::isNotBlank)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: RuntimeException) {
            logger.log(
                Level.WARNING,
                "Greeting chat knowledge read failed for peerId=${peerId.value}: ${e.javaClass.simpleName}",
            )
            null
        }
    }

    private fun personalKnowledge(
        peerId: PeerId,
        people: List<GreetingPerson>,
    ): Map<FromId, UserKnowledge> {
        if (!knowledgeEnabled) {
            return emptyMap()
        }
        return try {
            findUserKnowledgePort.execute(FindUserKnowledgePort.Request(people.map { person -> person.fromId }))
                .knowledge
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: RuntimeException) {
            logger.log(
                Level.WARNING,
                "Greeting personal knowledge read failed for peerId=${peerId.value}: " +
                    e.javaClass.simpleName,
            )
            emptyMap()
        }
    }

    private fun template(plan: GreetingPlan): String = when (plan) {
        is GreetingPlan.HolidayGreeting -> holidayTemplate(plan.holiday)
        is GreetingPlan.BirthdayGreeting ->
            "С днём рождения, ${plan.people.map { person -> person.name }.sorted().joinToString(", ")}!"
    }

    private fun holidayTemplate(holiday: Holiday): String = when (holiday) {
        Holiday.NEW_YEAR -> "С Новым годом!"
        Holiday.PROGRAMMER_DAY -> "С Днём программиста!"
    }

    private companion object {
        const val BIRTHDAY_OCCASION = "День рождения"
    }
}
