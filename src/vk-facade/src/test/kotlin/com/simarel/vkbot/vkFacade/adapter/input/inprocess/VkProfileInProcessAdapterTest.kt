package com.simarel.vkbot.vkFacade.adapter.input.inprocess

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkProfileInputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VkProfileInProcessAdapterTest {

    private val user = VkUserProfile.of(id = 1, firstName = "Ivan", lastName = "Petrov")
    private val group = VkGroupProfile.of(id = 2, name = "Group")

    @Test
    fun `get user profiles batch forwards only user ids`() {
        val port = RecordingVkProfileInputPort(
            VkProfileInputPort.VkProfileResponse(users = listOf(user), groups = emptyList()),
        )
        val adapter = GetUserProfilesBatchInProcessAdapter(port)

        val response = adapter.execute(
            GetUserProfilesBatchPort.Request(listOf(FromId.of(1), FromId.of(3))),
        )

        assertEquals(
            VkProfileInputPort.VkProfileRequest(userIds = listOf(1, 3), groupIds = emptyList()),
            port.requests.single(),
        )
        assertEquals(listOf(user), response.profiles)
    }

    @Test
    fun `get group profiles batch forwards only group ids`() {
        val port = RecordingVkProfileInputPort(
            VkProfileInputPort.VkProfileResponse(users = emptyList(), groups = listOf(group)),
        )
        val adapter = GetGroupProfilesBatchInProcessAdapter(port)

        val response = adapter.execute(
            GetGroupProfilesBatchPort.Request(listOf(FromId.of(2))),
        )

        assertEquals(
            VkProfileInputPort.VkProfileRequest(userIds = emptyList(), groupIds = listOf(2)),
            port.requests.single(),
        )
        assertEquals(listOf(group), response.profiles)
    }

    private class RecordingVkProfileInputPort(
        private val response: VkProfileInputPort.VkProfileResponse,
    ) : VkProfileInputPort {
        val requests = mutableListOf<VkProfileInputPort.VkProfileRequest>()

        override fun execute(request: VkProfileInputPort.VkProfileRequest): VkProfileInputPort.VkProfileResponse {
            requests.add(request)
            return response
        }
    }
}
