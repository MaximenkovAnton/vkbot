package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindStoredUserProfileHistoryPort :
    OutputPort<
        FindStoredUserProfileHistoryPort.Request,
        FindStoredUserProfileHistoryPort.Response,
        > {
    data class Request(val profileId: FromId, val limit: Int) : OutputPortRequest
    data class Response(val entries: List<ProfileHistoryEntry>) : OutputPortResponse
}
