package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile

sealed interface ProfileDossier {
    data class UserDossier(
        val profile: VkUserProfile,
        val statistics: UserStatistics,
        val history: List<ProfileHistoryEntry>,
        val chatMessageCount: Long?,
    ) : ProfileDossier

    data class GroupDossier(
        val profile: VkGroupProfile,
        val chatMessageCount: Long?,
    ) : ProfileDossier
}
