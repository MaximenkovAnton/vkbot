package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity

interface MessageRepositoryPort {
    fun save(message: MessageEntity)
}
