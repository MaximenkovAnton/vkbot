package com.simarel.vkbot.persistence.domain.knowledge

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object KnowledgeValueHash {

    private val WHITESPACE = Regex("\\s+")

    fun of(value: String): String = sha256(value.trim().replace(WHITESPACE, " ").lowercase())

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
