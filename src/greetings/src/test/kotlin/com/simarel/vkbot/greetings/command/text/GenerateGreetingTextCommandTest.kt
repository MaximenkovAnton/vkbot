package com.simarel.vkbot.greetings.command.text

import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.test.FakeGenerateGreetingTextPort
import com.simarel.vkbot.greetings.test.SystemLoggerCapture
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeGreetingPortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.logging.Level
import java.util.stream.Stream

class GenerateGreetingTextCommandTest {

    @Test
    fun `the ai request carries the date, enum ordered holidays and sorted names`() {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText("generated greeting"),
            ),
        )
        val plan = GreetingPlan(
            peerId = FakeVoProvider.createPeerId(2_000_000_001),
            holidays = listOf(Holiday.PROGRAMMER_DAY, Holiday.NEW_YEAR),
            birthdays = listOf("Анна Смирнова", "Ёлкин Иван"),
        )

        val response = GenerateGreetingTextCommandImpl(port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        val request = port.executeCalls.single()
        assertEquals(DATE, request.date)
        assertEquals(listOf(Holiday.NEW_YEAR.title, Holiday.PROGRAMMER_DAY.title), request.holidays)
        assertEquals(listOf("Ёлкин Иван", "Анна Смирнова"), request.people)
        assertEquals("generated greeting", response.text.value)
    }

    @Test
    fun `an ai failure falls back to the template and warns`() {
        val port = FakeGenerateGreetingTextPort(failure = IllegalStateException("llm is down"))
        val plan = GreetingPlan(
            peerId = FakeVoProvider.createPeerId(2_000_000_001),
            holidays = listOf(Holiday.NEW_YEAR),
            birthdays = listOf("Иван Петров"),
        )

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            val response = GenerateGreetingTextCommandImpl(port).execute(
                FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
            )

            assertEquals("С Новым годом! С днём рождения, Иван Петров!", response.text.value)
            assertTrue(capture.messages(Level.WARNING).isNotEmpty())
        }
    }

    @ParameterizedTest
    @MethodSource("blankTexts")
    fun `blank ai text falls back to the template and warns`(text: String) {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText(text),
            ),
        )
        val plan = GreetingPlan(
            peerId = FakeVoProvider.createPeerId(2_000_000_001),
            holidays = listOf(Holiday.PROGRAMMER_DAY),
            birthdays = emptyList(),
        )

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            val response = GenerateGreetingTextCommandImpl(port).execute(
                FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
            )

            assertEquals("С Днём программиста!", response.text.value)
            assertTrue(capture.messages(Level.WARNING).any { it.contains("blank") })
        }
    }

    @ParameterizedTest
    @MethodSource("templateCases")
    fun `the template renders every event combination in enum order`(case: TemplateCase) {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText("   "),
            ),
        )
        val plan = GreetingPlan(
            peerId = FakeVoProvider.createPeerId(2_000_000_001),
            holidays = case.holidays,
            birthdays = case.birthdays,
        )

        val response = GenerateGreetingTextCommandImpl(port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        assertEquals(case.expected, response.text.value)
        assertEquals(case.holidays.sortedBy { it.ordinal }.map { it.title }, port.executeCalls.single().holidays)
    }

    data class TemplateCase(
        val holidays: List<Holiday>,
        val birthdays: List<String>,
        val expected: String,
    )

    companion object {
        private val DATE: LocalDate = LocalDate.parse("2027-01-01")

        @JvmStatic
        fun blankTexts(): Stream<String> = Stream.of("", "   ")

        @JvmStatic
        fun templateCases(): Stream<TemplateCase> = Stream.of(
            TemplateCase(listOf(Holiday.NEW_YEAR), emptyList(), "С Новым годом!"),
            TemplateCase(listOf(Holiday.PROGRAMMER_DAY), emptyList(), "С Днём программиста!"),
            TemplateCase(
                emptyList(),
                listOf("Ёлкин Иван", "Анна Смирнова"),
                "С днём рождения, Ёлкин Иван, Анна Смирнова!",
            ),
            TemplateCase(
                listOf(Holiday.PROGRAMMER_DAY, Holiday.NEW_YEAR),
                emptyList(),
                "С Новым годом! С Днём программиста!",
            ),
            TemplateCase(
                listOf(Holiday.NEW_YEAR),
                listOf("Иван Петров"),
                "С Новым годом! С днём рождения, Иван Петров!",
            ),
        )
    }
}
