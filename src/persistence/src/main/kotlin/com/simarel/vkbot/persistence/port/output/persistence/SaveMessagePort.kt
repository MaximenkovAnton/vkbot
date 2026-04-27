package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity

interface SaveMessagePort {
    fun save(message: MessageEntity)
}
