package com.simarel.vk.share.adapter.input

import com.fasterxml.jackson.databind.JsonNode
import com.simarel.vk.share.domain.Event

interface EventProcessor {
    fun event(): Event
    fun process(request: JsonNode)
}