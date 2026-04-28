package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.MessageRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class MessageRepositoryAdapter(
    private val repository: JooqMessageRepository,
) : MessageRepositoryPort {

    @Transactional
    override fun save(message: MessageEntity) {
        repository.persist(message)
    }
}
