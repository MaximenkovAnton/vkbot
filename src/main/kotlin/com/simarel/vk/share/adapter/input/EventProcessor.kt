package com.simarel.vk.share.adapter.input

import com.simarel.vk.share.domain.Event

interface EventProcessor {
    fun event(): Event
    fun process(jsonString: String)
}