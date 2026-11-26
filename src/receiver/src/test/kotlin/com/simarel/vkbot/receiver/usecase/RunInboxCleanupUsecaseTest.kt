package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.fixtures.FakePurgeInboxInputPortProvider
import com.simarel.vkbot.receiver.fixtures.port.FakePurgeIncomingMessagesPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.OffsetDateTime

class RunInboxCleanupUsecaseTest {

    @Test
    fun `cutoff is now minus retention and the deleted count is returned`() {
        val purge = FakePurgeIncomingMessagesPort()
        val usecase = RunInboxCleanupUsecase(purge, RETENTION)

        val result = usecase.execute(
            FakePurgeInboxInputPortProvider.createRequest(now = OffsetDateTime.parse("2026-11-26T10:00:00Z")),
        )

        assertEquals(OffsetDateTime.parse("2026-11-25T10:00:00Z"), purge.executeCalls.single().until)
        assertEquals(1, purge.executeCalls.size)
        assertEquals(2, result.deleted)
    }

    private companion object {
        val RETENTION: Duration = Duration.ofHours(24)
    }
}
