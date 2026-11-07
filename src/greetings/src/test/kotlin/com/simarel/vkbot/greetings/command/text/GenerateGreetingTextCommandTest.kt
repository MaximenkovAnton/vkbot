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
    fun `a holiday request carries the holiday title and no people`() {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText("generated greeting"),
            ),
        )
        val plan = FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.NEW_YEAR)

        val response = GenerateGreetingTextCommandImpl(port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        val request = port.executeCalls.single()
        assertEquals(DATE, request.date)
        assertEquals(Holiday.NEW_YEAR.title, request.occasion)
        assertTrue(request.people.isEmpty())
        assertEquals("generated greeting", response.text.value)
    }

    @Test
    fun `a birthday request carries the occasion and the sorted names`() {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText("generated greeting"),
            ),
        )
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            people = listOf("Анна Смирнова", "Ёлкин Иван"),
        )

        GenerateGreetingTextCommandImpl(port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        val request = port.executeCalls.single()
        assertEquals(DATE, request.date)
        assertEquals("День рождения", request.occasion)
        assertEquals(listOf("Ёлкин Иван", "Анна Смирнова"), request.people)
    }

    @Test
    fun `an ai failure falls back to the template and warns`() {
        val port = FakeGenerateGreetingTextPort(failure = IllegalStateException("llm is down"))
        val plan = FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.NEW_YEAR)

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            val response = GenerateGreetingTextCommandImpl(port).execute(
                FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
            )

            assertEquals("С Новым годом!", response.text.value)
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
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(people = listOf("Иван Петров"))

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            val response = GenerateGreetingTextCommandImpl(port).execute(
                FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
            )

            assertEquals("С днём рождения, Иван Петров!", response.text.value)
            assertTrue(capture.messages(Level.WARNING).any { it.contains("blank") })
        }
    }

    @ParameterizedTest
    @MethodSource("templateCases")
    fun `the template renders every plan kind and the request carries its occasion`(case: TemplateCase) {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText("   "),
            ),
        )

        val response = GenerateGreetingTextCommandImpl(port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, case.plan),
        )

        assertEquals(case.expected, response.text.value)
        assertEquals(case.expectedOccasion, port.executeCalls.single().occasion)
        assertEquals(case.expectedPeople, port.executeCalls.single().people)
    }

    data class TemplateCase(
        val plan: GreetingPlan,
        val expected: String,
        val expectedOccasion: String,
        val expectedPeople: List<String>,
    )

    companion object {
        private val DATE: LocalDate = LocalDate.parse("2027-01-01")

        @JvmStatic
        fun blankTexts(): Stream<String> = Stream.of("", "   ")

        @JvmStatic
        fun templateCases(): Stream<TemplateCase> = Stream.of(
            TemplateCase(
                plan = FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.NEW_YEAR),
                expected = "С Новым годом!",
                expectedOccasion = Holiday.NEW_YEAR.title,
                expectedPeople = emptyList(),
            ),
            TemplateCase(
                plan = FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.PROGRAMMER_DAY),
                expected = "С Днём программиста!",
                expectedOccasion = Holiday.PROGRAMMER_DAY.title,
                expectedPeople = emptyList(),
            ),
            TemplateCase(
                plan = FakeGreetingProvider.createBirthdayGreetingPlan(
                    people = listOf("Ёлкин Иван", "Анна Смирнова"),
                ),
                expected = "С днём рождения, Ёлкин Иван, Анна Смирнова!",
                expectedOccasion = "День рождения",
                expectedPeople = listOf("Ёлкин Иван", "Анна Смирнова"),
            ),
        )
    }
}
