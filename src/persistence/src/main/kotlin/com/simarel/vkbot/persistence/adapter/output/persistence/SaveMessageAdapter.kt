package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.port.output.persistence.SaveMessagePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveMessageAdapter(
    private val table: MessageTable,
) : SaveMessagePort {

    @Transactional
    override fun execute(request: SaveMessagePort.SaveMessageRequest): SaveMessagePort.SaveMessageResponse {
        table.persist(request.message)
        return SaveMessagePort.SaveMessageResponse()
    }
}
