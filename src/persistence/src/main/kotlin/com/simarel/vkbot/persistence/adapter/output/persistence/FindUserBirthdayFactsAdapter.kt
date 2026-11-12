package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserKnowledgeRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindUserBirthdayFactsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserBirthdayFactsAdapter(
    private val repository: JooqUserKnowledgeRepository,
) : FindUserBirthdayFactsPort {

    override fun execute(
        request: FindUserBirthdayFactsPort.Request,
    ): FindUserBirthdayFactsPort.Response = FindUserBirthdayFactsPort.Response(
        facts = repository.findCurrentBirthdayFacts(request.monthDay),
    )
}
