package com.simarel.vkbot.greetings.domain

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
            require(people.isNotEmpty()) {
                "Birthday greeting must contain at least one person"
            }
        }
    }
}
