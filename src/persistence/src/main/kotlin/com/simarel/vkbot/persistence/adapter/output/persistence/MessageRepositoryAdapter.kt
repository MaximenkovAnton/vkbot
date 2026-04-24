package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.MessageRepositoryPort
import io.quarkus.hibernate.orm.panache.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class MessageRepositoryAdapter : PanacheRepository<MessageEntity>, MessageRepositoryPort {

    @Transactional
    override fun save(message: MessageEntity) {
        persist(message)
    }
}
