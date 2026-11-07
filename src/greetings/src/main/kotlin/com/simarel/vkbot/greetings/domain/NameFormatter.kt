package com.simarel.vkbot.greetings.domain

import com.simarel.vkbot.share.domain.model.VkUserProfile

object NameFormatter {
    fun format(profile: VkUserProfile): String {
        val fullName = listOfNotNull(profile.firstName, profile.lastName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (fullName.isNotEmpty()) {
            return fullName
        }
        val screenName = profile.screenName?.trim().orEmpty()
        if (screenName.isNotEmpty()) {
            return screenName
        }
        return profile.id.value.toString()
    }
}
