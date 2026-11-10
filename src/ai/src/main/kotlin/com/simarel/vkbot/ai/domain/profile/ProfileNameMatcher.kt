package com.simarel.vkbot.ai.domain.profile

import com.simarel.vkbot.share.domain.model.VkUserProfile

private const val YO = "ё"
private const val E = "е"
private const val MIN_STEM_LENGTH = 2
private val SEPARATOR_REGEX = Regex("[^\\p{L}\\p{N}]+")
private val TRAILING_VOWELS = setOf('а', 'е', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я', 'й', 'ь', 'ъ')

class ProfileNameMatcher {

    fun matches(phrase: String, profiles: List<VkUserProfile>): List<VkUserProfile> {
        val phraseTokens = tokens(phrase)
        if (phraseTokens.isEmpty()) {
            return emptyList()
        }
        return profiles.filter { profile -> matches(phraseTokens, nameTokens(profile)) }
    }

    private fun nameTokens(profile: VkUserProfile): List<String> {
        val names = listOfNotNull(profile.firstName, profile.lastName, profile.screenName)
        return names.flatMap(::tokens).distinct()
    }

    private fun matches(phraseTokens: List<String>, nameTokens: List<String>): Boolean = nameTokens.isNotEmpty() &&
        phraseTokens.all { phraseToken ->
            nameTokens.any { nameToken -> tokenMatches(phraseToken, nameToken) }
        }

    private fun tokenMatches(phraseToken: String, nameToken: String): Boolean {
        val phraseStem = stem(phraseToken)
        if (phraseStem.length < MIN_STEM_LENGTH) {
            return phraseToken == nameToken
        }
        return stem(nameToken).startsWith(phraseStem)
    }

    private fun tokens(text: String): List<String> = text.lowercase()
        .replace(YO, E)
        .split(SEPARATOR_REGEX)
        .filter { it.isNotEmpty() }

    private fun stem(token: String): String = token.trimEnd { it in TRAILING_VOWELS }
}
