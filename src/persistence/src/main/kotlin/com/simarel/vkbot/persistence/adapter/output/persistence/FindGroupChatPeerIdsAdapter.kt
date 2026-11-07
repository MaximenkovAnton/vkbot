package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupChatPeerIdsPort
import com.simarel.vkbot.share.domain.vo.PeerId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindGroupChatPeerIdsAdapter(
    private val repository: JooqMessageRepository,
) : FindGroupChatPeerIdsPort {

    override fun execute(
        request: FindGroupChatPeerIdsPort.FindGroupChatPeerIdsRequest,
    ): FindGroupChatPeerIdsPort.FindGroupChatPeerIdsResponse = FindGroupChatPeerIdsPort.FindGroupChatPeerIdsResponse(
        repository.findGroupPeerIds().map { PeerId.of(it) }.toSet(),
    )
}
