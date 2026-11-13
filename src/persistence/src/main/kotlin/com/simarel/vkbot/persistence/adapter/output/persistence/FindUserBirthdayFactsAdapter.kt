package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserKnowledgeTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserBirthdayFactsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserBirthdayFactsAdapter(
    private val table: UserKnowledgeTable,
) : FindUserBirthdayFactsPort {

    override fun execute(
        request: FindUserBirthdayFactsPort.Request,
    ): FindUserBirthdayFactsPort.Response = FindUserBirthdayFactsPort.Response(
        facts = table.findCurrentBirthdayFacts(request.monthDay),
    )
}
