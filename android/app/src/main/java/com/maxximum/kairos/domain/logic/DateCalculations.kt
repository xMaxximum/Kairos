package com.maxximum.kairos.domain.logic

import java.util.Calendar

fun nextDailyOccurrence(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= now) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}

fun nextWeeklyOccurrence(
    dayOfWeek: Int,
    hour: Int,
    minute: Int,
    now: Long = System.currentTimeMillis()
): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
    var dayDelta = dayOfWeek - currentDay
    if (dayDelta < 0) dayDelta += 7
    cal.add(Calendar.DAY_OF_YEAR, dayDelta)

    if (cal.timeInMillis <= now) {
        cal.add(Calendar.DAY_OF_YEAR, 7)
    }
    return cal.timeInMillis
}

fun weekDays(): List<Int> {
    return listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
}

fun shortWeekDayLabel(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        Calendar.SUNDAY -> "Sun"
        else -> "?"
    }
}
