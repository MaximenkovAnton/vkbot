package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.vo.FromId

sealed interface ProfileTarget {
    val fromId: FromId

    data class User(override val fromId: FromId) : ProfileTarget

    data class Group(override val fromId: FromId) : ProfileTarget
}
