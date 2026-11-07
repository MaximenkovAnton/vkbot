package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByBirthDatePort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindUserProfilesByBirthDateAdapter(
    private val repository: JooqVkUserProfileRepository,
) : FindUserProfilesByBirthDatePort {

    override fun execute(
        request: FindUserProfilesByBirthDatePort.FindUserProfilesByBirthDateRequest,
    ): FindUserProfilesByBirthDatePort.FindUserProfilesByBirthDateResponse = FindUserProfilesByBirthDatePort
        .FindUserProfilesByBirthDateResponse(repository.findByBirthDate(request.day, request.month))
}
