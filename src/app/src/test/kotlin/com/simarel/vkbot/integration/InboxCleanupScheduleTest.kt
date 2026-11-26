package com.simarel.vkbot.integration

import io.quarkus.scheduler.Scheduler
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class InboxCleanupScheduleTestProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf("vk.longpoll.enabled" to "false")
}

@QuarkusTest
@TestProfile(InboxCleanupScheduleTestProfile::class)
class InboxCleanupScheduleTest {

    @Inject
    lateinit var scheduler: Scheduler

    @Test
    fun `cleanup is not scheduled when the cron is off`() {
        assertFalse(scheduler.scheduledJobs.map { it.id }.contains(JOB_ID))
    }

    private companion object {
        const val JOB_ID = "inbox-cleanup"
    }
}
