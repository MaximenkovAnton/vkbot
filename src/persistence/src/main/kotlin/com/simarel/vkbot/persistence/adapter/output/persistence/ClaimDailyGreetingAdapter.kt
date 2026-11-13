package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.GreetingLogTable
import com.simarel.vkbot.persistence.port.output.persistence.ClaimDailyGreetingPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class ClaimDailyGreetingAdapter(
    private val table: GreetingLogTable,
) : ClaimDailyGreetingPort {

    @Transactional
    override fun execute(
        request: ClaimDailyGreetingPort.ClaimDailyGreetingRequest,
    ): ClaimDailyGreetingPort.ClaimDailyGreetingResponse = ClaimDailyGreetingPort.ClaimDailyGreetingResponse(
        table.claim(request.date, request.peerId.value),
    )
}
