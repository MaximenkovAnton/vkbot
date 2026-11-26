package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.port.input.PurgeInboxInputPort
import com.simarel.vkbot.share.port.output.persistence.PurgeIncomingMessagesPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

@ApplicationScoped
class RunInboxCleanupUsecase(
    private val purgeIncomingMessagesPort: PurgeIncomingMessagesPort,
    @ConfigProperty(name = "vk.inbox.retention", defaultValue = "24h") private val retention: Duration,
) : PurgeInboxInputPort {

    override fun execute(request: PurgeInboxInputPort.Request): PurgeInboxInputPort.Response {
        val deleted = purgeIncomingMessagesPort
            .execute(PurgeIncomingMessagesPort.Request(until = request.now.minus(retention)))
            .deleted
        return PurgeInboxInputPort.Response(deleted = deleted)
    }
}
