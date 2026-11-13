package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkUserProfileTable
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveUserProfileAdapter(
    private val table: VkUserProfileTable,
) : SaveUserProfilePort {

    @Transactional
    override fun execute(request: SaveUserProfilePort.SaveUserProfileRequest): SaveUserProfilePort.SaveUserProfileResponse {
        table.persist(request.profile, request.changes)
        return SaveUserProfilePort.SaveUserProfileResponse()
    }
}
