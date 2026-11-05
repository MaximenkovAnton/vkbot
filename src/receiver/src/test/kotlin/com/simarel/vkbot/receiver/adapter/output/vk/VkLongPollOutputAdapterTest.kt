package com.simarel.vkbot.receiver.adapter.output.vk

import com.simarel.vkbot.receiver.domain.exception.VkLongPollServerException
import com.simarel.vkbot.receiver.port.output.GetLongPollServerPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VkLongPollOutputAdapterTest {

    @Test
    fun `parses the long poll session and asks vk for the configured group`() {
        val client = FakeVkLongPollServerClient("""{"response":{"key":"k","server":"https://lp.vk.com/wh1","ts":30}}""")

        val response = adapter(client).execute(GetLongPollServerPort.Request)

        assertEquals("https://lp.vk.com/wh1", response.server)
        assertEquals("k", response.key)
        assertEquals("30", response.ts)
        assertEquals(listOf(GROUP_ID), client.requestedGroupIds)
    }

    @Test
    fun `vk error becomes an exception naming the code and the reason`() {
        val client = FakeVkLongPollServerClient(
            """{"error":{"error_code":15,"error_subcode":1133,"error_msg":"Access denied"}}""",
        )

        val thrown = assertThrows<VkLongPollServerException> {
            adapter(client).execute(GetLongPollServerPort.Request)
        }

        assertEquals(
            "groups.getLongPollServer failed: code 15 subcode 1133 message Access denied",
            thrown.message,
        )
    }

    private fun adapter(client: VkLongPollServerClient) = VkLongPollOutputAdapter(client, GROUP_ID)

    private class FakeVkLongPollServerClient(private val body: String) : VkLongPollServerClient {
        val requestedGroupIds = mutableListOf<Long>()

        override fun getServer(groupId: Long): String {
            requestedGroupIds.add(groupId)
            return body
        }
    }

    private companion object {
        const val GROUP_ID = 232142875L
    }
}
