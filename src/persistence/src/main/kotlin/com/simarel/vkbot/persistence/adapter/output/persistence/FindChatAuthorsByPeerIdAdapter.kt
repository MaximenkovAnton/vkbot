package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindChatAuthorsByPeerIdPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindChatAuthorsByPeerIdAdapter(
    private val repository: JooqMessageRepository,
) : FindChatAuthorsByPeerIdPort {

    override fun execute(
        request: FindChatAuthorsByPeerIdPort.Request,
    ): FindChatAuthorsByPeerIdPort.Response = FindChatAuthorsByPeerIdPort.Response(
        repository.findAuthorsByPeerId(request.peerId.value),
    )
}
