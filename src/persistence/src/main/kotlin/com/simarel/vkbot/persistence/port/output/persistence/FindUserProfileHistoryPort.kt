package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserProfileHistoryPort :
    OutputPort<
        FindUserProfileHistoryPort.FindUserProfileHistoryRequest,
        FindUserProfileHistoryPort.FindUserProfileHistoryResponse,
        > {
    data class FindUserProfileHistoryRequest(
        val profileId: Long,
        val limit: Int,
        val offset: Int,
    ) : OutputPortRequest

    data class FindUserProfileHistoryResponse(
        val entries: List<ProfileHistoryEntry>,
    ) : OutputPortResponse
}
