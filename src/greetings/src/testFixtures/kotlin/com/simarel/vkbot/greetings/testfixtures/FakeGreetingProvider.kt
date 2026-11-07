package com.simarel.vkbot.greetings.testfixtures

import com.simarel.vkbot.greetings.command.collect.CollectGreetingPlansCommand
import com.simarel.vkbot.greetings.command.text.GenerateGreetingTextCommand
import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import java.time.LocalDate

private const val DEFAULT_GROUP_CHAT_ID = 2_000_000_001L
private val DEFAULT_DATE: LocalDate = LocalDate.parse("2027-01-01")

object FakeGreetingProvider {

    fun createHolidayGreetingPlan(
        peerId: PeerId = PeerId.of(DEFAULT_GROUP_CHAT_ID),
        holiday: Holiday = Holiday.NEW_YEAR,
    ) = GreetingPlan.HolidayGreeting(peerId = peerId, holiday = holiday)

    fun createBirthdayGreetingPlan(
        peerId: PeerId = PeerId.of(DEFAULT_GROUP_CHAT_ID),
        people: List<String> = listOf("Иван Петров"),
    ) = GreetingPlan.BirthdayGreeting(peerId = peerId, people = people)

    fun createRunDailyGreetingsRequest(
        date: LocalDate = DEFAULT_DATE,
    ) = RunDailyGreetingsInputPort.Request(date = date)

    fun createRunDailyGreetingsResponse(
        planned: Int = 1,
        sent: Int = 1,
        failed: Int = 0,
    ) = RunDailyGreetingsInputPort.Response(planned = planned, sent = sent, failed = failed)

    fun createCollectGreetingPlansRequest(
        date: LocalDate = DEFAULT_DATE,
    ) = CollectGreetingPlansCommand.Request(date = date)

    fun createCollectGreetingPlansResponse(
        plans: List<GreetingPlan> = listOf(createHolidayGreetingPlan()),
    ) = CollectGreetingPlansCommand.Response(plans = plans)

    fun createGenerateGreetingTextCommandRequest(
        date: LocalDate = DEFAULT_DATE,
        plan: GreetingPlan = createHolidayGreetingPlan(),
    ) = GenerateGreetingTextCommand.Request(date = date, plan = plan)

    fun createGenerateGreetingTextCommandResponse(
        text: MessageText = MessageText.of("С Новым годом!"),
    ) = GenerateGreetingTextCommand.Response(text = text)
}
