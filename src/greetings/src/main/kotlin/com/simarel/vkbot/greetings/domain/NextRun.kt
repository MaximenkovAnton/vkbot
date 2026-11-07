package com.simarel.vkbot.greetings.domain

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

data class NextRun(val target: ZonedDateTime) {
    fun delay(now: ZonedDateTime): Duration = Duration.between(now, target)

    companion object {
        fun after(now: ZonedDateTime, zone: ZoneId): NextRun {
            val startOfToday = now.withZoneSameInstant(zone).toLocalDate().atStartOfDay(zone)
            val target = if (now.isBefore(startOfToday)) startOfToday else startOfToday.plusDays(1)
            return NextRun(target)
        }
    }
}
