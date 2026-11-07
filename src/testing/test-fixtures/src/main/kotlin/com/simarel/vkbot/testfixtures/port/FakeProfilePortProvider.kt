package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort

object FakeProfilePortProvider {

    fun createGetUserProfilesBatchRequest(
        fromIds: List<FromId> = emptyList(),
    ) = GetUserProfilesBatchPort.Request(fromIds = fromIds)

    fun createGetGroupProfilesBatchRequest(
        fromIds: List<FromId> = emptyList(),
    ) = GetGroupProfilesBatchPort.Request(fromIds = fromIds)

    fun createFindStoredUserProfilesByIdsRequest(
        ids: List<FromId> = emptyList(),
    ) = FindStoredUserProfilesByIdsPort.Request(ids = ids)

    fun createFindStoredGroupProfilesByIdsRequest(
        ids: List<FromId> = emptyList(),
    ) = FindStoredGroupProfilesByIdsPort.Request(ids = ids)
}
