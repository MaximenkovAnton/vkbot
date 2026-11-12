package com.simarel.vkbot.ai.command.knowledge

import com.simarel.vkbot.ai.domain.exception.UserKnowledgeSummaryException
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindUserKnowledgePort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindUserKnowledgeSummaryStatePort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeUserKnowledgeSummaryPort
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

private const val USER_ID = 7_600_001L
private const val THRESHOLD = 20
private const val BELOW_THRESHOLD = THRESHOLD - 1
private const val MAX_SUMMARY_LENGTH = 2000
private const val FOOTBALL = "футбол"
private const val SUMMARY_TEXT = "Иван увлекается футболом и историей."
private val INTERVAL: Duration = Duration.ofDays(14)

class GenerateUserKnowledgeSummaryCommandImplTest {

    @Test
    fun `generatesAndSavesSummaryOnFirstRun`() {
        val llm = FakeUserKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }
        val save = RecordingSaveUserKnowledgeSummaryPort()

        command(state = state(), find = findKnowledge(confirmedFact()), llm = llm, save = save).execute(request())

        assertEquals(1, llm.executeCalls.size)
        assertEquals("Интересы и увлечения:\n- футбол", llm.executeCalls.single().facts)
        assertEquals(
            FakePersistencePortProvider.createSaveUserKnowledgeSummaryRequest(
                userId = USER_ID,
                summaryText = SUMMARY_TEXT,
            ),
            save.executeCalls.single(),
        )
    }

    @Test
    fun `clipsOverlongSummaryTo2000Chars`() {
        val llm = FakeUserKnowledgeSummaryPort().apply { text = "а".repeat(MAX_SUMMARY_LENGTH + 500) }
        val save = RecordingSaveUserKnowledgeSummaryPort()

        command(state = state(), find = findKnowledge(confirmedFact()), llm = llm, save = save).execute(request())

        val saved = save.executeCalls.single()
        assertEquals(MAX_SUMMARY_LENGTH, saved.summaryText.length)
        assertEquals("а".repeat(MAX_SUMMARY_LENGTH), saved.summaryText)
    }

    @Test
    fun `generatesWhenFactThresholdReached`() {
        val llm = FakeUserKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }

        command(
            state = state(
                summarizedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                factsSinceSummary = THRESHOLD,
            ),
            find = findKnowledge(confirmedFact()),
            llm = llm,
            save = RecordingSaveUserKnowledgeSummaryPort(),
        ).execute(request())

        assertEquals(1, llm.executeCalls.size)
    }

    @Test
    fun `skipsBelowThresholdAndInterval`() {
        val llm = FakeUserKnowledgeSummaryPort()
        val save = RecordingSaveUserKnowledgeSummaryPort()

        SystemLoggerCapture(GenerateUserKnowledgeSummaryCommandImpl::class.java.name).use { capture ->
            command(
                state = state(
                    summarizedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                    factsSinceSummary = BELOW_THRESHOLD,
                ),
                find = findKnowledge(confirmedFact()),
                llm = llm,
                save = save,
            ).execute(request())

            assertTrue(
                capture.messages(Level.FINE).any {
                    it.contains("userId=7600001") && it.contains("below threshold and interval")
                },
                capture.messages(Level.FINE).toString(),
            )
        }
        assertTrue(llm.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `generatesAtIntervalBoundary`() {
        val llm = FakeUserKnowledgeSummaryPort().apply { text = SUMMARY_TEXT }

        command(
            state = state(
                summarizedAt = OffsetDateTime.now(ZoneOffset.UTC).minus(INTERVAL),
                factsSinceSummary = 1,
            ),
            find = findKnowledge(confirmedFact()),
            llm = llm,
            save = RecordingSaveUserKnowledgeSummaryPort(),
        ).execute(request())

        assertEquals(1, llm.executeCalls.size)
    }

    @Test
    fun `skipsBeforeIntervalElapsed`() {
        val llm = FakeUserKnowledgeSummaryPort()
        val save = RecordingSaveUserKnowledgeSummaryPort()

        command(
            state = state(
                summarizedAt = OffsetDateTime.now(ZoneOffset.UTC).minus(INTERVAL).plusDays(1),
                factsSinceSummary = 1,
            ),
            find = findKnowledge(confirmedFact()),
            llm = llm,
            save = save,
        ).execute(request())

        assertTrue(llm.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `skipsWithoutFacts`() {
        assertSkipsWithoutLlmOrSave(findKnowledge())
    }

    @Test
    fun `skipsWhenAllFactsStale`() {
        assertSkipsWithoutLlmOrSave(
            findKnowledge(FakeModelProvider.createUserFact(userId = USER_ID, value = FOOTBALL, stale = true)),
        )
    }

    @Test
    fun `rethrowsLlmFailureWithoutSaving`() {
        val llm = FakeUserKnowledgeSummaryPort().apply {
            failure = UserKnowledgeSummaryException("generation failed", null)
        }
        val save = RecordingSaveUserKnowledgeSummaryPort()

        assertThrows<UserKnowledgeSummaryException> {
            command(state = state(), find = findKnowledge(confirmedFact()), llm = llm, save = save).execute(request())
        }

        assertEquals(1, llm.executeCalls.size)
        assertTrue(save.executeCalls.isEmpty())
    }

    @Test
    fun `logsOnlyIdentifiersAndCounts`() {
        val facts = listOf(
            confirmedFact(value = FOOTBALL),
            confirmedFact(value = "история"),
            confirmedFact(value = "бег"),
            confirmedFact(value = "Python"),
            confirmedFact(value = "друг Сергей"),
        )

        SystemLoggerCapture(GenerateUserKnowledgeSummaryCommandImpl::class.java.name).use { capture ->
            command(
                state = state(),
                find = findKnowledge(*facts.toTypedArray()),
                llm = FakeUserKnowledgeSummaryPort().apply { text = SUMMARY_TEXT },
                save = RecordingSaveUserKnowledgeSummaryPort(),
            ).execute(request())

            val info = capture.messages(Level.INFO)
            assertTrue(
                info.any {
                    it.contains("userId=7600001") &&
                        it.contains("facts=5") &&
                        it.contains("length=${SUMMARY_TEXT.length}")
                },
                info.toString(),
            )
            info.forEach { line ->
                assertTrue(!line.contains(FOOTBALL), line)
                assertTrue(!line.contains("Иван"), line)
                assertTrue(!line.contains(SUMMARY_TEXT), line)
            }
        }
    }

    private fun assertSkipsWithoutLlmOrSave(find: FakeFindUserKnowledgePort) {
        val llm = FakeUserKnowledgeSummaryPort()
        val save = RecordingSaveUserKnowledgeSummaryPort()

        SystemLoggerCapture(GenerateUserKnowledgeSummaryCommandImpl::class.java.name).use { capture ->
            command(state = state(), find = find, llm = llm, save = save).execute(request())

            assertTrue(
                capture.messages(Level.FINE).any { it.contains("no fresh facts") },
                capture.messages(Level.FINE).toString(),
            )
        }
        assertTrue(llm.executeCalls.isEmpty())
        assertTrue(save.executeCalls.isEmpty())
    }

    private fun command(
        state: FakeFindUserKnowledgeSummaryStatePort,
        find: FakeFindUserKnowledgePort,
        llm: FakeUserKnowledgeSummaryPort,
        save: SaveUserKnowledgeSummaryPort,
    ) = GenerateUserKnowledgeSummaryCommandImpl(
        findUserKnowledgeSummaryStatePort = state,
        findUserKnowledgePort = find,
        userKnowledgeSummaryPort = llm,
        saveUserKnowledgeSummaryPort = save,
        threshold = THRESHOLD,
        interval = INTERVAL,
    )

    private fun request() = FakeKnowledgeProvider.createGenerateUserKnowledgeSummaryCommandRequest(
        payload = FakeEventProvider.createUserKnowledgeSummaryRequestedPayload(userId = USER_ID),
    )

    private fun state(
        summarizedAt: OffsetDateTime? = null,
        factsSinceSummary: Int = 0,
    ) = FakeFindUserKnowledgeSummaryStatePort().apply {
        this.summarizedAt = summarizedAt
        this.factsSinceSummary = factsSinceSummary
    }

    private fun findKnowledge(vararg facts: UserFact) = FakeFindUserKnowledgePort().apply {
        knowledge = mapOf(
            FakeVoProvider.createFromId(USER_ID) to FakeModelProvider.createUserKnowledge(facts = facts.toList()),
        )
    }

    private fun confirmedFact(
        value: String = FOOTBALL,
    ): UserFact = FakeModelProvider.createUserFact(userId = USER_ID, value = value)

    private class RecordingSaveUserKnowledgeSummaryPort : SaveUserKnowledgeSummaryPort {
        val executeCalls = ConcurrentLinkedQueue<SaveUserKnowledgeSummaryPort.Request>()

        override fun execute(
            request: SaveUserKnowledgeSummaryPort.Request,
        ): SaveUserKnowledgeSummaryPort.Response {
            executeCalls.add(request)
            return FakePersistencePortProvider.createSaveUserKnowledgeSummaryResponse()
        }
    }
}
