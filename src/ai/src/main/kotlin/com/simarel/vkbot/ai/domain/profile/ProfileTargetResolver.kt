package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId

private const val SPACE = " "
private const val ID_MENTION_KIND = "id"
private const val EMPTY_TARGET_ID = 0L
private const val LABEL_GROUP_INDEX = 3
private const val ABOUT_ME_PHRASE = "обо мне"
private const val ABOUT_ME_ALTERNATIVE = "про меня"
private const val STRUCTURED_MENTION_PATTERN = "\\[(id|club|public)(\\d+)\\|([^\\]]*)\\]"
private const val ID_TOKEN_PATTERN = "(?<![\\p{L}\\p{N}])(id|club|public)(\\d+)(?![\\p{L}\\p{N}])"

class ProfileTargetResolver(
    private val botId: Long,
    botMention: String,
    groupId: Long,
    private val nameNormalizer: ProfileNameNormalizer,
    private val nameMatcher: ProfileNameMatcher,
) {

    private val botMention = botMention.trim()
    private val botGroupId = -groupId
    private val structuredMentionRegex = Regex(STRUCTURED_MENTION_PATTERN, RegexOption.IGNORE_CASE)
    private val idTokenRegex = Regex(ID_TOKEN_PATTERN, RegexOption.IGNORE_CASE)

    sealed interface Resolution {
        data class Resolved(val target: ProfileTarget, val profile: VkUserProfile? = null) : Resolution

        data class Ambiguous(val targets: List<ProfileTarget>) : Resolution

        data class NotFound(val phrase: String) : Resolution

        data object NoTarget : Resolution
    }

    fun resolveExplicit(message: Message): Resolution {
        val targets = mentionTargets(message.messageText.value)
        if (targets.isNotEmpty()) {
            val single = targets.singleOrNull()
            return if (single == null) Resolution.Ambiguous(targets) else Resolution.Resolved(single)
        }
        forwardedTarget(message)?.let { return Resolution.Resolved(it) }
        selfTarget(message)?.let { return Resolution.Resolved(it) }
        return Resolution.NoTarget
    }

    fun resolveByName(message: Message, participants: List<VkUserProfile>): Resolution {
        val phrase = nameNormalizer.phrase(message.messageText.value) ?: return Resolution.NoTarget
        val matched = nameMatcher.matches(phrase, participants.sortedBy { it.id.value })
        return when (matched.size) {
            0 -> Resolution.NotFound(phrase)
            1 -> Resolution.Resolved(ProfileTarget.User(matched.single().id), matched.single())
            else -> Resolution.Ambiguous(matched.map { ProfileTarget.User(it.id) })
        }
    }

    private fun mentionTargets(text: String): List<ProfileTarget> {
        val targets = mutableListOf<ProfileTarget>()
        structuredMentionRegex.findAll(text).forEach { match ->
            val target = targetOf(match.groupValues[1], match.groupValues[2]) ?: return@forEach
            if (!isBotTarget(target, match.groupValues[LABEL_GROUP_INDEX])) {
                targets += target
            }
        }
        val withoutStructuredMentions = structuredMentionRegex.replace(text, SPACE)
        idTokenRegex.findAll(withoutStructuredMentions).forEach { match ->
            val target = targetOf(match.groupValues[1], match.groupValues[2]) ?: return@forEach
            if (!isBotTarget(target, null)) {
                targets += target
            }
        }
        return targets.distinct()
    }

    private fun targetOf(kind: String, digits: String): ProfileTarget? {
        val value = digits.toLongOrNull() ?: return null
        if (value == EMPTY_TARGET_ID) {
            return null
        }
        return if (kind.equals(ID_MENTION_KIND, ignoreCase = true)) {
            ProfileTarget.User(FromId(value))
        } else {
            ProfileTarget.Group(FromId(-value))
        }
    }

    private fun isBotTarget(target: ProfileTarget, label: String?): Boolean {
        if (label != null && label.trim().equals(botMention, ignoreCase = true)) {
            return true
        }
        return target.fromId.value == botId || target.fromId.value == botGroupId
    }

    private fun forwardedTarget(message: Message): ProfileTarget? = message.forwardedMessages
        .asReversed()
        .asSequence()
        .map { it.fromId.value }
        .firstOrNull { it != botId && it != EMPTY_TARGET_ID }
        ?.let { value -> if (value > 0) ProfileTarget.User(FromId(value)) else ProfileTarget.Group(FromId(value)) }

    private fun selfTarget(message: Message): ProfileTarget? {
        val text = nameNormalizer.normalize(message.messageText.value)
        if (!text.contains(ABOUT_ME_PHRASE) && !text.contains(ABOUT_ME_ALTERNATIVE)) {
            return null
        }
        val fromId = message.fromId.value
        return if (fromId > 0) ProfileTarget.User(FromId(fromId)) else null
    }
}
