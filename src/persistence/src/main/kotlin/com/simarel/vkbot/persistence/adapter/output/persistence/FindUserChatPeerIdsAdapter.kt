package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserChatPeerIdsPort
import com.simarel.vkbot.share.domain.vo.PeerId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindUserChatPeerIdsAdapter(
    private val table: MessageTable,
) : FindUserChatPeerIdsPort {

    override fun execute(
        request: FindUserChatPeerIdsPort.FindUserChatPeerIdsRequest,
    ): FindUserChatPeerIdsPort.FindUserChatPeerIdsResponse = FindUserChatPeerIdsPort.FindUserChatPeerIdsResponse(
        table.findPeerIdsByFromId(request.userId.value).map { PeerId.of(it) }.toSet(),
    )
}
