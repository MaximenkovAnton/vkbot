package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqGreetingLogRepository
import com.simarel.vkbot.persistence.port.output.persistence.ClaimDailyGreetingPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class ClaimDailyGreetingAdapter(
    private val repository: JooqGreetingLogRepository,
) : ClaimDailyGreetingPort {

    @Transactional
    override fun execute(
        request: ClaimDailyGreetingPort.ClaimDailyGreetingRequest,
    ): ClaimDailyGreetingPort.ClaimDailyGreetingResponse = ClaimDailyGreetingPort.ClaimDailyGreetingResponse(
        repository.claim(request.date, request.peerId.value),
    )
}
