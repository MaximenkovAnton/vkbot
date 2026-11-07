package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserChatPeerIdsPort :
    OutputPort<
        FindUserChatPeerIdsPort.FindUserChatPeerIdsRequest,
        FindUserChatPeerIdsPort.FindUserChatPeerIdsResponse,
        > {
    data class FindUserChatPeerIdsRequest(val userId: FromId) : OutputPortRequest
    data class FindUserChatPeerIdsResponse(val peerIds: Set<PeerId>) : OutputPortResponse
}
