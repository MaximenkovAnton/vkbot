package com.simarel.vkbot.receiver.adapter.input.schedule

import com.simarel.vkbot.receiver.fixtures.FakePurgeInboxInputPortProvider
import com.simarel.vkbot.receiver.fixtures.port.FakePurgeInboxInputPort
import com.simarel.vkbot.receiver.test.SystemLoggerCapture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.logging.Level

class InboxCleanupRunnerTest {

    @Test
    fun `scheduled run purges with the current utc time`() {
        val purge = FakePurgeInboxInputPort()
        val runner = InboxCleanupRunner(purge)

        val before = OffsetDateTime.now(ZoneOffset.UTC)
        runner.runScheduled()
        val after = OffsetDateTime.now(ZoneOffset.UTC)

        assertEquals(1, purge.executeCalls.size)
        val now = purge.executeCalls.single().now
        assertEquals(ZoneOffset.UTC, now.offset)
        assertFalse(now.isBefore(before))
        assertFalse(now.isAfter(after))
    }

    @Test
    fun `cleanup logs the deleted count without pii`() {
        val purge = FakePurgeInboxInputPort(
            response = FakePurgeInboxInputPortProvider.createResponse(deleted = 2),
        )
        val runner = InboxCleanupRunner(purge)
        SystemLoggerCapture(InboxCleanupRunner::class.java.name).use { capture ->
            runner.runScheduled()

            assertEquals(
                Level.INFO,
                capture.records.single { it.message == "Inbox cleanup deleted 2 incoming messages" }.level,
            )
            assertTrue(capture.records.none { it.message?.contains("peer") == true })
        }
    }
}
