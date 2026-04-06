package com.simarel.vkbot.share.adapter.input

import com.simarel.vkbot.share.domain.Event

interface EventProcessor {
    fun event(): Event
    fun process(jsonString: String)
}
