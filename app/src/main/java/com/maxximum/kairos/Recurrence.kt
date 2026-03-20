package com.maxximum.kairos

enum class RecurrenceType(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly")
}

fun Todo.recurrenceType(): RecurrenceType {
    return RecurrenceType.entries.firstOrNull { it.name == recurrence } ?: RecurrenceType.NONE
}

fun Todo.applyCompletionChange(markCompleted: Boolean, now: Long = System.currentTimeMillis()): Todo {
    if (!markCompleted) {
        return copy(isCompleted = false)
    }

    return when (recurrenceType()) {
        RecurrenceType.NONE -> copy(isCompleted = true)
        RecurrenceType.DAILY -> {
            val baseTime = reminderTime ?: now
            var nextTime = baseTime
            while (nextTime <= now) {
                nextTime += 24L * 60L * 60L * 1000L
            }
            copy(
                isCompleted = false,
                reminderTime = nextTime,
                timestamp = now
            )
        }
        RecurrenceType.WEEKLY -> {
            val baseTime = reminderTime ?: now
            var nextTime = baseTime
            while (nextTime <= now) {
                nextTime += 7L * 24L * 60L * 60L * 1000L
            }
            copy(
                isCompleted = false,
                reminderTime = nextTime,
                timestamp = now
            )
        }
    }
}

fun RecurrenceType.shortLabel(): String {
    return when (this) {
        RecurrenceType.NONE -> "Off"
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.WEEKLY -> "Weekly"
    }
}
