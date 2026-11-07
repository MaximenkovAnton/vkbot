package com.simarel.vkbot.greetings.domain

import java.time.LocalDate
import java.time.Month

object Holidays {
    private const val NEW_YEAR_DAY = 1
    private const val PROGRAMMER_DAY_OF_YEAR = 256

    fun forDate(date: LocalDate): List<Holiday> = buildList {
        if (date.monthValue == Month.JANUARY.value && date.dayOfMonth == NEW_YEAR_DAY) {
            add(Holiday.NEW_YEAR)
        }
        if (date.dayOfYear == PROGRAMMER_DAY_OF_YEAR) {
            add(Holiday.PROGRAMMER_DAY)
        }
    }
}
