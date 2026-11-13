package com.steady90.app

import java.time.ZonedDateTime

object ReminderTime {
    /** One wall-clock reminder per day; recalculate across timezone and DST changes. */
    fun next(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        require(hour in 0..23 && minute in 0..59)
        val localTime = java.time.LocalTime.of(hour, minute)
        val candidate = now.toLocalDate().atTime(localTime).atZone(now.zone)
        return if (candidate.isAfter(now)) candidate else now.toLocalDate().plusDays(1).atTime(localTime).atZone(now.zone)
    }
}
