package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.VkEventPublishException
import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.fixtures.FakeLongPollPortProvider
import com.simarel.vkbot.receiver.fixtures.FakeMessageMapper
import com.simarel.vkbot.receiver.fixtures.port.FakeAcquireLongPollLeasePort
import com.simarel.vkbot.receiver.fixtures.port.FakeCheckLongPollPort
import com.simarel.vkbot.receiver.fixtures.port.FakeFindLongPollTsPort
import com.simarel.vkbot.receiver.fixtures.port.FakeGetLongPollServerPort
import com.simarel.vkbot.receiver.fixtures.port.FakeReceiveMessageInputPort
import com.simarel.vkbot.receiver.fixtures.port.FakeReleaseLongPollLeasePort
import com.simarel.vkbot.receiver.fixtures.port.FakeSaveLongPollTsPort
import com.simarel.vkbot.receiver.test.SystemLoggerCapture
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.logging.Level

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
    fun `the raw update is passed to the use case`() {
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(
                    ts = "ts-raw",
                    updates = listOf(MESSAGE_NEW_UPDATE),
                ),
            ),
        )
        val receiveMessage = FakeReceiveMessageInputPort()

        runner(checks = checks, receiveMessage = receiveMessage).pollOnce()

        val request = receiveMessage.executeCalls.single()
        assertEquals(MESSAGE_NEW_UPDATE, request.rawUpdate)
        assertEquals(VkUpdateType.MESSAGE_NEW, request.type)
        assertNotNull(request.message)
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
    fun `loop acquires the lease with a 40 second ttl before every check`() {
        val journal = mutableListOf<String>()
        val acquire = FakeAcquireLongPollLeasePort(journal = journal)
        val checks = FakeCheckLongPollPort(journal = journal)
        checks.failure = IllegalStateException("network is down")
        val runner = runner(
            checks = checks,
            acquireLease = acquire,
            pauses = mutableListOf(),
            stopAfterPauses = 1,
        )

        runner.start(StartupEvent())
        runner.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

        assertEquals(listOf("acquire", "check"), journal)
        assertEquals(Duration.ofSeconds(40), acquire.executeCalls.single().ttl)
        assertTrue(OWNER_PATTERN.matches(acquire.executeCalls.single().owner))
        assertEquals(1, checks.executeCalls.size)
    }

    @Test
    fun `standby pauses two seconds and never polls`() {
        val acquire = FakeAcquireLongPollLeasePort(acquiredSequence = listOf(false))
        val checks = FakeCheckLongPollPort()
        val saveTs = FakeSaveLongPollTsPort()
        val pauses = mutableListOf<Long>()
        val runner = runner(
            checks = checks,
            saveTs = saveTs,
            acquireLease = acquire,
            pauses = pauses,
            stopAfterPauses = 1,
        )

        runner.start(StartupEvent())
        runner.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

        assertEquals(listOf(2_000L), pauses)
        assertTrue(checks.executeCalls.isEmpty())
        assertTrue(saveTs.executeCalls.isEmpty())
        assertTrue(!acquire.executeCalls.isEmpty())
    }

    @Test
    fun `stop releases the lease of the owner`() {
        val acquire = FakeAcquireLongPollLeasePort()
        val release = FakeReleaseLongPollLeasePort()
        val checks = FakeCheckLongPollPort()
        checks.failure = IllegalStateException("network is down")
        val runner = runner(
            checks = checks,
            acquireLease = acquire,
            releaseLease = release,
            pauses = mutableListOf(),
            stopAfterPauses = 1,
        )

        runner.start(StartupEvent())
        runner.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

        assertEquals(1, release.executeCalls.size)
        assertEquals(acquire.executeCalls.first().owner, release.executeCalls.single().owner)
        assertTrue(OWNER_PATTERN.matches(release.executeCalls.single().owner))
    }

    @Test
    fun `takeover reads the ts from the stored position`() {
        val acquire = FakeAcquireLongPollLeasePort(acquiredSequence = listOf(true, false, true, false))
        val checks = FakeCheckLongPollPort(
            listOf(
                FakeLongPollPortProvider.createCheckResponse(ts = "ts-own"),
                FakeLongPollPortProvider.createCheckResponse(ts = null),
            ),
        )
        val findTs = FakeFindLongPollTsPort(FakeLongPollPortProvider.createLongPollTs("ts-saved"))
        val saveTs = FakeSaveLongPollTsPort()
        val runner = runner(
            checks = checks,
            findTs = findTs,
            saveTs = saveTs,
            acquireLease = acquire,
            pauses = mutableListOf(),
            stopAfterPauses = 2,
        )

        runner.start(StartupEvent())
        runner.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

        assertEquals(2, checks.executeCalls.size)
        assertEquals("ts-saved", checks.executeCalls.toList()[1].ts)
        assertEquals("ts-own", saveTs.executeCalls.single().ts.value)
        assertEquals(2, findTs.executeCalls.size)
    }

    @Test
    fun `leadership transitions are logged without pii`() {
        SystemLoggerCapture(LongPollRunner::class.java.name).use { capture ->
            val leaderAcquire = FakeAcquireLongPollLeasePort()
            val leaderChecks = FakeCheckLongPollPort(
                listOf(FakeLongPollPortProvider.createCheckResponse(updates = listOf(MESSAGE_NEW_UPDATE))),
            )
            leaderChecks.failure = IllegalStateException("network is down")
            val leader = runner(
                checks = leaderChecks,
                acquireLease = leaderAcquire,
                pauses = mutableListOf(),
                stopAfterPauses = 1,
            )
            leader.start(StartupEvent())
            leader.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

            val standbyAcquire = FakeAcquireLongPollLeasePort(acquiredSequence = listOf(false))
            val standby = runner(
                acquireLease = standbyAcquire,
                pauses = mutableListOf(),
                stopAfterPauses = 1,
            )
            standby.start(StartupEvent())
            standby.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

            val transitionsAcquire = FakeAcquireLongPollLeasePort(acquiredSequence = listOf(false, true, false))
            val transitions = runner(
                checks = FakeCheckLongPollPort(
                    listOf(
                        FakeLongPollPortProvider.createCheckResponse(updates = listOf(MESSAGE_NEW_UPDATE)),
                        FakeLongPollPortProvider.createCheckResponse(updates = listOf(MESSAGE_NEW_UPDATE)),
                    ),
                ),
                acquireLease = transitionsAcquire,
                pauses = mutableListOf(),
                stopAfterPauses = 2,
            )
            transitions.start(StartupEvent())
            transitions.pollThread?.join(POLL_THREAD_TIMEOUT_MILLIS)

            val owner = leaderAcquire.executeCalls.first().owner
            val standbyOwner = standbyAcquire.executeCalls.first().owner
            val transitionsOwner = transitionsAcquire.executeCalls.first().owner
            assertLogged(capture, Level.INFO, "VK long poll lease acquired, polling as leader (owner=$owner)")
            assertLogged(
                capture,
                Level.INFO,
                "VK long poll lease is held by another instance, standing by (owner=$standbyOwner)",
            )
            assertLogged(capture, Level.WARNING, "VK long poll lease lost, standing by (owner=$transitionsOwner)")
            assertLogged(
                capture,
                Level.WARNING,
                "VK long poll lease taken over, polling as leader (owner=$transitionsOwner)",
            )
            assertLogged(capture, Level.INFO, "VK long poll lease released (owner=$owner)")
            assertTrue(capture.records.none { it.message?.contains("hello") == true })
        }
    }

    @Test
    fun `release failure is logged and shutdown continues`() {
        val release = FakeReleaseLongPollLeasePort()
        release.failure = IllegalStateException("datasource is closed")
        val runner = runner(releaseLease = release)
        SystemLoggerCapture(LongPollRunner::class.java.name).use { capture ->
            assertDoesNotThrow { runner.stop(ShutdownEvent()) }

            assertEquals(
                Level.WARNING,
                capture.records.single {
                    it.message == "VK long poll lease release failed (java.lang.IllegalStateException)"
                }.level,
            )
        }
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

    private fun assertLogged(capture: SystemLoggerCapture, level: Level, message: String) {
        assertEquals(level, capture.records.single { it.message == message }.level)
    }

    @Suppress("LongParameterList")
    private fun runner(
        sessions: FakeGetLongPollServerPort = FakeGetLongPollServerPort(),
        checks: FakeCheckLongPollPort = FakeCheckLongPollPort(),
        receiveMessage: FakeReceiveMessageInputPort = FakeReceiveMessageInputPort(),
        findTs: FakeFindLongPollTsPort = FakeFindLongPollTsPort(),
        saveTs: FakeSaveLongPollTsPort = FakeSaveLongPollTsPort(),
        acquireLease: FakeAcquireLongPollLeasePort = FakeAcquireLongPollLeasePort(),
        releaseLease: FakeReleaseLongPollLeasePort = FakeReleaseLongPollLeasePort(),
        messageMapper: FakeMessageMapper = FakeMessageMapper(),
        enabled: Boolean = true,
        leaseTtl: Duration = Duration.ofSeconds(40),
        standbyPause: Duration = Duration.ofSeconds(2),
        pauses: MutableList<Long>? = null,
        stopAfterPauses: Int = Int.MAX_VALUE,
    ): LongPollRunner = object : LongPollRunner(
        getLongPollServerPort = sessions,
        checkLongPollPort = checks,
        receiveMessageInputPort = receiveMessage,
        findLongPollTsPort = findTs,
        saveLongPollTsPort = saveTs,
        acquireLongPollLeasePort = acquireLease,
        releaseLongPollLeasePort = releaseLease,
        messageMapper = messageMapper,
        groupId = GROUP_ID,
        enabled = enabled,
        leaseTtl = leaseTtl,
        standbyPause = standbyPause,
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
        val OWNER_PATTERN =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
