package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkGroupProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FindGroupProfilesByIdsAdapter(
    private val repository: JooqVkGroupProfileRepository,
) : FindGroupProfilesByIdsPort {

    @Transactional
    override fun execute(request: FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest): FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse {
        if (request.fromIds.isEmpty()) {
            return FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse(emptyList())
        }
        // FromId для групп < 0, но в БД хранятся > 0
        val idValues = request.fromIds.map { it.value }
        return FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse(repository.findByIds(idValues))
    }
}
