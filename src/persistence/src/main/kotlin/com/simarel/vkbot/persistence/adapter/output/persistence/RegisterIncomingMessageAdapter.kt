package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.IncomingMessageTable
import com.simarel.vkbot.persistence.port.output.persistence.RegisterIncomingMessagePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class RegisterIncomingMessageAdapter(
    private val table: IncomingMessageTable,
) : RegisterIncomingMessagePort {

    @Transactional
    override fun execute(
        request: RegisterIncomingMessagePort.RegisterIncomingMessageRequest,
    ): RegisterIncomingMessagePort.RegisterIncomingMessageResponse {
        val inboxMessageId = table.register(request.peerId, request.conversationMessageId, request.payload)
        return RegisterIncomingMessagePort.RegisterIncomingMessageResponse(inboxMessageId)
    }
}
