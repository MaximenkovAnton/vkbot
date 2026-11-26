package com.simarel.vkbot.receiver.adapter.input.schedule

import com.simarel.vkbot.receiver.port.input.PurgeInboxInputPort
import io.quarkus.logging.Log
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
open class InboxCleanupRunner(
    private val purgeInboxInputPort: PurgeInboxInputPort,
) {
    @Scheduled(identity = "inbox-cleanup", cron = "{vk.inbox.cleanup-cron}")
    open fun runScheduled() {
        val deleted = purgeInboxInputPort.execute(
            PurgeInboxInputPort.Request(now = OffsetDateTime.now(ZoneOffset.UTC)),
        ).deleted
        Log.info("Inbox cleanup deleted $deleted incoming messages")
    }
}
