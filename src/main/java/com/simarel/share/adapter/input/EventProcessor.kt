package com.simarel.share.adapter.input

import com.fasterxml.jackson.databind.JsonNode
import com.simarel.share.domain.Event

interface EventProcessor {
    fun event(): Event
    fun process(request: JsonNode)
}