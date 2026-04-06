package com.simarel.vkbot.share.port.input

import com.simarel.vkbot.share.domain.Event

interface EventProcessor {
    fun event(): Event
    fun process(payload: String)
}