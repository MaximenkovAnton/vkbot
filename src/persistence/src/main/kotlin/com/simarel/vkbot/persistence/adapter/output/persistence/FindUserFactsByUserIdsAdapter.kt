package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserKnowledgeTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserFactsByUserIdsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserFactsByUserIdsAdapter(
    private val table: UserKnowledgeTable,
) : FindUserFactsByUserIdsPort {

    override fun execute(
        request: FindUserFactsByUserIdsPort.Request,
    ): FindUserFactsByUserIdsPort.Response = FindUserFactsByUserIdsPort.Response(
        facts = table.findFactsByUserIds(request.userIds),
    )
}
