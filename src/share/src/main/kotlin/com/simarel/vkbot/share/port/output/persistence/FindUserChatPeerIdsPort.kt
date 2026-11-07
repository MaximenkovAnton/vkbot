package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserChatPeerIdsPort :
    OutputPort<
        FindUserChatPeerIdsPort.Request,
        FindUserChatPeerIdsPort.Response,
        > {
    data class Request(val userId: FromId) : OutputPortRequest
    data class Response(val peerIds: Set<PeerId>) : OutputPortResponse
}
