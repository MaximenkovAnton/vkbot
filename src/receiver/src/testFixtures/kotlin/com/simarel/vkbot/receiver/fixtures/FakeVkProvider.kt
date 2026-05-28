package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.domain.vo.VkEvent

object FakeVkProvider {
    const val SECRET = "test_secret"
    fun createVkEvent(type: VkCallbackEvent = VkCallbackEvent.MESSAGE_NEW): VkEvent = VkEvent(type)
}
