package com.simarel.vkbot.greetings.domain

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.Year

object BirthdayDates {
    private const val FIRST_DAY = 1
    private const val LAST_DAY_OF_FEBRUARY = 29

    fun monthDaysFor(date: LocalDate): List<MonthDay> = buildList {
        add(MonthDay.of(date.monthValue, date.dayOfMonth))
        if (date.monthValue == Month.MARCH.value && date.dayOfMonth == FIRST_DAY && !Year.isLeap(date.year.toLong())) {
            add(MonthDay.of(Month.FEBRUARY.value, LAST_DAY_OF_FEBRUARY))
        }
    }
}
