package com.simarel.vkbot.objectProvider.fake.adapter.input

import com.simarel.vkbot.share.adapter.input.EventProcessor
import com.simarel.vkbot.share.domain.Event
import java.util.concurrent.ConcurrentLinkedQueue

class FakeEventProcessor(
    private val eventType: Event,
    private val shouldProcess: Boolean = true
) : EventProcessor {
    
    val processCalls = ConcurrentLinkedQueue<String>()
    
    override fun event(): Event = eventType
    
    override fun process(jsonString: String) {
        if (shouldProcess) {
            processCalls.add(jsonString)
        }
    }
}