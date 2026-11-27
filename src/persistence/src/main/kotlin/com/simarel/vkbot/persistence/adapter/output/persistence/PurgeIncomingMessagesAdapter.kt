package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.IncomingMessageTable
import com.simarel.vkbot.persistence.port.output.persistence.PurgeIncomingMessagesPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class PurgeIncomingMessagesAdapter(
    private val table: IncomingMessageTable,
) : PurgeIncomingMessagesPort {

    @Transactional
    override fun execute(
        request: PurgeIncomingMessagesPort.PurgeIncomingMessagesRequest,
    ): PurgeIncomingMessagesPort.PurgeIncomingMessagesResponse {
        val deleted = table.purge(request.until)
        return PurgeIncomingMessagesPort.PurgeIncomingMessagesResponse(deleted)
    }
}
