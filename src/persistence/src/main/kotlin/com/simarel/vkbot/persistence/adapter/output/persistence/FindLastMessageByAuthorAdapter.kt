package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.port.output.persistence.FindLastMessageByAuthorPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindLastMessageByAuthorAdapter(
    private val table: MessageTable,
) : FindLastMessageByAuthorPort {
    override fun execute(
        request: FindLastMessageByAuthorPort.Request,
    ): FindLastMessageByAuthorPort.Response = FindLastMessageByAuthorPort.Response(
        table.findLastByAuthor(
            request.peerId.value,
            request.fromId.value,
            request.beforeConversationMessageId.value,
        ),
    )
}
