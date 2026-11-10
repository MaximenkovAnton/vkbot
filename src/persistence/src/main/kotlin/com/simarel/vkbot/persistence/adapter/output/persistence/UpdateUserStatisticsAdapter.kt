package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserStatisticsWriterRepository
import com.simarel.vkbot.persistence.port.output.persistence.UpdateUserStatisticsPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class UpdateUserStatisticsAdapter(
    private val repository: JooqUserStatisticsWriterRepository,
) : UpdateUserStatisticsPort {

    @Transactional
    override fun execute(request: UpdateUserStatisticsPort.Request): UpdateUserStatisticsPort.Response {
        repository.upsertTotals(request.userId, request.date, request.messageTextLength)
        repository.upsertChat(request.userId, request.peerId, request.date, request.messageTextLength)
        repository.upsertHour(request.userId, request.date)
        return UpdateUserStatisticsPort.Response
    }
}
