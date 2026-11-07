package com.simarel.vkbot.greetings.domain

import com.simarel.vkbot.share.domain.vo.PeerId

data class GreetingPlan(
    val peerId: PeerId,
    val holidays: List<Holiday>,
    val birthdays: List<String>,
) {
    init {
        require(holidays.isNotEmpty() || birthdays.isNotEmpty()) {
            "Greeting plan must contain at least one event"
        }
    }
}
