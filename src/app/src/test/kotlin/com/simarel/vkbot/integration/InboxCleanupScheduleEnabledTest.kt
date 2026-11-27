package com.simarel.vkbot.integration

import io.quarkus.scheduler.Scheduler
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboxCleanupScheduleEnabledProfile : QuarkusTestProfile {
    override fun getConfigProfile(): String = "integration-test"

    override fun getConfigOverrides(): Map<String, String> = mapOf(
        "vk.inbox.cleanup-cron" to "0 0 * * * ?",
        "vk.longpoll.enabled" to "false",
    )
}

@QuarkusTest
@TestProfile(InboxCleanupScheduleEnabledProfile::class)
class InboxCleanupScheduleEnabledTest {

    @Inject
    lateinit var scheduler: Scheduler

    @Test
    fun `cleanup is scheduled when the cron is valid`() {
        assertTrue(scheduler.scheduledJobs.map { it.id }.contains(JOB_ID))
    }

    private companion object {
        const val JOB_ID = "inbox-cleanup"
    }
}
