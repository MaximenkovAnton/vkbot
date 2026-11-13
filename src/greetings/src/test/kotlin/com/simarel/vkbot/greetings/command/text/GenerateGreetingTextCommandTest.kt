package com.simarel.vkbot.greetings.command.text

import com.simarel.vkbot.greetings.domain.GreetingPlan
import com.simarel.vkbot.greetings.domain.Holiday
import com.simarel.vkbot.greetings.test.FakeFindChatKnowledgeSummary
import com.simarel.vkbot.greetings.test.FakeFindUserKnowledge
import com.simarel.vkbot.greetings.test.FakeGenerateGreetingTextPort
import com.simarel.vkbot.greetings.test.SystemLoggerCapture
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
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

        val response = command(port = port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        val request = port.executeCalls.single()
        assertEquals(DATE, request.date)
        assertEquals(Holiday.NEW_YEAR.title, request.occasion)
        assertTrue(request.people.isEmpty())
        assertEquals("generated greeting", response.text.value)
    }

    @Test
    fun `a holiday request carries the chat facts`() {
        val port = FakeGenerateGreetingTextPort()
        val chatKnowledge = FakeFindChatKnowledgeSummary(
            knowledge = FakeModelProvider.createChatKnowledge(summary = "Обсуждают футбол и дачу."),
        )

        command(port = port, chatKnowledge = chatKnowledge).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(
                DATE,
                FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.NEW_YEAR),
            ),
        )

        assertEquals(PORT_PEER_ID, chatKnowledge.executeCalls.single().peerId)
        assertEquals("Обсуждают футбол и дачу.", port.executeCalls.single().chatFacts)
        assertTrue(port.executeCalls.single().personalKnowledge.isEmpty())
    }

    @Test
    fun `a birthday request carries the occasion and the sorted people`() {
        val port = FakeGenerateGreetingTextPort(
            response = FakeGreetingPortProvider.createGenerateGreetingTextResponse(
                text = FakeVoProvider.createMessageText("generated greeting"),
            ),
        )
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            people = listOf(
                FakeModelProvider.createGreetingPerson(name = "Анна Смирнова", fromId = 102),
                FakeModelProvider.createGreetingPerson(name = "Ёлкин Иван", fromId = 101),
            ),
        )

        command(port = port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        val request = port.executeCalls.single()
        assertEquals(DATE, request.date)
        assertEquals("День рождения", request.occasion)
        assertEquals(listOf("Ёлкин Иван", "Анна Смирнова"), request.people.map { it.name })
        assertEquals(listOf(101L, 102L), request.people.map { it.fromId.value })
    }

    @Test
    fun `a birthday request carries chat facts and personal knowledge for every person`() {
        val port = FakeGenerateGreetingTextPort()
        val chatKnowledge = FakeFindChatKnowledgeSummary(
            knowledge = FakeModelProvider.createChatKnowledge(summary = "Обсуждают футбол."),
        )
        val knowledge = FakeFindUserKnowledge(
            knowledge = mapOf(
                FakeVoProvider.createFromId(101) to FakeModelProvider.createUserKnowledge(
                    facts = listOf(FakeModelProvider.createUserFact(value = "шахматы")),
                ),
                FakeVoProvider.createFromId(102) to FakeModelProvider.createUserKnowledge(
                    summary = "Анна увлекается историей.",
                    summaryFresh = true,
                ),
            ),
        )
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            people = listOf(
                FakeModelProvider.createGreetingPerson(name = "Анна Смирнова", fromId = 102),
                FakeModelProvider.createGreetingPerson(name = "Ёлкин Иван", fromId = 101),
            ),
        )

        command(port = port, chatKnowledge = chatKnowledge, knowledge = knowledge).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        val request = port.executeCalls.single()
        assertEquals("Обсуждают футбол.", request.chatFacts)
        assertEquals(listOf("Ёлкин Иван", "Анна Смирнова"), request.people.map { it.name })
        assertEquals(setOf(101L, 102L), request.personalKnowledge.keys.map { it.value }.toSet())
        assertEquals(listOf(101L, 102L), knowledge.executeCalls.single().userIds.map { it.value })
    }

    @Test
    fun `knowledge is not read when the kill switch is off`() {
        val port = FakeGenerateGreetingTextPort()
        val chatKnowledge = FakeFindChatKnowledgeSummary()
        val knowledge = FakeFindUserKnowledge()
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            people = listOf(FakeModelProvider.createGreetingPerson(name = "Иван Петров", fromId = 101)),
        )

        command(
            port = port,
            chatKnowledge = chatKnowledge,
            knowledge = knowledge,
            knowledgeEnabled = false,
        ).execute(FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan))

        assertTrue(chatKnowledge.executeCalls.isEmpty())
        assertTrue(knowledge.executeCalls.isEmpty())
        val request = port.executeCalls.single()
        assertEquals(null, request.chatFacts)
        assertTrue(request.personalKnowledge.isEmpty())
    }

    @Test
    fun `a chat knowledge failure is fail open and logs no values`() {
        val port = FakeGenerateGreetingTextPort()
        val chatKnowledge = FakeFindChatKnowledgeSummary(
            knowledge = FakeModelProvider.createChatKnowledge(summary = "секретный факт"),
            failure = IllegalStateException("boom"),
        )
        val plan = FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.NEW_YEAR)

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            command(port = port, chatKnowledge = chatKnowledge).execute(
                FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
            )

            assertEquals(null, port.executeCalls.single().chatFacts)
            val warning = capture.messages(Level.WARNING).single()
            assertTrue(warning.contains("chat knowledge read failed"))
            assertTrue(warning.contains("IllegalStateException"))
            assertTrue(!warning.contains("секретный факт"))
        }
    }

    @Test
    fun `a personal knowledge failure is fail open`() {
        val port = FakeGenerateGreetingTextPort()
        val knowledge = FakeFindUserKnowledge(failure = IllegalStateException("boom"))
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            people = listOf(FakeModelProvider.createGreetingPerson(name = "Иван Петров", fromId = 101)),
        )

        command(port = port, knowledge = knowledge).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, plan),
        )

        assertTrue(port.executeCalls.single().personalKnowledge.isEmpty())
        assertTrue(port.executeCalls.single().chatFacts == null)
    }

    @Test
    fun `an ai failure falls back to the template and warns`() {
        val port = FakeGenerateGreetingTextPort(failure = IllegalStateException("llm is down"))
        val plan = FakeGreetingProvider.createHolidayGreetingPlan(holiday = Holiday.NEW_YEAR)

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            val response = command(port = port).execute(
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
        val plan = FakeGreetingProvider.createBirthdayGreetingPlan(
            people = listOf(FakeModelProvider.createGreetingPerson(name = "Иван Петров", fromId = 101)),
        )

        SystemLoggerCapture(GenerateGreetingTextCommandImpl::class.java.name).use { capture ->
            val response = command(port = port).execute(
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

        val response = command(port = port).execute(
            FakeGreetingProvider.createGenerateGreetingTextCommandRequest(DATE, case.plan),
        )

        assertEquals(case.expected, response.text.value)
        assertEquals(case.expectedOccasion, port.executeCalls.single().occasion)
        assertEquals(case.expectedPeople, port.executeCalls.single().people.map { it.name })
    }

    private fun command(
        port: FakeGenerateGreetingTextPort = FakeGenerateGreetingTextPort(),
        chatKnowledge: FindChatKnowledgeSummaryPort = FakeFindChatKnowledgeSummary(),
        knowledge: FindUserKnowledgePort = FakeFindUserKnowledge(),
        knowledgeEnabled: Boolean = true,
    ): GenerateGreetingTextCommandImpl = GenerateGreetingTextCommandImpl(
        generateGreetingText = port,
        findChatKnowledgeSummaryPort = chatKnowledge,
        findUserKnowledgePort = knowledge,
        knowledgeEnabled = knowledgeEnabled,
    )

    data class TemplateCase(
        val plan: GreetingPlan,
        val expected: String,
        val expectedOccasion: String,
        val expectedPeople: List<String>,
    )

    companion object {
        private const val PORT_PEER_ID = 2_000_000_001L
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
                    people = listOf(
                        FakeModelProvider.createGreetingPerson(name = "Ёлкин Иван", fromId = 101),
                        FakeModelProvider.createGreetingPerson(name = "Анна Смирнова", fromId = 102),
                    ),
                ),
                expected = "С днём рождения, Ёлкин Иван, Анна Смирнова!",
                expectedOccasion = "День рождения",
                expectedPeople = listOf("Ёлкин Иван", "Анна Смирнова"),
            ),
        )
    }
}
