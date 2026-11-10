package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindChatAuthorsPort : OutputPort<FindChatAuthorsPort.Request, FindChatAuthorsPort.Response> {
    data class Request(val peerId: PeerId) : OutputPortRequest
    data class Response(val authors: List<ChatAuthor>) : OutputPortResponse
}
