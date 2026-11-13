package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkGroupProfileTable
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveGroupProfileAdapter(
    private val table: VkGroupProfileTable,
) : SaveGroupProfilePort {

    @Transactional
    override fun execute(request: SaveGroupProfilePort.SaveGroupProfileRequest): SaveGroupProfilePort.SaveGroupProfileResponse {
        table.persist(request.profile)
        return SaveGroupProfilePort.SaveGroupProfileResponse()
    }
}
