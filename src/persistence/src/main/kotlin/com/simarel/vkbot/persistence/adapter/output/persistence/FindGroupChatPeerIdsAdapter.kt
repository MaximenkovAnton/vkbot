package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.domain.vo.PeerId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindGroupChatPeerIdsAdapter(
    private val table: MessageTable,
) : FindGroupChatPeerIdsPort {

    override fun execute(
        request: FindGroupChatPeerIdsPort.FindGroupChatPeerIdsRequest,
    ): FindGroupChatPeerIdsPort.FindGroupChatPeerIdsResponse = FindGroupChatPeerIdsPort.FindGroupChatPeerIdsResponse(
        table.findGroupPeerIds().map { PeerId.of(it) }.toSet(),
    )
}
