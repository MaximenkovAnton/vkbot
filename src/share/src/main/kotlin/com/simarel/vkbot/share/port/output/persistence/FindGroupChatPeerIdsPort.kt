package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindGroupChatPeerIdsPort :
    OutputPort<
        FindGroupChatPeerIdsPort.Request,
        FindGroupChatPeerIdsPort.Response,
        > {
    data object Request : OutputPortRequest
    data class Response(val peerIds: Set<PeerId>) : OutputPortResponse
}
