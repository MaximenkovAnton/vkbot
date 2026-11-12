package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqKnowledgeSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.UpsertUserKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
open class UpsertUserKnowledgeSummaryAdapter(
    private val repository: JooqKnowledgeSummaryRepository,
) : UpsertUserKnowledgeSummaryPort {

    @Transactional
    override fun execute(
        request: UpsertUserKnowledgeSummaryPort.Request,
    ): UpsertUserKnowledgeSummaryPort.Response {
        repository.upsertUserSummary(request.userId, request.summaryText, OffsetDateTime.now(ZoneOffset.UTC))
        return UpsertUserKnowledgeSummaryPort.Response()
    }
}
