package com.simarel.vkbot.ai.adapter.output.ai.greeting

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.ai.GenerateGreetingTextPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import java.time.format.DateTimeFormatter

private const val NO_EVENTS = "нет"

@ApplicationScoped
class GreetingTextOutputAdapter(
    private val greetingAiService: GreetingAiService,
) : GenerateGreetingTextPort {

    @ActivateRequestContext
    override fun execute(request: GenerateGreetingTextPort.Request): GenerateGreetingTextPort.Response {
        val text = greetingAiService.generateGreeting(
            date = request.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            holidays = formatEvents(request.holidays),
            people = formatEvents(request.people),
        )
        return GenerateGreetingTextPort.Response(MessageText.of(text))
    }

    private fun formatEvents(events: List<String>): String = events.joinToString(", ").ifEmpty { NO_EVENTS }
}
