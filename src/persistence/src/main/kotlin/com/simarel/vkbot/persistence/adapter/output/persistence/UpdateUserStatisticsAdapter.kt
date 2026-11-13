package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserStatisticsWriter
import com.simarel.vkbot.persistence.port.output.persistence.UpdateUserStatisticsPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class UpdateUserStatisticsAdapter(
    private val writer: UserStatisticsWriter,
) : UpdateUserStatisticsPort {

    @Transactional
    override fun execute(request: UpdateUserStatisticsPort.Request): UpdateUserStatisticsPort.Response {
        writer.upsertTotals(request.userId, request.date, request.messageTextLength)
        writer.upsertChat(request.userId, request.peerId, request.date, request.messageTextLength)
        writer.upsertHour(request.userId, request.date)
        return UpdateUserStatisticsPort.Response
    }
}
