package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserKnowledgeRepository
import com.simarel.vkbot.persistence.config.KnowledgeConfig
import com.simarel.vkbot.persistence.port.output.persistence.PersistUserFactsPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
open class PersistUserFactsAdapter(
    private val repository: JooqUserKnowledgeRepository,
    private val config: KnowledgeConfig,
) : PersistUserFactsPort {

    @Transactional
    override fun execute(request: PersistUserFactsPort.Request): PersistUserFactsPort.Response {
        val saved = repository.saveFact(
            request.candidate,
            config.confirmThreshold,
            config.evidenceRefsLimit,
            OffsetDateTime.now(ZoneOffset.UTC),
        )
        return PersistUserFactsPort.Response(saved = saved)
    }
}
