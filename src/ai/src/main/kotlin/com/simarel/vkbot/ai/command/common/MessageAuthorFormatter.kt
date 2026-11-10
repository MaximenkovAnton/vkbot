package com.simarel.vkbot.ai.command.common

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageAuthorFormatter(
    private val findUserProfilesByIdsPort: FindStoredUserProfilesByIdsPort,
    private val findGroupProfilesByIdsPort: FindStoredGroupProfilesByIdsPort,
) {

    fun format(messages: List<StoredMessage>): List<String> {
        val fromIds = messages.map { it.fromId }.distinct()
        val (groupFromIds, userIds) = fromIds.partition { it < 0 }
        val userProfiles = findUserProfilesByIdsPort.execute(
            FindStoredUserProfilesByIdsPort.Request(userIds.map { FromId.of(it) }),
        ).profiles.associateBy { it.id.value }
        val groupProfiles = findGroupProfilesByIdsPort.execute(
            FindStoredGroupProfilesByIdsPort.Request(groupFromIds.map { FromId.of(it) }),
        ).profiles.associateBy { it.id.value }
        return messages.map { message ->
            "[${authorName(message, userProfiles, groupProfiles)}]: ${message.messageText ?: ""}"
        }
    }

    private fun authorName(
        message: StoredMessage,
        userProfiles: Map<Long, VkUserProfile>,
        groupProfiles: Map<Long, VkGroupProfile>,
    ): String = when {
        message.fromId < 0 -> groupProfiles[-message.fromId]?.name ?: "Группа ${message.fromId}"
        else -> userProfiles[message.fromId]?.let { "${it.firstName} ${it.lastName}" }
            ?: "Пользователь ${message.fromId}"
    }
}
