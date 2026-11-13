package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.port.output.persistence.FindChatAuthorsByPeerIdPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindChatAuthorsByPeerIdAdapter(
    private val table: MessageTable,
) : FindChatAuthorsByPeerIdPort {

    override fun execute(
        request: FindChatAuthorsByPeerIdPort.Request,
    ): FindChatAuthorsByPeerIdPort.Response = FindChatAuthorsByPeerIdPort.Response(
        table.findAuthorsByPeerId(request.peerId.value),
    )
}
