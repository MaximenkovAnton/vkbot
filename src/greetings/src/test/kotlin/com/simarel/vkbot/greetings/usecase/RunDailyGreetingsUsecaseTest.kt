package com.simarel.vkbot.greetings.usecase

import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.test.FakeClaimDailyGreeting
import com.simarel.vkbot.greetings.test.FakeCollectGreetingPlansCommand
import com.simarel.vkbot.greetings.test.FakeGenerateGreetingTextCommand
import com.simarel.vkbot.greetings.test.FakeSendGreetingMessage
import com.simarel.vkbot.greetings.test.SystemLoggerCapture
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.logging.Level

class RunDailyGreetingsUsecaseTest {

    @Test
    fun `one plan is sent as one message`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)
        val send = FakeSendGreetingMessage()
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            peerId = peerId,
            people = listOf(FakeModelProvider.createGreetingPerson(name = "Иван Петров")),
        )

        val response = usecase(plans = listOf(plan), send = send)
            .execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))

        assertEquals(1, response.planned)
        assertEquals(1, response.sent)
        assertEquals(0, response.failed)
        val request = send.executeCalls.single()
        assertEquals(peerId, request.peerId)
        assertEquals(GREETING_TEXT, request.messageText.value)
    }

    @Test
    fun `two holiday plans for one peer are two messages with a single claim`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)
        val claim = FakeClaimDailyGreeting()
        val send = FakeSendGreetingMessage()

        val response = usecase(
            plans = listOf(
                FakeGreetingProvider.createHolidayGreetingPlan(peerId = peerId, holiday = Holiday.NEW_YEAR),
                FakeGreetingProvider.createHolidayGreetingPlan(peerId = peerId, holiday = Holiday.PROGRAMMER_DAY),
            ),
            claim = claim,
            send = send,
        ).execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))

        assertEquals(2, response.planned)
        assertEquals(2, response.sent)
        assertEquals(0, response.failed)
        assertEquals(2, send.executeCalls.size)
        assertTrue(send.executeCalls.all { it.peerId == peerId })
        val claimCall = claim.executeCalls.single()
        assertEquals(DATE, claimCall.date)
        assertEquals(peerId, claimCall.peerId)
    }

    @Test
    fun `a repeated run of the same date sends nothing twice`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)
        val claim = FakeClaimDailyGreeting()
        val send = FakeSendGreetingMessage()
        val usecase = usecase(
            plans = listOf(FakeGreetingProvider.createHolidayGreetingPlan(peerId = peerId)),
            claim = claim,
            send = send,
        )
        val request = FakeGreetingProvider.createRunDailyGreetingsRequest(DATE)

        val first = usecase.execute(request)
        val second = usecase.execute(request)

        assertEquals(1, first.sent)
        assertEquals(0, second.sent)
        assertEquals(1, send.executeCalls.size)
        assertEquals(2, claim.executeCalls.size)
        assertTrue(claim.executeCalls.all { it.date == DATE && it.peerId == peerId })
    }

    @Test
    fun `a claimed peer does not block the next peer`() {
        val firstPeer = FakeVoProvider.createPeerId(2_000_000_001)
        val secondPeer = FakeVoProvider.createPeerId(2_000_000_002)
        val claim = FakeClaimDailyGreeting(DATE to firstPeer)
        val send = FakeSendGreetingMessage()

        val response = usecase(
            plans = listOf(
                FakeGreetingProvider.createHolidayGreetingPlan(peerId = firstPeer),
                FakeGreetingProvider.createHolidayGreetingPlan(peerId = secondPeer),
            ),
            claim = claim,
            send = send,
        ).execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))

        assertEquals(2, response.planned)
        assertEquals(1, response.sent)
        assertEquals(0, response.failed)
        assertEquals(secondPeer, send.executeCalls.single().peerId)
    }

    @Test
    fun `a send failure is isolated to one message of the peer`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)
        val send = FakeSendGreetingMessage()
        send.failuresByAttempt[FIRST_ATTEMPT] = IllegalStateException("vk is down")

        SystemLoggerCapture(RunDailyGreetingsUsecase::class.java.name).use { capture ->
            val response = usecase(
                plans = listOf(
                    FakeGreetingProvider.createHolidayGreetingPlan(peerId = peerId, holiday = Holiday.NEW_YEAR),
                    FakeGreetingProvider.createHolidayGreetingPlan(peerId = peerId, holiday = Holiday.PROGRAMMER_DAY),
                ),
                send = send,
            ).execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))

            assertEquals(2, response.planned)
            assertEquals(1, response.sent)
            assertEquals(1, response.failed)
            assertEquals(2, send.executeCalls.size)
            assertTrue(send.executeCalls.all { it.peerId == peerId })
            assertTrue(
                capture.messages(Level.SEVERE).any {
                    it.contains(peerId.value.toString()) && it.contains("IllegalStateException")
                },
            )
        }
    }

    @Test
    fun `a claim failure counts every message of the peer as failed`() {
        val firstPeer = FakeVoProvider.createPeerId(2_000_000_001)
        val secondPeer = FakeVoProvider.createPeerId(2_000_000_002)
        val claim = FakeClaimDailyGreeting()
        claim.failures[firstPeer] = IllegalStateException("database is down")
        val send = FakeSendGreetingMessage()

        SystemLoggerCapture(RunDailyGreetingsUsecase::class.java.name).use { capture ->
            val response = usecase(
                plans = listOf(
                    FakeGreetingProvider.createHolidayGreetingPlan(
                        peerId = firstPeer,
                        holiday = Holiday.NEW_YEAR,
                    ),
                    FakeGreetingProvider.createHolidayGreetingPlan(
                        peerId = firstPeer,
                        holiday = Holiday.PROGRAMMER_DAY,
                    ),
                    FakeGreetingProvider.createHolidayGreetingPlan(peerId = secondPeer),
                ),
                claim = claim,
                send = send,
            ).execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))

            assertEquals(3, response.planned)
            assertEquals(1, response.sent)
            assertEquals(2, response.failed)
            assertEquals(secondPeer, send.executeCalls.single().peerId)
            assertTrue(
                capture.messages(Level.SEVERE).any {
                    it.contains(firstPeer.value.toString()) && it.contains("IllegalStateException")
                },
            )
        }
    }

    @Test
    fun `an interruption is rethrown and keeps the interrupt flag`() {
        val peerId = FakeVoProvider.createPeerId(2_000_000_001)
        val send = FakeSendGreetingMessage()
        send.failures[peerId] = InterruptedException()

        try {
            assertThrows<InterruptedException> {
                usecase(plans = listOf(FakeGreetingProvider.createHolidayGreetingPlan(peerId = peerId)), send = send)
                    .execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `an empty plan set produces an empty response`() {
        val claim = FakeClaimDailyGreeting()
        val send = FakeSendGreetingMessage()

        val response = usecase(plans = emptyList(), claim = claim, send = send)
            .execute(FakeGreetingProvider.createRunDailyGreetingsRequest(DATE))

        assertEquals(0, response.planned)
        assertEquals(0, response.sent)
        assertEquals(0, response.failed)
        assertTrue(claim.executeCalls.isEmpty())
        assertTrue(send.executeCalls.isEmpty())
    }

    private fun usecase(
        plans: List<GreetingPlan> = emptyList(),
        claim: FakeClaimDailyGreeting = FakeClaimDailyGreeting(),
        send: FakeSendGreetingMessage = FakeSendGreetingMessage(),
        text: MessageText = FakeVoProvider.createMessageText(GREETING_TEXT),
    ): RunDailyGreetingsUsecase = RunDailyGreetingsUsecase(
        FakeCollectGreetingPlansCommand(plans = plans),
        FakeGenerateGreetingTextCommand(text = text),
        claim,
        send,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.parse("2026-01-01")
        const val GREETING_TEXT = "generated greeting"
        const val FIRST_ATTEMPT = 1
    }
}
