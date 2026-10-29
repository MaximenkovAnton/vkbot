package com.simarel.vkbot.testfixtures.port

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort

object FakeProfilePortProvider {

    fun createGetUserProfilesBatchRequest(
        fromIds: List<FromId> = emptyList(),
    ) = GetUserProfilesBatchPort.Request(fromIds = fromIds)

    fun createGetGroupProfilesBatchRequest(
        fromIds: List<FromId> = emptyList(),
    ) = GetGroupProfilesBatchPort.Request(fromIds = fromIds)
}
