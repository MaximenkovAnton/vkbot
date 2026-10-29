package com.simarel.vkbot.vkFacade.adapter.input.inprocess

import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakeProfilePortProvider
import com.simarel.vkbot.testfixtures.vkFacade.FakeVkFacadeProvider
import com.simarel.vkbot.vkFacade.port.input.vk.VkProfileInputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VkProfileInProcessAdapterTest {

    private val user = FakeModelProvider.createVkUserProfile(id = 1, firstName = "Ivan", lastName = "Petrov")
    private val group = FakeModelProvider.createVkGroupProfile(id = 2, name = "Group")

    @Test
    fun `get user profiles batch forwards only user ids`() {
        val port = RecordingVkProfileInputPort(
            FakeVkFacadeProvider.createVkProfileResponse(users = listOf(user)),
        )
        val adapter = GetUserProfilesBatchInProcessAdapter(port)

        val response = adapter.execute(
            FakeProfilePortProvider.createGetUserProfilesBatchRequest(
                listOf(FakeVoProvider.createFromId(1), FakeVoProvider.createFromId(3)),
            ),
        )

        assertEquals(
            FakeVkFacadeProvider.createVkProfileRequest(userIds = listOf(1, 3)),
            port.requests.single(),
        )
        assertEquals(listOf(user), response.profiles)
    }

    @Test
    fun `get group profiles batch forwards only group ids`() {
        val port = RecordingVkProfileInputPort(
            FakeVkFacadeProvider.createVkProfileResponse(groups = listOf(group)),
        )
        val adapter = GetGroupProfilesBatchInProcessAdapter(port)

        val response = adapter.execute(
            FakeProfilePortProvider.createGetGroupProfilesBatchRequest(listOf(FakeVoProvider.createFromId(2))),
        )

        assertEquals(
            FakeVkFacadeProvider.createVkProfileRequest(groupIds = listOf(2)),
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
