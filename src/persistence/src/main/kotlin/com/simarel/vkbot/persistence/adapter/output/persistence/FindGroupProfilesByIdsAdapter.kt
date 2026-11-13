package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkGroupProfileTable
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FindGroupProfilesByIdsAdapter(
    private val table: VkGroupProfileTable,
) : FindGroupProfilesByIdsPort {

    @Transactional
    override fun execute(request: FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest): FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse {
        if (request.fromIds.isEmpty()) {
            return FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse(emptyList())
        }
        // FromId для групп < 0, но в БД хранятся > 0
        val idValues = request.fromIds.map { it.value }
        return FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse(table.findByIds(idValues))
    }
}
