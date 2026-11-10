package com.simarel.vkbot.persistence.testfixtures

import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileRequest
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.util.concurrent.ConcurrentLinkedQueue

object ProfileActualizationPortProvider {

    fun createFetchUserProfileRequest(
        message: Message = FakeVoProvider.createMessage(),
    ) = FetchUserProfileRequest(message = message)

    fun createFindUserProfilesByIdsRequest(
        fromIds: Collection<FromId> = emptyList(),
    ) = FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest(fromIds = fromIds)

    fun createFindGroupProfilesByIdsRequest(
        fromIds: Collection<FromId> = emptyList(),
    ) = FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest(fromIds = fromIds)
}

class FakeFindUserProfilesByIdsPort(
    private val profiles: List<VkUserProfile> = emptyList(),
) : FindUserProfilesByIdsPort {

    val requests = ConcurrentLinkedQueue<FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest>()

    override fun execute(
        request: FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest,
    ): FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse {
        requests.add(request)
        return FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse(profiles = profiles)
    }
}

class FakeFindGroupProfilesByIdsPort(
    private val profiles: List<VkGroupProfile> = emptyList(),
) : FindGroupProfilesByIdsPort {

    val requests = ConcurrentLinkedQueue<FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest>()

    override fun execute(
        request: FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest,
    ): FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse {
        requests.add(request)
        return FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse(profiles = profiles)
    }
}

class FakeSaveUserProfilePort : SaveUserProfilePort {

    val savedProfiles = ConcurrentLinkedQueue<VkUserProfile>()
    val requests = ConcurrentLinkedQueue<SaveUserProfilePort.SaveUserProfileRequest>()

    override fun execute(
        request: SaveUserProfilePort.SaveUserProfileRequest,
    ): SaveUserProfilePort.SaveUserProfileResponse {
        requests.add(request)
        savedProfiles.add(request.profile)
        return SaveUserProfilePort.SaveUserProfileResponse()
    }
}

class FakeSaveGroupProfilePort : SaveGroupProfilePort {

    val savedProfiles = ConcurrentLinkedQueue<VkGroupProfile>()

    override fun execute(
        request: SaveGroupProfilePort.SaveGroupProfileRequest,
    ): SaveGroupProfilePort.SaveGroupProfileResponse {
        savedProfiles.add(request.profile)
        return SaveGroupProfilePort.SaveGroupProfileResponse()
    }
}

class FakeGetUserProfilesBatchPort(
    private val profiles: List<VkUserProfile> = emptyList(),
    private val failure: RuntimeException? = null,
) : GetUserProfilesBatchPort {

    val requestedIds = ConcurrentLinkedQueue<List<FromId>>()

    override fun execute(request: GetUserProfilesBatchPort.Request): GetUserProfilesBatchPort.Response {
        requestedIds.add(request.fromIds)
        failure?.let { throw it }
        return GetUserProfilesBatchPort.Response(profiles = profiles)
    }
}

class FakeGetGroupProfilesBatchPort(
    private val profiles: List<VkGroupProfile> = emptyList(),
    private val failure: RuntimeException? = null,
) : GetGroupProfilesBatchPort {

    val requestedIds = ConcurrentLinkedQueue<List<FromId>>()

    override fun execute(request: GetGroupProfilesBatchPort.Request): GetGroupProfilesBatchPort.Response {
        requestedIds.add(request.fromIds)
        failure?.let { throw it }
        return GetGroupProfilesBatchPort.Response(profiles = profiles)
    }
}
