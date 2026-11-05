package com.simarel.vkbot.receiver.adapter.output.vk

import com.simarel.vkbot.receiver.domain.vo.LongPollFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VkLongPollCheckOutputAdapterTest {

    private val adapter = VkLongPollCheckOutputAdapter()

    @Test
    fun `parses numeric ts and raw updates`() {
        val response = adapter.parseCheckResponse(
            """{"ts":123,"updates":[{"type":"message_new","object":{}}]}""",
        )

        assertEquals("123", response.ts)
        assertEquals(listOf("""{"type":"message_new","object":{}}"""), response.updates)
        assertEquals(LongPollFailure.NONE, response.failure)
    }

    @Test
    fun `parses string ts and empty updates`() {
        val response = adapter.parseCheckResponse("""{"ts":"456","updates":[]}""")

        assertEquals("456", response.ts)
        assertEquals(emptyList<String>(), response.updates)
        assertEquals(LongPollFailure.NONE, response.failure)
    }

    @Test
    fun `maps failed codes to long poll failures`() {
        assertEquals(LongPollFailure.TS_OUTDATED, adapter.parseCheckResponse("""{"failed":1}""").failure)
        assertEquals(LongPollFailure.KEY_EXPIRED, adapter.parseCheckResponse("""{"failed":2}""").failure)
        assertEquals(LongPollFailure.INFO_LOST, adapter.parseCheckResponse("""{"failed":3}""").failure)
        assertEquals(LongPollFailure.NONE, adapter.parseCheckResponse("{}").failure)
    }
}
