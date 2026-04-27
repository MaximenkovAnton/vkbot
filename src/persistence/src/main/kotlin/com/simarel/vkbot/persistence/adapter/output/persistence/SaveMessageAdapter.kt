package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.SaveMessagePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveMessageAdapter(
    private val repository: MessagePanacheRepository,
) : SaveMessagePort {

    @Transactional
    override fun save(message: MessageEntity) {
        repository.persist(message)
    }
}
