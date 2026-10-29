package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort

object FakeConversationContextPortProvider {

    fun createFindMessagesBeforeResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = FindStoredMessagesBeforePort.Response(messages)

    fun createFindUserProfilesByIdsResponse(
        profiles: List<VkUserProfile> = emptyList(),
    ) = FindStoredUserProfilesByIdsPort.Response(profiles)

    fun createFindGroupProfilesByIdsResponse(
        profiles: List<VkGroupProfile> = emptyList(),
    ) = FindStoredGroupProfilesByIdsPort.Response(profiles)
}
