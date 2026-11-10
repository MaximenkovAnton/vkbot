package com.simarel.vkbot.ai.domain.profile

private const val SPACE = " "
private const val YO = "ё"
private const val E = "е"
private val STRUCTURED_MENTION_REGEX = Regex("\\[(?:id|club|public)\\d+\\|[^\\]]*\\]", RegexOption.IGNORE_CASE)
private val ID_TOKEN_REGEX = Regex(
    "(?<![\\p{L}\\p{N}])(?:id|club|public)\\d+(?![\\p{L}\\p{N}])",
    RegexOption.IGNORE_CASE,
)
private val SEPARATOR_REGEX = Regex("[\\s.,!?;:«»\"'()\\[\\]]+")
private val STOP_WORDS = setOf(
    "что",
    "ты",
    "знаешь",
    "знаете",
    "известно",
    "расскажи",
    "расскажите",
    "кто",
    "такой",
    "такая",
    "такое",
    "это",
    "покажи",
    "покажите",
    "информация",
    "данные",
    "профиль",
    "о",
    "об",
    "обо",
    "про",
    "пожалуйста",
    "мне",
    "меня",
    "этого",
    "этот",
    "эту",
    "человека",
    "человек",
    "пользователя",
    "пользователь",
    "участника",
    "участник",
)

class ProfileNameNormalizer(botMention: String) {

    private val botMentionRegex = Regex(Regex.escape(botMention.trim()), RegexOption.IGNORE_CASE)

    fun normalize(text: String): String = text.lowercase().replace(YO, E)

    fun phrase(text: String): String? {
        val withoutMentions = ID_TOKEN_REGEX.replace(STRUCTURED_MENTION_REGEX.replace(text, SPACE), SPACE)
        val withoutBotMention = botMentionRegex.replace(withoutMentions, SPACE)
        val words = withoutBotMention
            .split(SEPARATOR_REGEX)
            .filter { word -> word.isNotEmpty() && normalize(word) !in STOP_WORDS }
        return words.joinToString(SPACE).takeIf { it.isNotEmpty() }
    }
}
