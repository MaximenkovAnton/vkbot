package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindStoredUserStatisticsPort :
    OutputPort<
        FindStoredUserStatisticsPort.Request,
        FindStoredUserStatisticsPort.Response,
        > {
    data class Request(val userId: FromId) : OutputPortRequest
    data class Response(val statistics: UserStatistics) : OutputPortResponse
}
