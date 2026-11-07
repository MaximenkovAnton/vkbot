package com.simarel.vkbot.greetings.command.text

import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.ai.GenerateGreetingTextPort
import jakarta.enterprise.context.ApplicationScoped
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
open class GenerateGreetingTextCommandImpl(
    private val generateGreetingText: GenerateGreetingTextPort,
) : GenerateGreetingTextCommand {
    private val logger = System.getLogger(GenerateGreetingTextCommandImpl::class.java.name)

    @Suppress("TooGenericExceptionCaught")
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
            people = emptyList(),
        )
        is GreetingPlan.BirthdayGreeting -> GenerateGreetingTextPort.Request(
            date = date,
            occasion = BIRTHDAY_OCCASION,
            people = plan.people.sorted(),
        )
    }

    private fun template(plan: GreetingPlan): String = when (plan) {
        is GreetingPlan.HolidayGreeting -> holidayTemplate(plan.holiday)
        is GreetingPlan.BirthdayGreeting -> "С днём рождения, ${plan.people.sorted().joinToString(", ")}!"
    }

    private fun holidayTemplate(holiday: Holiday): String = when (holiday) {
        Holiday.NEW_YEAR -> "С Новым годом!"
        Holiday.PROGRAMMER_DAY -> "С Днём программиста!"
    }

    private companion object {
        const val BIRTHDAY_OCCASION = "День рождения"
    }
}
