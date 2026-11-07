package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindGroupChatPeerIdsPort :
    OutputPort<
        FindGroupChatPeerIdsPort.FindGroupChatPeerIdsRequest,
        FindGroupChatPeerIdsPort.FindGroupChatPeerIdsResponse,
        > {
    data object FindGroupChatPeerIdsRequest : OutputPortRequest
    data class FindGroupChatPeerIdsResponse(val peerIds: Set<PeerId>) : OutputPortResponse
}
