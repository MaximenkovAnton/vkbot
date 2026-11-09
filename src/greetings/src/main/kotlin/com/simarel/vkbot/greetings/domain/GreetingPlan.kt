package com.simarel.vkbot.greetings.domain

import com.simarel.vkbot.share.domain.exception.ValidationException
import com.simarel.vkbot.share.domain.vo.PeerId

sealed interface GreetingPlan {
    val peerId: PeerId

    data class HolidayGreeting(
        override val peerId: PeerId,
        val holiday: Holiday,
    ) : GreetingPlan

    data class BirthdayGreeting(
        override val peerId: PeerId,
        val people: List<String>,
    ) : GreetingPlan {
        init {
            if (people.isEmpty()) throw BirthdayGreetingPeopleEmptyValidationException()
        }
    }
}

open class BirthdayGreetingValidationException(message: String) : ValidationException(message)

class BirthdayGreetingPeopleEmptyValidationException :
    BirthdayGreetingValidationException(
        "Birthday greeting must contain at least one person",
    )
