package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserStatisticsReader
import com.simarel.vkbot.persistence.config.StatisticsConfig
import com.simarel.vkbot.persistence.port.output.persistence.FindUserStatisticsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserStatisticsAdapter(
    private val reader: UserStatisticsReader,
    private val config: StatisticsConfig,
) : FindUserStatisticsPort {

    override fun execute(
        request: FindUserStatisticsPort.FindUserStatisticsRequest,
    ): FindUserStatisticsPort.FindUserStatisticsResponse {
        val statistics = reader.findByUser(request.userId, config.zone)
        return FindUserStatisticsPort.FindUserStatisticsResponse(statistics)
    }
}
