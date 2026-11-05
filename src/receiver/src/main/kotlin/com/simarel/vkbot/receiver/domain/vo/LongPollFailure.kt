package com.simarel.vkbot.receiver.domain.vo

enum class LongPollFailure {
    NONE,
    TS_OUTDATED,
    KEY_EXPIRED,
    INFO_LOST,
}
