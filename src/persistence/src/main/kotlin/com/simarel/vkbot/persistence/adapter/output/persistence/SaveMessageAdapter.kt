package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.SaveMessagePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveMessageAdapter(
    private val repository: JooqMessageRepository,
) : SaveMessagePort {

    @Transactional
    override fun execute(request: SaveMessagePort.SaveMessageRequest): SaveMessagePort.SaveMessageResponse {
        repository.persist(request.message)
        return SaveMessagePort.SaveMessageResponse()
    }
}
