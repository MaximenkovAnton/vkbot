package com.simarel.vkbot.share.domain.model.greeting

import com.simarel.vkbot.share.domain.vo.FromId

data class GreetingPerson(
    val name: String,
    val fromId: FromId,
)
