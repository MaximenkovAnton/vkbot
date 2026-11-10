package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.statistics.UserStatistics
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserStatisticsPort :
    OutputPort<
        FindUserStatisticsPort.FindUserStatisticsRequest,
        FindUserStatisticsPort.FindUserStatisticsResponse,
        > {
    data class FindUserStatisticsRequest(val userId: Long) : OutputPortRequest

    data class FindUserStatisticsResponse(val statistics: UserStatistics) : OutputPortResponse
}
