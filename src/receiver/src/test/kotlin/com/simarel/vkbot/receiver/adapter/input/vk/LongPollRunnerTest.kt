package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.VkEventPublishException
import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.fixtures.FakeLongPollPortProvider
import com.simarel.vkbot.receiver.fixtures.FakeMessageMapper
import com.simarel.vkbot.receiver.fixtures.port.FakeCheckLongPollPort
import com.simarel.vkbot.receiver.fixtures.port.FakeFindLongPollTsPort
import com.simarel.vkbot.receiver.fixtures.port.FakeGetLongPollServerPort
import com.simarel.vkbot.receiver.fixtures.port.FakeReceiveMessageInputPort
import com.simarel.vkbot.receiver.fixtures.port.FakeSaveLongPollTsPort
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LongPollRunnerTest {

    @Test
    fun `pollOnce starts from the saved ts instead of the session ts`() {
        val sessions = FakeGetLongPollServerPort()
        val checks = FakeCheckLongPollPort()
        val findTs = FakeFindLongPollTsPort(FakeLongPollPortProvider.createLongPollTs("ts-saved"))
        val saveTs = FakeSaveLongPollTsPort()

        runner(sessions = sessions, checks = checks, findTs = findTs, saveTs = saveTs).pollOnce()

        assertEquals("ts-saved", checks.executeCalls.single().ts)
        assertEquals(1, findTs.executeCalls.size)
        assertEquals("ts-2", saveTs.executeCalls.single().ts.value)
    }

    @Test
    fun `message_new is published before the ts is saved`() {
        val journal = mutableListOf<String>()
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-next",
                    updates = listOf(MESSAGE_NEW_UPDATE),
                ),
            ),
        )
        val receiveMessage = FakeReceiveMessageInputPort(journal = journal)
        val saveTs = FakeSaveLongPollTsPort(journal = journal)
        val messageMapper = FakeMessageMapper()

        runner(
            checks = checks,
            receiveMessage = receiveMessage,
            saveTs = saveTs,
            messageMapper = messageMapper,
        ).pollOnce()

        assertEquals(listOf("publish", "save"), journal)
        assertEquals(1, messageMapper.toDomainCalls.size)
        val request = receiveMessage.executeCalls.single()
        assertEquals(VkUpdateType.MESSAGE_NEW, request.type)
        assertNotNull(request.message)
        assertEquals("ts-next", saveTs.executeCalls.single().ts.value)
    }

    @Test
    fun `empty batch saves the ts`() {
        val checks = FakeCheckLongPollPort(listOf(FakeLongPollPortProvider.createCheckResponse(ts = "ts-empty")))
        val saveTs = FakeSaveLongPollTsPort()

        runner(checks = checks, saveTs = saveTs).pollOnce()

        assertEquals("ts-empty", saveTs.executeCalls.single().ts.value)
    }

    @Test
    fun `unknown update is skipped and the ts is saved`() {
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-unknown",
                    updates = listOf("""{"type":"confirmation","object":{}}"""),
                ),
            ),
        )
        val receiveMessage = FakeReceiveMessageInputPort()
        val messageMapper = FakeMessageMapper()
        val saveTs = FakeSaveLongPollTsPort()

        runner(
            checks = checks,
            receiveMessage = receiveMessage,
            saveTs = saveTs,
            messageMapper = messageMapper,
        ).pollOnce()

        assertTrue(receiveMessage.executeCalls.isEmpty())
        assertTrue(messageMapper.toDomainCalls.isEmpty())
        assertEquals("ts-unknown", saveTs.executeCalls.single().ts.value)
    }

    @Test
    fun `unreadable update is skipped and the ts is saved`() {
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-broken",
                    updates = listOf("not a json"),
                ),
            ),
        )
        val receiveMessage = FakeReceiveMessageInputPort()
        val saveTs = FakeSaveLongPollTsPort()

        runner(checks = checks, receiveMessage = receiveMessage, saveTs = saveTs).pollOnce()

        assertTrue(receiveMessage.executeCalls.isEmpty())
        assertEquals("ts-broken", saveTs.executeCalls.single().ts.value)
    }

    @Test
    fun `update that fails mapping is skipped and the ts is saved`() {
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-mapped",
                    updates = listOf(MESSAGE_NEW_UPDATE),
                ),
            ),
        )
        val messageMapper = FakeMessageMapper()
        messageMapper.failure = IllegalStateException("bad update")
        val receiveMessage = FakeReceiveMessageInputPort()
        val saveTs = FakeSaveLongPollTsPort()

        runner(
            checks = checks,
            receiveMessage = receiveMessage,
            saveTs = saveTs,
            messageMapper = messageMapper,
        ).pollOnce()

        assertTrue(receiveMessage.executeCalls.isEmpty())
        assertEquals("ts-mapped", saveTs.executeCalls.single().ts.value)
    }

    @Test
    fun `failed one accepts and saves the returned ts`() {
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-outdated",
                    failure = LongPollFailure.TS_OUTDATED,
                ),
                FakeLongPollPortProvider.createCheckResponse(ts = "ts-3"),
            ),
        )
        val sessions = FakeGetLongPollServerPort()
        val saveTs = FakeSaveLongPollTsPort()
        val runner = runner(sessions = sessions, checks = checks, saveTs = saveTs)

        runner.pollOnce()

        assertEquals("ts-outdated", saveTs.executeCalls.single().ts.value)

        runner.pollOnce()

        assertEquals("ts-outdated", checks.executeCalls.toList()[1].ts)
        assertEquals(1, sessions.executeCalls.size)
    }

    @Test
    fun `failed two requests a new session and keeps the ts`() {
        val sessions = FakeGetLongPollServerPort(
            listOf(
                FakeLongPollPortProvider.createSession(ts = "ts-session-1"),
                FakeLongPollPortProvider.createSession(ts = "ts-session-2"),
            ),
        )
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(ts = null, failure = LongPollFailure.KEY_EXPIRED),
                FakeLongPollPortProvider.createCheckResponse(ts = "ts-2"),
            ),
        )
        val saveTs = FakeSaveLongPollTsPort()
        val runner = runner(sessions = sessions, checks = checks, saveTs = saveTs)

        runner.pollOnce()

        assertEquals(1, sessions.executeCalls.size)
        assertTrue(saveTs.executeCalls.isEmpty())

        runner.pollOnce()

        val requests = checks.executeCalls.toList()
        assertEquals(2, requests.size)
        assertEquals(requests[0].ts, requests[1].ts)
        assertEquals(2, sessions.executeCalls.size)
    }

    @Test
    fun `failed three requests a new session and takes its ts`() {
        val sessions = FakeGetLongPollServerPort(
            listOf(
                FakeLongPollPortProvider.createSession(ts = "ts-session-1"),
                FakeLongPollPortProvider.createSession(ts = "ts-session-new"),
            ),
        )
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(ts = null, failure = LongPollFailure.INFO_LOST),
                FakeLongPollPortProvider.createCheckResponse(ts = "ts-2"),
            ),
        )
        val saveTs = FakeSaveLongPollTsPort()
        val runner = runner(sessions = sessions, checks = checks, saveTs = saveTs)

        runner.pollOnce()

        assertEquals(2, sessions.executeCalls.size)
        assertTrue(saveTs.executeCalls.isEmpty())

        runner.pollOnce()

        assertEquals("ts-session-new", checks.executeCalls.toList()[1].ts)
        assertEquals("ts-2", saveTs.executeCalls.single().ts.value)
        assertEquals(2, sessions.executeCalls.size)
    }

    @Test
    fun `publish failure is rethrown and the ts is not saved`() {
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-2",
                    updates = listOf(MESSAGE_NEW_UPDATE),
                ),
            ),
        )
        val receiveMessage = FakeReceiveMessageInputPort()
        receiveMessage.failure = IllegalStateException("broker is down")
        val saveTs = FakeSaveLongPollTsPort()

        val thrown = assertThrows(VkEventPublishException::class.java) {
            runner(checks = checks, receiveMessage = receiveMessage, saveTs = saveTs).pollOnce()
        }

        assertEquals("broker is down", thrown.cause?.message)
        assertTrue(saveTs.executeCalls.isEmpty())
    }

    @Test
    fun `disabled runner does not start polling`() {
        val sessions = FakeGetLongPollServerPort()
        val runner = runner(sessions = sessions, enabled = false)

        runner.start(StartupEvent())

        assertNull(runner.pollThread)
        assertTrue(sessions.executeCalls.isEmpty())
    }

    @Test
    fun `poll starts from the session ts when nothing is saved`() {
        val sessions = FakeGetLongPollServerPort()
        val checks = FakeCheckLongPollPort()

        runner(sessions = sessions, checks = checks).pollOnce()

        assertEquals(FakeLongPollPortProvider.createSession().ts, checks.executeCalls.single().ts)
    }

    @Test
    fun `loop grows the pause between failed polls and survives them`() {
        val pauses = mutableListOf<Long>()
        val checks = FakeCheckLongPollPort()
        checks.failure = IllegalStateException("network is down")
        val runner = runner(checks = checks, pauses = pauses, stopAfterPauses = 3)

        runner.start(StartupEvent())
        runner.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

        assertEquals(listOf(5_000L, 10_000L, 20_000L), pauses)
        assertEquals(3, checks.executeCalls.size)
    }

    @Test
    fun `loop pauses five seconds after a publish failure`() {
        val pauses = mutableListOf<Long>()
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-2",
                    updates = listOf(MESSAGE_NEW_UPDATE),
                ),
            ),
        )
        val receiveMessage = FakeReceiveMessageInputPort()
        receiveMessage.failure = IllegalStateException("broker is down")
        val saveTs = FakeSaveLongPollTsPort()
        val runner = runner(
            checks = checks,
            receiveMessage = receiveMessage,
            saveTs = saveTs,
            pauses = pauses,
            stopAfterPauses = 1,
        )

        runner.start(StartupEvent())
        runner.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

        assertEquals(listOf(5_000L), pauses)
        assertTrue(saveTs.executeCalls.isEmpty())
    }

    @Suppress("LongParameterList")
    private fun runner(
        sessions: FakeGetLongPollServerPort = FakeGetLongPollServerPort(),
        checks: FakeCheckLongPollPort = FakeCheckLongPollPort(),
        receiveMessage: FakeReceiveMessageInputPort = FakeReceiveMessageInputPort(),
        findTs: FakeFindLongPollTsPort = FakeFindLongPollTsPort(),
        saveTs: FakeSaveLongPollTsPort = FakeSaveLongPollTsPort(),
        messageMapper: FakeMessageMapper = FakeMessageMapper(),
        enabled: Boolean = true,
        pauses: MutableList<Long>? = null,
        stopAfterPauses: Int = Int.MAX_VALUE,
    ): LongPollRunner = object : LongPollRunner(
        getLongPollServerPort = sessions,
        checkLongPollPort = checks,
        receiveMessageInputPort = receiveMessage,
        findLongPollTsPort = findTs,
        saveLongPollTsPort = saveTs,
        messageMapper = messageMapper,
        groupId = GROUP_ID,
        enabled = enabled,
    ) {
        override fun pause(millis: Long) {
            val recorded = pauses ?: return
            recorded.add(millis)
            if (recorded.size >= stopAfterPauses) stop(ShutdownEvent())
        }
    }

    private companion object {
        const val GROUP_ID = 1L
        const val POLL_THREAD_TIMEOUT_MILLIS = 5_000L
        const val MESSAGE_NEW_UPDATE =
            """{"type":"message_new","object":{"message":{"text":"hello"}},"group_id":1}"""
    }
}
