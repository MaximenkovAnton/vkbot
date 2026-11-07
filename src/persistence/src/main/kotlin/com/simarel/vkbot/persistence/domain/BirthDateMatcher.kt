package com.simarel.vkbot.persistence.domain

import java.time.MonthDay

object BirthDateMatcher {

    private val PATTERN = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.\d{4})?$""")

    fun parse(birthDate: String?): MonthDay? {
        val match = birthDate?.trim()?.let(PATTERN::matchEntire) ?: return null
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        return runCatching { MonthDay.of(month, day) }.getOrNull()
    }
}
